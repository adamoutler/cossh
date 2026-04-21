package com.adamoutler.ssh.network

import com.adamoutler.ssh.crypto.IdentityStorageManager
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.IdentityProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.security.KeyPair
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec

class SshConnectionManager(
    private val hostKeyVerifier: HostKeyVerifier? = null,
    private val identityStorageManager: IdentityStorageManager? = null
) {
    private fun getAuthenticator(
        profile: ConnectionProfile,
        keyPair: KeyPair?,
        identity: IdentityProfile? = null
    ): SshAuthenticator {
        // Resolve credentials priority: 1. Identity, 2. Explicit KeyPair, 3. Inline Profile
        if (identity != null) {
            return when (identity.authType) {
                AuthType.PASSWORD -> {
                    PasswordAuthenticator()
                }
                AuthType.KEY -> {
                    val resolvedKeyPair = if (identity.privateKey != null) {
                        loadKeyPairFromIdentity(identity)
                    } else {
                        keyPair
                    } ?: throw IllegalArgumentException("No KeyPair available for identity ${identity.name}")
                    KeyAuthenticator(resolvedKeyPair)
                }
            }
        }

        return when (profile.authType) {
            AuthType.PASSWORD -> PasswordAuthenticator()
            AuthType.KEY -> {
                if (keyPair == null) throw IllegalArgumentException("KeyPair required for key-based authentication")
                KeyAuthenticator(keyPair)
            }
        }
    }

    private fun loadKeyPairFromIdentity(identity: IdentityProfile): KeyPair {
        val privateKeyBytes = identity.privateKey ?: throw IllegalArgumentException("Identity has no private key")
        // Identity currently doesn't store the algorithm explicitly for private keys, 
        // but we can infer it or try both. For now we assume the identity creation 
        // matches the supported algorithms in SSHKeyGenerator.
        
        // This is a simplified loader. In a real app, we'd store the algorithm name.
        return try {
            val keyFactory = KeyFactory.getInstance("Ed25519")
            val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))
            // We'd also need the public key to reconstruct the KeyPair, 
            // or we use a different SshAuthenticator that only needs the private key.
            // Sshj's KeyPair wrapper often needs both.
            KeyPair(null, privateKey) // Placeholder
        } catch (e: Exception) {
            val keyFactory = KeyFactory.getInstance("RSA")
            val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))
            KeyPair(null, privateKey) // Placeholder
        }
    }

    private fun resolveIdentity(profile: ConnectionProfile): IdentityProfile? {
        val id = profile.identityId ?: return null
        return identityStorageManager?.getIdentity(id)
    }

    suspend fun connectAndExecute(profile: ConnectionProfile, command: String, keyPair: KeyPair? = null): String = withContext(Dispatchers.IO) {
        val client = SSHClient(net.schmizz.sshj.AndroidConfig())
        // Aggressive timeouts per security invariant
        client.connectTimeout = 10000
        client.timeout = 10000
        
        val identity = resolveIdentity(profile)
        
        try {
            if (hostKeyVerifier != null) {
                client.addHostKeyVerifier(hostKeyVerifier)
            } else {
                try {
                    client.loadKnownHosts()
                } catch (e: java.io.IOException) {
                    android.util.Log.w("SshConnectionManager", "Could not load known_hosts, falling back to PromiscuousVerifier", e)
                    client.addHostKeyVerifier(net.schmizz.sshj.transport.verification.PromiscuousVerifier())
                }
            }
            
            client.connect(profile.host, profile.port)
            
            val effectiveProfile = if (identity != null) {
                profile.copy(username = identity.username, password = identity.password)
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
        onConnect: (java.io.OutputStream, net.schmizz.sshj.connection.channel.direct.Session.Shell) -> Unit
    ) = withContext(Dispatchers.IO) {
        val client = SSHClient(net.schmizz.sshj.AndroidConfig())
        client.connectTimeout = 10000
        client.timeout = 10000
        
        val identity = resolveIdentity(profile)

        try {
            if (hostKeyVerifier != null) {
                client.addHostKeyVerifier(hostKeyVerifier)
            } else {
                try {
                    client.loadKnownHosts()
                } catch (e: java.io.IOException) {
                    android.util.Log.w("SshConnectionManager", "Could not load known_hosts, falling back to PromiscuousVerifier", e)
                    client.addHostKeyVerifier(net.schmizz.sshj.transport.verification.PromiscuousVerifier())
                }
            }
            client.connect(profile.host, profile.port)

            val effectiveProfile = if (identity != null) {
                profile.copy(username = identity.username, password = identity.password)
            } else profile

            getAuthenticator(profile, keyPair, identity).authenticate(client, effectiveProfile)

            client.startSession().use { session ->
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
