package com.adamoutler.ssh.network

import android.content.Context
import com.adamoutler.ssh.crypto.IdentityStorageManager
import com.adamoutler.ssh.data.ConnectionProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.io.OutputStream
import java.security.KeyPair

class SshConnectionHandler(
    private val hostKeyVerifier: HostKeyVerifier? = null,
    private val identityStorageManager: IdentityStorageManager? = null,
    private val context: Context? = null,
    private val portForwardingOrchestrator: PortForwardingOrchestrator
) : ConnectionProtocol {

    var client: SSHClient? = null
        private set

    override suspend fun connect(
        profile: ConnectionProfile,
        keyPair: KeyPair?,
        onOutput: suspend (ByteArray, Int) -> Unit,
        onConnect: (OutputStream, net.schmizz.sshj.connection.channel.direct.Session.Shell?) -> Unit
    ) = withContext(Dispatchers.IO) {
        val sshClient = SSHClient(net.schmizz.sshj.AndroidConfig())
        client = sshClient
        val handshakeCoordinator = SshHandshakeCoordinator(hostKeyVerifier, identityStorageManager, context)

        try {
            handshakeCoordinator.executeWithConnection(sshClient, profile, keyPair) { effectiveProfile ->
                portForwardingOrchestrator.startPortForwards(sshClient, effectiveProfile)

                sshClient.startSession().use { session ->
                    effectiveProfile.envVars.forEach { (key, value) ->
                        try {
                            session.setEnvVar(key, value)
                        } catch (e: Exception) {
                            android.util.Log.w("SshConnectionHandler", "Failed to set env var $key", e)
                        }
                    }
                    session.allocatePTY("xterm-256color", 80, 24, 0, 0, emptyMap())
                    val shell = session.startShell()

                    if (!effectiveProfile.initialDirectory.isNullOrEmpty()) {
                        val escapedDir = effectiveProfile.initialDirectory.replace("'", "'\\''")
                        val cdCmd = "cd '$escapedDir'\r\n"
                        shell.outputStream.write(cdCmd.toByteArray(Charsets.UTF_8))
                        shell.outputStream.flush()
                    }

                    onConnect(shell.outputStream, shell)

                    val bridge = PtyStreamBridge(shell.inputStream, onOutput)
                    bridge.startBridge()
                }
            }
        } finally {
            disconnect()
        }
    }

    override fun disconnect() {
        try {
            client?.disconnect()
        } catch (e: Exception) {
            android.util.Log.e("SshConnectionHandler", "Error during disconnect", e)
        }
    }
}
