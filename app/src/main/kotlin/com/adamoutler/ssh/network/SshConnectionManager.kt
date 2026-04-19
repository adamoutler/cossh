package com.adamoutler.ssh.network

import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.security.KeyPair

class SshConnectionManager(
    private val hostKeyVerifier: HostKeyVerifier? = null
) {
    private fun getAuthenticator(profile: ConnectionProfile, keyPair: KeyPair?): SshAuthenticator {
        return when (profile.authType) {
            AuthType.PASSWORD -> PasswordAuthenticator()
            AuthType.KEY -> {
                if (keyPair == null) throw IllegalArgumentException("KeyPair required for key-based authentication")
                KeyAuthenticator(keyPair)
            }
        }
    }

    suspend fun connectAndExecute(profile: ConnectionProfile, command: String, keyPair: KeyPair? = null): String = withContext(Dispatchers.IO) {
        val client = SSHClient(net.schmizz.sshj.AndroidConfig())
        // Aggressive timeouts per security invariant
        client.connectTimeout = 10000
        client.timeout = 10000
        
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
            getAuthenticator(profile, keyPair).authenticate(client, profile)

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
            getAuthenticator(profile, keyPair).authenticate(client, profile)

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
        }
    }
}
