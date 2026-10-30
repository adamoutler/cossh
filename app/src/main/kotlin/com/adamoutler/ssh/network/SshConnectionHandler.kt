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
    private val portForwardingOrchestrator: PortForwardingOrchestrator,
    private val securityStorageManager: com.adamoutler.ssh.crypto.SecurityStorageManager? = null,
    private val injectedHandshakeCoordinator: SshHandshakeCoordinator? = null,
) : ConnectionProtocol {

    var client: SSHClient? = null
        private set
    var jumpClient: SSHClient? = null
        private set
    var jumpTunnel: net.schmizz.sshj.connection.channel.direct.DirectConnection? = null
        private set

    override suspend fun connect(
        profile: ConnectionProfile,
        keyPair: KeyPair?,
        onOutput: suspend (ByteArray, Int) -> Unit,
        onConnect: (OutputStream, net.schmizz.sshj.connection.channel.direct.Session.Shell?) -> Unit,
        isAutonomousReconnect: Boolean,
        sessionId: String?,
    ) = withContext(Dispatchers.IO) {
        val sshClient = SSHClient(SecureSshConfig())
        client = sshClient
        val handshakeCoordinator = injectedHandshakeCoordinator ?: SshHandshakeCoordinator(hostKeyVerifier, identityStorageManager, context)

        try {
            if (!profile.jumpHostProfileId.isNullOrEmpty()) {
                val jumpProfile = securityStorageManager?.getProfile(profile.jumpHostProfileId)
                    ?: throw java.io.IOException("Jump host profile ${profile.jumpHostProfileId} not found")

                val jumpSshClient = SSHClient(SecureSshConfig())
                jumpClient = jumpSshClient

                // 1. Connect and authenticate to Jump Host with independent TOFU verification
                handshakeCoordinator.executeWithConnection(
                    jumpSshClient,
                    jumpProfile,
                    isAutonomousReconnect = isAutonomousReconnect,
                    sessionId = sessionId?.let { "${it}_jump" },
                ) { _ ->
                    // 2. Open direct-tcpip channel to target host:port via the Jump Host
                    val tunnel = jumpSshClient.newDirectConnection(profile.host, profile.port)
                    jumpTunnel = tunnel

                    // 3. Configure independent Host Key Verifier on target sshClient BEFORE connectVia initiates key exchange
                    handshakeCoordinator.configureHostKeyVerifier(sshClient, isAutonomousReconnect)

                    // 4. Connect target SSHClient through the established tunnel using native SSHJ connectVia
                    sshClient.connectVia(tunnel)
                }
            }

            // 5. Authenticate target SSHClient and start session
            handshakeCoordinator.executeWithConnection(
                sshClient,
                profile,
                keyPair,
                skipSocketConnect = !profile.jumpHostProfileId.isNullOrEmpty(),
                isAutonomousReconnect = isAutonomousReconnect,
                sessionId = sessionId,
            ) { effectiveProfile ->
                portForwardingOrchestrator.startPortForwards(sshClient, effectiveProfile)

                sshClient.startSession().use { session ->
                    effectiveProfile.envVars.forEach { (key, value) ->
                        try {
                            session.setEnvVar(key, value)
                        } catch (e: Exception) {
                            println(("SshConnectionHandler").toString() + ": " + ("Failed to set env var $key").toString() + " " + (e).toString())
                        }
                    }
                    sessionId?.let {
                        ConnectionLogRepository.log(
                            sessionId = it,
                            milestone = ConnectionMilestone.CHANNEL,
                            message = "Allocating PTY (xterm-256color, 80x24)...",
                        )
                    }
                    session.allocatePTY("xterm-256color", 80, 24, 0, 0, emptyMap())
                    val shell = session.startShell()

                    sessionId?.let {
                        ConnectionLogRepository.log(
                            sessionId = it,
                            milestone = ConnectionMilestone.READY,
                            message = "Interactive shell established. Session ready.",
                        )
                    }

                    if (!effectiveProfile.initialDirectory.isNullOrEmpty()) {
                        val sanitized = effectiveProfile.initialDirectory.filter { ch -> ch.code >= 0x20 && ch.code != 0x7F }
                        if (sanitized.isNotBlank()) {
                            val escapedDir = sanitized.replace("'", "'\\''")
                            val cdCmd = "cd '$escapedDir'\r\n"
                            shell.outputStream.write(cdCmd.toByteArray(Charsets.UTF_8))
                            shell.outputStream.flush()
                        }
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
            portForwardingOrchestrator.stopAll()
        } catch (_: Exception) {}
        try {
            client?.disconnect()
        } catch (e: Exception) {
            println(("SshConnectionHandler").toString() + ": " + ("Error during disconnect").toString() + " " + (e).toString())
        }
        try {
            jumpTunnel?.close()
        } catch (e: Exception) {
            println(("SshConnectionHandler").toString() + ": " + ("Error closing jump tunnel").toString() + " " + (e).toString())
        }
        try {
            jumpClient?.disconnect()
        } catch (e: Exception) {
            println(("SshConnectionHandler").toString() + ": " + ("Error during jump client disconnect").toString() + " " + (e).toString())
        }
    }
}
