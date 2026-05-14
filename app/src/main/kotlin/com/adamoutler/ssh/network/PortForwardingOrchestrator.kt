package com.adamoutler.ssh.network

import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.PortForwardType
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.DirectTCPIPChannel
import net.schmizz.sshj.common.StreamCopier
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class PortForwardingOrchestrator {
    companion object {
        private const val LOCAL_HOST = "127.0.0.1"
    }

    private val localServerSockets = mutableListOf<ServerSocket>()
    private val clientThreads = mutableListOf<Thread>()

    fun startPortForwards(client: SSHClient, profile: ConnectionProfile) {
        profile.portForwards.forEach { config ->
            try {
                if (config.type == PortForwardType.LOCAL) {
                    startLocalPortForward(client, config)
                } else if (config.type == PortForwardType.REMOTE) {
                    startRemotePortForward(client, config)
                } else if (config.type == PortForwardType.DYNAMIC) {
                    startDynamicPortForward(client, config)
                }
            } catch (e: Exception) {
                println(("PortForwardingOrchestrator").toString() + ": " + ("Failed to setup port forward $config").toString() + " " + (e).toString())
            }
        }
    }

    private fun startLocalPortForward(client: SSHClient, config: com.adamoutler.ssh.data.PortForwardConfig) {
        val params = net.schmizz.sshj.connection.channel.direct.Parameters(LOCAL_HOST, config.localPort, config.remoteHost, config.remotePort)
        val serverSocket = ServerSocket()
        serverSocket.reuseAddress = true
        serverSocket.bind(InetSocketAddress(LOCAL_HOST, config.localPort))
        localServerSockets.add(serverSocket)
        val localPortForwarder = client.newLocalPortForwarder(params, serverSocket)
        thread(name = "LocalPortForwarder_${config.localPort}") {
            try {
                localPortForwarder.listen()
            } catch (e: Exception) {
                println(("PortForwardingOrchestrator").toString() + ": " + ("Local port forwarder error").toString() + " " + (e).toString())
            } finally {
                try {
                    serverSocket.close()
                } catch (e: Exception) {
                    // Ignore exception on close
                }
            }
        }
    }

    private fun startRemotePortForward(client: SSHClient, config: com.adamoutler.ssh.data.PortForwardConfig) {
        val remoteForward = net.schmizz.sshj.connection.channel.forwarded.RemotePortForwarder.Forward(config.remotePort)
        val host = if (config.remoteHost.isEmpty()) LOCAL_HOST else config.remoteHost
        val localTargetAddress = InetSocketAddress(host, config.localPort)
        val localListener = net.schmizz.sshj.connection.channel.forwarded.SocketForwardingConnectListener(localTargetAddress)
        client.remotePortForwarder.bind(remoteForward, localListener)
    }

    private fun startDynamicPortForward(client: SSHClient, config: com.adamoutler.ssh.data.PortForwardConfig) {
        val serverSocket = ServerSocket()
        serverSocket.reuseAddress = true
        serverSocket.bind(InetSocketAddress(LOCAL_HOST, config.localPort))
        localServerSockets.add(serverSocket)
        
        thread(name = "DynamicPortForwarder_${config.localPort}") {
            try {
                while (!serverSocket.isClosed) {
                    val socket = serverSocket.accept()
                    val t = thread(name = "SOCKS5_Client_${socket.port}") {
                        handleSocks5Connection(client, socket)
                    }
                    clientThreads.add(t)
                }
            } catch (e: Exception) {
                println("Dynamic port forwarder error: $e")
            } finally {
                try {
                    serverSocket.close()
                } catch (e: Exception) {}
            }
        }
    }

    private fun handleSocks5Connection(client: SSHClient, socket: Socket) {
        try {
            val input = DataInputStream(socket.getInputStream())
            val output = DataOutputStream(socket.getOutputStream())

            // SOCKS5 Handshake
            val version = input.readUnsignedByte()
            if (version != 0x05) throw IOException("Unsupported SOCKS version: $version")
            val numMethods = input.readUnsignedByte()
            val methods = ByteArray(numMethods)
            input.readFully(methods)

            // Reply: NO AUTHENTICATION REQUIRED
            output.writeByte(0x05)
            output.writeByte(0x00)
            output.flush()

            // Read Request
            val reqVersion = input.readUnsignedByte()
            if (reqVersion != 0x05) throw IOException("Unsupported SOCKS version in request: $reqVersion")
            val command = input.readUnsignedByte()
            if (command != 0x01) { // CONNECT
                // Unsupported command
                output.writeByte(0x05)
                output.writeByte(0x07) // Command not supported
                output.write(byteArrayOf(0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
                output.flush()
                throw IOException("Unsupported SOCKS command: $command")
            }
            input.readUnsignedByte() // RSV
            val addressType = input.readUnsignedByte()

            val targetHost: String
            when (addressType) {
                0x01 -> { // IPv4
                    val ipv4 = ByteArray(4)
                    input.readFully(ipv4)
                    targetHost = InetAddress.getByAddress(ipv4).hostAddress ?: ""
                }
                0x03 -> { // Domain name
                    val len = input.readUnsignedByte()
                    val domain = ByteArray(len)
                    input.readFully(domain)
                    targetHost = String(domain, Charsets.UTF_8)
                }
                0x04 -> { // IPv6
                    val ipv6 = ByteArray(16)
                    input.readFully(ipv6)
                    targetHost = InetAddress.getByAddress(ipv6).hostAddress ?: ""
                }
                else -> throw IOException("Unsupported SOCKS address type: $addressType")
            }
            val targetPort = input.readUnsignedShort()

            // Open SSH tunnel
            var channel: DirectTCPIPChannel? = null
            try {
                channel = net.schmizz.sshj.connection.channel.direct.ChannelFactory.createDirectTCPIPChannel(client.connection, LOCAL_HOST, socket.port, targetHost, targetPort)
            } catch (e: Exception) {
                // Reply: Host unreachable / connection refused
                output.writeByte(0x05)
                output.writeByte(0x04)
                output.write(byteArrayOf(0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
                output.flush()
                throw e
            }

            // Reply: Success
            output.writeByte(0x05)
            output.writeByte(0x00) // Success
            output.writeByte(0x00) // RSV
            output.writeByte(0x01) // IPv4
            output.write(byteArrayOf(0x00, 0x00, 0x00, 0x00)) // BND.ADDR
            output.writeShort(0) // BND.PORT
            output.flush()

            val toServer = StreamCopier(socket.getInputStream(), channel.outputStream, client.transport.config.loggerFactory)
                .spawnDaemon("socks5-to-server")
            val toClient = StreamCopier(channel.inputStream, socket.getOutputStream(), client.transport.config.loggerFactory)
                .spawnDaemon("server-to-socks5")

            toServer.await()
            toClient.await()
            channel.close()
            socket.close()

        } catch (e: Exception) {
            println("SOCKS5 connection error: $e")
            try { socket.close() } catch (ignored: Exception) {}
        }
    }

    fun stopAll() {
        try {
            localServerSockets.forEach { it.close() }
            localServerSockets.clear()
            clientThreads.forEach { it.interrupt() }
            clientThreads.clear()
        } catch (e: Exception) {
            println(("PortForwardingOrchestrator").toString() + ": " + ("Error closing local port forwarders").toString() + " " + (e).toString())
        }
    }
}
