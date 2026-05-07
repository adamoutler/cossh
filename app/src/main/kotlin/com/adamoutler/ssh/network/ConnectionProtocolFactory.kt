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
) {
    fun create(protocol: Protocol): ConnectionProtocol = when (protocol) {
        Protocol.TELNET -> TelnetConnectionHandler()
        else -> SshConnectionHandler(hostKeyVerifier, identityStorageManager, context, portForwardingOrchestrator)
    }
}
