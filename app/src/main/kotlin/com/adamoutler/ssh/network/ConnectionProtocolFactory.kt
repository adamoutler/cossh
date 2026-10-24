package com.adamoutler.ssh.network

import android.content.Context
import com.adamoutler.ssh.crypto.IdentityStorageManager
import com.adamoutler.ssh.data.Protocol
import net.schmizz.sshj.transport.verification.HostKeyVerifier

class ConnectionProtocolFactory(
    private val hostKeyVerifier: HostKeyVerifier? = null,
    private val identityStorageManager: IdentityStorageManager? = null,
    private val context: Context? = null,
    private val portForwardingOrchestrator: PortForwardingOrchestrator,
    private val securityStorageManager: com.adamoutler.ssh.crypto.SecurityStorageManager? = null,
) {
    fun create(protocol: Protocol): ConnectionProtocol = when (protocol) {
        Protocol.TELNET -> TelnetConnectionHandler()

        else -> SshConnectionHandler(
            hostKeyVerifier = hostKeyVerifier,
            identityStorageManager = identityStorageManager,
            context = context,
            portForwardingOrchestrator = portForwardingOrchestrator,
            securityStorageManager = securityStorageManager,
        )
    }
}
