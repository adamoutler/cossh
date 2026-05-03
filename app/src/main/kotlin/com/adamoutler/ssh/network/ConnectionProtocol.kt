package com.adamoutler.ssh.network

import com.adamoutler.ssh.data.ConnectionProfile
import java.io.OutputStream
import java.security.KeyPair

interface ConnectionProtocol {
    suspend fun connect(
        profile: ConnectionProfile,
        keyPair: KeyPair? = null,
        onOutput: suspend (ByteArray, Int) -> Unit,
        onConnect: (OutputStream, net.schmizz.sshj.connection.channel.direct.Session.Shell?) -> Unit,
    )

    fun disconnect()
}