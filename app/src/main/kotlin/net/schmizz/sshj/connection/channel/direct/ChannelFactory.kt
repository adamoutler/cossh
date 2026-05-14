package net.schmizz.sshj.connection.channel.direct

import net.schmizz.sshj.connection.Connection

object ChannelFactory {
    fun createDirectTCPIPChannel(connection: Connection, localHost: String, localPort: Int, remoteHost: String, remotePort: Int): DirectTCPIPChannel {
        val params = Parameters(localHost, localPort, remoteHost, remotePort)
        val channel = DirectTCPIPChannel(connection, params)
        channel.open()
        return channel
    }
}
