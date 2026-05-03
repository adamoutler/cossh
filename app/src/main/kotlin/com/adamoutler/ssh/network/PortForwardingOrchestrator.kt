package com.adamoutler.ssh.network

import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.PortForwardType
import net.schmizz.sshj.SSHClient
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

class PortForwardingOrchestrator {
    private val localServerSockets = mutableListOf<ServerSocket>()

    fun startPortForwards(client: SSHClient, profile: ConnectionProfile) {
        profile.portForwards.forEach { config ->
            try {
                if (config.type == PortForwardType.LOCAL) {
                    val params = net.schmizz.sshj.connection.channel.direct.Parameters("127.0.0.1", config.localPort, config.remoteHost, config.remotePort)
                    val serverSocket = ServerSocket()
                    serverSocket.reuseAddress = true
                    serverSocket.bind(InetSocketAddress("127.0.0.1", config.localPort))
                    localServerSockets.add(serverSocket)
                    val localPortForwarder = client.newLocalPortForwarder(params, serverSocket)
                    thread(name = "LocalPortForwarder_${config.localPort}") {
                        try {
                            localPortForwarder.listen()
                        } catch (e: Exception) {
                            android.util.Log.e("PortForwardingOrchestrator", "Local port forwarder error", e)
                        } finally {
                            try {
                                serverSocket.close()
                            } catch (e: Exception) {}
                        }
                    }
                } else if (config.type == PortForwardType.REMOTE) {
                    val remoteForward = net.schmizz.sshj.connection.channel.forwarded.RemotePortForwarder.Forward(config.remotePort)
                    val host = if (config.remoteHost.isEmpty()) "127.0.0.1" else config.remoteHost
                    val localTargetAddress = InetSocketAddress(host, config.localPort)
                    val localListener = net.schmizz.sshj.connection.channel.forwarded.SocketForwardingConnectListener(localTargetAddress)
                    client.remotePortForwarder.bind(remoteForward, localListener)
                }
            } catch (e: Exception) {
                android.util.Log.e("PortForwardingOrchestrator", "Failed to setup port forward $config", e)
            }
        }
    }

    fun stopAll() {
        try {
            localServerSockets.forEach { it.close() }
            localServerSockets.clear()
        } catch (e: Exception) {
            android.util.Log.e("PortForwardingOrchestrator", "Error closing local port forwarders", e)
        }
    }
}
