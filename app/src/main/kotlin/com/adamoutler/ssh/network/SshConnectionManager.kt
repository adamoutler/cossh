package com.adamoutler.ssh.network

import com.adamoutler.ssh.crypto.IdentityStorageManager
import com.adamoutler.ssh.data.ConnectionProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.transport.verification.OpenSSHKnownHosts
import java.io.File
import java.security.KeyPair
import java.security.PublicKey

class TofuHostKeyVerifier(private val knownHostsFile: File) : OpenSSHKnownHosts(knownHostsFile) {
    override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
        if (super.verify(hostname, port, key)) {
            return true
        }

        val formattedHost = if (port != 22) "[$hostname]:$port" else hostname

        var oldFingerprint: String? = null
        val hostExists = knownHostsFile.exists() && knownHostsFile.useLines { lines ->
            var found = false
            for (line in lines) {
                val tokens = line.split(" ")
                val firstToken = tokens.firstOrNull()
                if (firstToken != null && firstToken == formattedHost) {
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
                hostname = formattedHost,
                expectedFingerprint = oldFingerprint,
                receivedFingerprint = receivedFingerprint,
                isKeyChanged = hostExists,
            )
        }

        if (userAccepted) {
            val newEntry = "$formattedHost $keyType $keyBlobBase64\n"
            if (hostExists && knownHostsFile.exists()) {
                // Atomic overwrite: remove old entry, write new
                val tempFile = File(knownHostsFile.absolutePath + ".tmp")
                knownHostsFile.useLines { lines ->
                    tempFile.printWriter().use { out ->
                        lines.forEach { line ->
                            val firstToken = line.split(" ").firstOrNull()
                            if (firstToken != null && firstToken != formattedHost) {
                                out.println(line)
                            }
                        }
                        out.print(newEntry)
                    }
                }
                val success = tempFile.renameTo(knownHostsFile)
                if (!success) {
                    println(("TofuHostKeyVerifier").toString() + ": " + ("Failed to rename temp known_hosts file").toString())
                }
            } else {
                knownHostsFile.appendText(newEntry)
            }
            try {
                println(("TofuHostKeyVerifier").toString() + ": " + ("Host $hostname key accepted and saved.").toString())
            } catch (e: Throwable) {
                println("Host $hostname key accepted and saved.")
            }
            return true
        } else {
            try {
                println(("TofuHostKeyVerifier").toString() + ": " + ("Host key for $hostname rejected by user. Connection aborted.").toString())
            } catch (e: Throwable) {
                println("Host key rejected by user.")
            }
            return false
        }
    }
}

class SshConnectionManager(
    private val hostKeyVerifier: HostKeyVerifier? = null,
    private val identityStorageManager: IdentityStorageManager? = null,
    private val context: android.content.Context? = null,
) {
    private val portForwardingOrchestrator = PortForwardingOrchestrator()
    private val protocolFactory = ConnectionProtocolFactory(hostKeyVerifier, identityStorageManager, context, portForwardingOrchestrator)

    private var activeProtocol: ConnectionProtocol? = null

    // Exposed for tests or legacy code that might need the underlying client.
    val client: SSHClient?
        get() = (activeProtocol as? SshConnectionHandler)?.client

    fun disconnect() {
        try {
            activeProtocol?.disconnect()
        } catch (e: Exception) {
            println(("SshConnectionManager").toString() + ": " + ("Error during manual disconnect").toString() + " " + (e).toString())
        }
        try {
            portForwardingOrchestrator.stopAll()
        } catch (e: Exception) {
            println(("SshConnectionManager").toString() + ": " + ("Error closing local port forwarders").toString() + " " + (e).toString())
        }
    }

    suspend fun connectAndExecute(profile: ConnectionProfile, command: String, keyPair: KeyPair? = null): String = withContext(Dispatchers.IO) {
        val sshClient = SSHClient(net.schmizz.sshj.AndroidConfig())
        val handshakeCoordinator = SshHandshakeCoordinator(hostKeyVerifier, identityStorageManager, context)

        try {
            handshakeCoordinator.executeWithConnection(sshClient, profile, keyPair) { _ ->
                sshClient.startSession().use { session ->
                    val cmd = session.exec(command)
                    val result = cmd.inputStream.bufferedReader().use { it.readText() }
                    cmd.join()
                    result
                }
            }
        } finally {
            try {
                sshClient.disconnect()
            } catch (e: Exception) {
                println(("SshConnectionManager").toString() + ": " + ("Error during disconnect").toString() + " " + (e).toString())
            }
        }
    }

    suspend fun connectPty(
        profile: ConnectionProfile,
        keyPair: KeyPair? = null,
        onOutput: suspend (ByteArray, Int) -> Unit,
        onConnect: (java.io.OutputStream, net.schmizz.sshj.connection.channel.direct.Session.Shell?) -> Unit,
    ) = withContext(Dispatchers.IO) {
        activeProtocol = protocolFactory.create(profile.protocol)
        activeProtocol?.connect(profile, keyPair, onOutput, onConnect)
    }

    /**
     * Injects a public key into the remote server's authorized_keys file.
     * Uses a temporary password for authentication.
     */
    suspend fun injectPublicKey(
        profile: ConnectionProfile,
        publicKey: String,
    ): Boolean = withContext(Dispatchers.IO) {
        // Validate public key to prevent shell injection
        val regex = Regex("^[a-zA-Z0-9+/= \\-_@]+$")
        if (!regex.matches(publicKey)) {
            println(("SshConnectionManager").toString() + ": " + ("Invalid public key format for injection").toString())
            return@withContext false
        }

        val injectionCommand = "mkdir -p ~/.ssh && chmod 700 ~/.ssh && echo \"$publicKey\" >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"

        try {
            connectAndExecute(profile, injectionCommand)
            true
        } catch (e: Exception) {
            println(("SshConnectionManager").toString() + ": " + ("Failed to inject public key").toString() + " " + (e).toString())
            false
        }
    }
}
