package com.adamoutler.ssh.network

import com.adamoutler.ssh.crypto.IdentityStorageManager
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.IdentityProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.transport.verification.OpenSSHKnownHosts
import java.security.KeyPair
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.security.PublicKey
import java.io.File

class TofuHostKeyVerifier(private val knownHostsFile: File) : OpenSSHKnownHosts(knownHostsFile) {
    override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
        if (super.verify(hostname, port, key)) {
            return true
        }

        var oldFingerprint: String? = null
        val hostExists = knownHostsFile.exists() && knownHostsFile.useLines { lines ->
            var found = false
            for (line in lines) {
                val tokens = line.split(" ")
                val firstToken = tokens.firstOrNull()
                if (firstToken != null && (firstToken.contains(hostname) || firstToken.contains("[$hostname]:$port"))) {
                    found = true
                    if (tokens.size >= 3) {
                        try {
                            val decodedOld = java.util.Base64.getDecoder().decode(tokens[2])
                            val md = java.security.MessageDigest.getInstance("SHA-256")
                            oldFingerprint = "SHA256:" + java.util.Base64.getEncoder().encodeToString(md.digest(decodedOld))
                        } catch (e: Exception) {
                            oldFingerprint = "Unknown/Unparseable"
                        }
                    }
                    break
                }
            }
            found
        }

        val keyType = net.schmizz.sshj.common.KeyType.fromKey(key).toString()
        val buffer = net.schmizz.sshj.common.Buffer.PlainBuffer().putPublicKey(key)
        val keyBlobBase64 = java.util.Base64.getEncoder().encodeToString(buffer.compactData)
        val receivedFingerprint = try {
            net.schmizz.sshj.common.SecurityUtils.getFingerprint(key)
        } catch (e: Exception) {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            "SHA256:" + java.util.Base64.getEncoder().encodeToString(md.digest(buffer.compactData))
        }

        // Prompt user (runBlocking because verify is synchronous but we need to await the user's UI action)
        val userAccepted = kotlinx.coroutines.runBlocking {
            ConnectionStateRepository.requestPrompt(
                hostname = hostname,
                expectedFingerprint = oldFingerprint,
                receivedFingerprint = receivedFingerprint,
                isKeyChanged = hostExists
            )
        }

        if (userAccepted) {
            val newEntry = "$hostname $keyType $keyBlobBase64\n"
            if (hostExists && knownHostsFile.exists()) {
                // Atomic overwrite: remove old entry, write new
                val tempFile = File(knownHostsFile.absolutePath + ".tmp")
                knownHostsFile.useLines { lines ->
                    tempFile.printWriter().use { out ->
                        lines.forEach { line ->
                            val firstToken = line.split(" ").firstOrNull()
                            if (firstToken != null && !firstToken.contains(hostname) && !firstToken.contains("[$hostname]:$port")) {
                                out.println(line)
                            }
                        }
                        out.print(newEntry)
                    }
                }
                tempFile.renameTo(knownHostsFile)
            } else {
                knownHostsFile.appendText(newEntry)
            }
            try { android.util.Log.i("TofuHostKeyVerifier", "Host $hostname key accepted and saved.") } catch (e: Throwable) { println("Host $hostname key accepted and saved.") }
            return true
        } else {
            try { android.util.Log.e("TofuHostKeyVerifier", "Host key for $hostname rejected by user. Connection aborted.") } catch (e: Throwable) { println("Host key rejected by user.") }
            return false
        }
    }
}

class SshConnectionManager(
    private val hostKeyVerifier: HostKeyVerifier? = null,
    private val identityStorageManager: IdentityStorageManager? = null,
    private val context: android.content.Context? = null
) {
    private var client: SSHClient? = null
    private var telnetClient: org.apache.commons.net.telnet.TelnetClient? = null
    private val localServerSockets = mutableListOf<java.net.ServerSocket>()

    private fun configureHostKeyVerifier() {
        if (hostKeyVerifier != null) {
            client?.addHostKeyVerifier(hostKeyVerifier)
        } else {
            val knownHostsFile = context?.let { File(it.filesDir, "ssh_known_hosts") }
            if (knownHostsFile != null) {
                if (!knownHostsFile.exists()) knownHostsFile.createNewFile()
                client?.addHostKeyVerifier(TofuHostKeyVerifier(knownHostsFile))
            } else {
                throw java.io.IOException("No Context provided for TOFU verifier and no HostKeyVerifier configured. Refusing to connect insecurely.")
            }
        }
    }

    private class CompositeAuthenticator(private val authenticators: List<SshAuthenticator>) : SshAuthenticator {
        override fun authenticate(client: SSHClient, profile: ConnectionProfile) {
            var lastException: Exception? = null
            for (authenticator in authenticators) {
                try {
                    authenticator.authenticate(client, profile)
                    if (client.isAuthenticated) {
                        return
                    }
                } catch (e: Exception) {
                    lastException = e
                    android.util.Log.w("CompositeAuthenticator", "Authentication failed with ${authenticator::class.java.simpleName}, trying next", e)
                }
            }
            throw net.schmizz.sshj.userauth.UserAuthException("All authentication methods failed", lastException)
        }
    }

    private fun getAuthenticator(
        profile: ConnectionProfile,
        keyPair: KeyPair?,
        identity: IdentityProfile? = null
    ): SshAuthenticator {
        if (identity != null) {
            val authenticators = mutableListOf<SshAuthenticator>()
            
            // Try key auth if a private key exists
            if (identity.privateKey != null || keyPair != null) {
                try {
                    val resolvedKeyPair = if (identity.privateKey != null) {
                        loadKeyPairFromIdentity(identity)
                    } else {
                        keyPair
                    }
                    if (resolvedKeyPair?.public != null) {
                        authenticators.add(KeyAuthenticator(resolvedKeyPair))
                    } else {
                        android.util.Log.w("SshConnectionManager", "Public key is null, skipping KeyAuthenticator")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SshConnectionManager", "Failed to initialize keypair", e)
                }
            }

            // Try password auth if a password exists
            if (identity.password != null) {
                authenticators.add(PasswordAuthenticator())
            }

            if (authenticators.isNotEmpty()) {
                return CompositeAuthenticator(authenticators)
            }
            
            throw IllegalArgumentException("Identity has neither valid password nor complete private/public key")
        }

        return when (profile.authType) {
            AuthType.PASSWORD -> PasswordAuthenticator()
            AuthType.KEY -> {
                if (keyPair == null || keyPair.public == null) throw IllegalArgumentException("Valid KeyPair with public key required for key-based authentication")
                KeyAuthenticator(keyPair)
            }
        }
    }

    private fun loadKeyPairFromIdentity(identity: IdentityProfile): KeyPair {
        val privateKeyBytes = identity.privateKey ?: throw IllegalArgumentException("Identity has no private key")
        
        var publicKey: java.security.PublicKey? = null
        try {
            val pubKeyStr = identity.publicKey
            if (!pubKeyStr.isNullOrEmpty()) {
                val parts = pubKeyStr.split(" ")
                if (parts.size >= 2) {
                    val type = parts[0]
                    val base64 = parts[1]
                    val decoded = java.util.Base64.getDecoder().decode(base64)
                    val buffer = net.schmizz.sshj.common.Buffer.PlainBuffer(decoded)
                    buffer.readString() // Read algorithm name
                    publicKey = net.schmizz.sshj.common.KeyType.fromString(type).readPubKeyFromBuffer(buffer)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SshConnectionManager", "Failed to parse public key from identity", e)
        }

        return com.adamoutler.ssh.crypto.PemUtils.parsePemToKeyPair(privateKeyBytes, publicKey)
    }

    private fun resolveIdentity(profile: ConnectionProfile): IdentityProfile? {
        val id = profile.identityId ?: return null
        return identityStorageManager?.getIdentity(id)
    }

    fun disconnect() {
        try {
            client?.disconnect()
        } catch (e: Exception) {
            android.util.Log.e("SshConnectionManager", "Error during manual disconnect", e)
        }
        try {
            localServerSockets.forEach { it.close() }
            localServerSockets.clear()
        } catch (e: Exception) {
            android.util.Log.e("SshConnectionManager", "Error closing local port forwarders", e)
        }
    }

    private fun startPortForwards(client: SSHClient, profile: ConnectionProfile) {
        profile.portForwards.forEach { config ->
            try {
                if (config.type == com.adamoutler.ssh.data.PortForwardType.LOCAL) {
                    val params = net.schmizz.sshj.connection.channel.direct.Parameters("127.0.0.1", config.localPort, config.remoteHost, config.remotePort)
                    val serverSocket = java.net.ServerSocket()
                    serverSocket.reuseAddress = true
                    serverSocket.bind(java.net.InetSocketAddress("127.0.0.1", config.localPort))
                    localServerSockets.add(serverSocket)
                    val localPortForwarder = client.newLocalPortForwarder(params, serverSocket)
                    kotlin.concurrent.thread(name = "LocalPortForwarder_${config.localPort}") {
                        try {
                            localPortForwarder.listen()
                        } catch (e: Exception) {
                            android.util.Log.e("SshConnectionManager", "Local port forwarder error", e)
                        } finally {
                            try { serverSocket.close() } catch (e: Exception) {}
                        }
                    }
                } else if (config.type == com.adamoutler.ssh.data.PortForwardType.REMOTE) {
                    val remoteForward = net.schmizz.sshj.connection.channel.forwarded.RemotePortForwarder.Forward(config.remotePort)
                    val host = if (config.remoteHost.isEmpty()) "127.0.0.1" else config.remoteHost
                    val localTargetAddress = java.net.InetSocketAddress(host, config.localPort)
                    val localListener = net.schmizz.sshj.connection.channel.forwarded.SocketForwardingConnectListener(localTargetAddress)
                    client.remotePortForwarder.bind(remoteForward, localListener)
                }
            } catch (e: Exception) {
                android.util.Log.e("SshConnectionManager", "Failed to setup port forward $config", e)
            }
        }
    }

    suspend fun connectAndExecute(profile: ConnectionProfile, command: String, keyPair: KeyPair? = null): String = withContext(Dispatchers.IO) {
        val client = SSHClient(net.schmizz.sshj.AndroidConfig())
        this@SshConnectionManager.client = client
        // Aggressive timeouts per security invariant
        client.connectTimeout = 10000
        client.timeout = 10000
        
        val identity = resolveIdentity(profile)

        try {
            configureHostKeyVerifier()

            client.connect(profile.host, profile.port)

            val effectiveProfile = if (identity != null) {                profile.copy(username = identity.username, password = identity.password)
            } else profile

            getAuthenticator(profile, keyPair, identity).authenticate(client, effectiveProfile)

            client.startSession().use { session ->
                val cmd = session.exec(command)
                val result = cmd.inputStream.bufferedReader().use { it.readText() }
                cmd.join()
                return@withContext result
            }
        } finally {
            try {
                client.disconnect()
            } catch (e: Exception) {
                android.util.Log.e("SshConnectionManager", "Error during disconnect", e)
            }
            identity?.clearSensitiveData()
        }
    }

    suspend fun connectPty(
        profile: ConnectionProfile,
        keyPair: KeyPair? = null,
        onOutput: suspend (ByteArray, Int) -> Unit,
        onConnect: (java.io.OutputStream, net.schmizz.sshj.connection.channel.direct.Session.Shell?) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (profile.protocol == com.adamoutler.ssh.data.Protocol.TELNET) {
            connectTelnet(profile, onOutput, onConnect)
            return@withContext
        }

        val client = SSHClient(net.schmizz.sshj.AndroidConfig())
        this@SshConnectionManager.client = client
        client.connectTimeout = 10000
        client.timeout = 10000
        
        val identity = resolveIdentity(profile)

        try {
            if (hostKeyVerifier != null) {
                client.addHostKeyVerifier(hostKeyVerifier)
            } else {
                val knownHostsFile = context?.let { File(it.filesDir, "ssh_known_hosts") }
                if (knownHostsFile != null) {
                    if (!knownHostsFile.exists()) knownHostsFile.createNewFile()
                    client.addHostKeyVerifier(TofuHostKeyVerifier(knownHostsFile))
                } else {
                    throw java.io.IOException("No Context provided for TOFU verifier and no HostKeyVerifier configured. Refusing to connect insecurely.")
                }
            }
            client.connect(profile.host, profile.port)

            val effectiveProfile = if (identity != null) {
                profile.copy(username = identity.username, password = identity.password)
            } else profile

            getAuthenticator(profile, keyPair, identity).authenticate(client, effectiveProfile)

            startPortForwards(client, effectiveProfile)

            client.startSession().use { session ->
                effectiveProfile.envVars.forEach { (key, value) ->
                    try {
                        session.setEnvVar(key, value)
                    } catch (e: Exception) {
                        android.util.Log.w("SshConnectionManager", "Failed to set env var $key", e)
                    }
                }
                session.allocateDefaultPTY()
                val shell = session.startShell()
                
                onConnect(shell.outputStream, shell)

                val bridge = PtyStreamBridge(shell.inputStream, onOutput)
                bridge.startBridge()
            }
        } finally {
            try {
                client.disconnect()
            } catch (e: Exception) {
                android.util.Log.e("SshConnectionManager", "Error during disconnect", e)
            }
            identity?.clearSensitiveData()
        }
    }

    private suspend fun connectTelnet(
        profile: ConnectionProfile,
        onOutput: suspend (ByteArray, Int) -> Unit,
        onConnect: (java.io.OutputStream, net.schmizz.sshj.connection.channel.direct.Session.Shell?) -> Unit
    ): Unit = withContext(Dispatchers.IO) {
        val tc = org.apache.commons.net.telnet.TelnetClient()
        this@SshConnectionManager.telnetClient = tc
        tc.connectTimeout = 10000

        tc.setSocketFactory(object : javax.net.SocketFactory() {
            override fun createSocket(): java.net.Socket = java.nio.channels.SocketChannel.open().socket()
            
            override fun createSocket(host: String, port: Int): java.net.Socket {
                return java.nio.channels.SocketChannel.open().apply { 
                    connect(java.net.InetSocketAddress(host, port)) 
                }.socket()
            }
            
            override fun createSocket(host: java.net.InetAddress, port: Int): java.net.Socket {
                return java.nio.channels.SocketChannel.open().apply { 
                    connect(java.net.InetSocketAddress(host, port)) 
                }.socket()
            }
            
            override fun createSocket(host: String, port: Int, localHost: java.net.InetAddress, localPort: Int) = throw NotImplementedError()
            override fun createSocket(address: java.net.InetAddress, port: Int, localAddress: java.net.InetAddress, localPort: Int) = throw NotImplementedError()
        })
        
        try {
            tc.addOptionHandler(org.apache.commons.net.telnet.TerminalTypeOptionHandler("xterm-256color", false, false, true, false))
            tc.addOptionHandler(org.apache.commons.net.telnet.EchoOptionHandler(false, true, false, true))
            tc.addOptionHandler(org.apache.commons.net.telnet.SuppressGAOptionHandler(true, true, true, true))

            tc.connect(profile.host, profile.port)
            
            val writeChannel = kotlinx.coroutines.channels.Channel<ByteArray>(kotlinx.coroutines.channels.Channel.UNLIMITED)
            
            val ioJob = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                for (bytes in writeChannel) {
                    try {
                        tc.outputStream.write(bytes)
                        tc.outputStream.flush()
                    } catch(e: Exception) {
                        android.util.Log.e("SshConnectionManager", "Error writing to telnet stream", e)
                        break
                    }
                }
            }

            val autoFlushingStream = object : java.io.OutputStream() {
                var lastWasCr = false
                private fun translateAndSend(b: Int) {
                    val byteVal = b.toByte()
                    if (byteVal == '\r'.code.toByte()) {
                        writeChannel.trySend(byteArrayOf('\r'.code.toByte(), '\n'.code.toByte()))
                        lastWasCr = true
                    } else if (byteVal == '\n'.code.toByte()) {
                        if (!lastWasCr) {
                            writeChannel.trySend(byteArrayOf('\r'.code.toByte(), '\n'.code.toByte()))
                        }
                        lastWasCr = false
                    } else {
                        writeChannel.trySend(byteArrayOf(byteVal))
                        lastWasCr = false
                    }
                }
                override fun write(b: Int) {
                    translateAndSend(b)
                }
                override fun write(b: ByteArray, off: Int, len: Int) {
                    for (i in off until off + len) {
                        translateAndSend(b[i].toInt())
                    }
                }
                override fun flush() {} 
                override fun close() {
                    writeChannel.close()
                    ioJob.cancel()
                    try { tc.outputStream.close() } catch (e: Exception) {}
                }
            }
            
            onConnect(autoFlushingStream, null)
            
            val bridgeJob = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                val bridge = PtyStreamBridge(tc.inputStream, onOutput)
                bridge.startBridge()
            }
            
            try {
                kotlinx.coroutines.awaitCancellation()
            } finally {
                bridgeJob.cancel()
            }
        } finally {
            try {
                tc.disconnect()
            } catch (e: Exception) {
                android.util.Log.e("SshConnectionManager", "Error during telnet disconnect", e)
            }
        }
    }

    /**
     * Injects a public key into the remote server's authorized_keys file.
     * Uses a temporary password for authentication.
     */
    suspend fun injectPublicKey(
        profile: ConnectionProfile,
        publicKey: String
    ): Boolean = withContext(Dispatchers.IO) {
        // Validate public key to prevent shell injection
        val regex = Regex("^[a-zA-Z0-9+/= \\-_@]+$")
        if (!regex.matches(publicKey)) {
            android.util.Log.e("SshConnectionManager", "Invalid public key format for injection")
            return@withContext false
        }

        val injectionCommand = "mkdir -p ~/.ssh && chmod 700 ~/.ssh && echo \"$publicKey\" >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
        
        try {
            connectAndExecute(profile, injectionCommand)
            true
        } catch (e: Exception) {
            android.util.Log.e("SshConnectionManager", "Failed to inject public key", e)
            false
        }
    }
}
