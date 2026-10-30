package com.adamoutler.ssh.network

import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.PortForwardType
import com.adamoutler.ssh.util.AppLog
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.StreamCopier
import net.schmizz.sshj.connection.channel.direct.DirectTCPIPChannel
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

open class PortForwardingOrchestrator {
    companion object {
        private const val TAG = "PortForwardingOrchestrator"
        private const val LOCAL_HOST = "127.0.0.1"
        const val MAX_CONCURRENT_FORWARD_CONNECTIONS = 32
        const val SERVER_SOCKET_SO_TIMEOUT_MS = 5000
        const val SOCKS5_HANDSHAKE_TIMEOUT_MS = 15_000
    }

    private val localServerSockets = CopyOnWriteArrayList<ServerSocket>()
    val activeClientSockets = ConcurrentHashMap.newKeySet<Socket>()
    private val socksExecutor = Executors.newFixedThreadPool(16) { runnable ->
        Thread(runnable, "SOCKS5_Worker").apply { isDaemon = true }
    }

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
                AppLog.e(TAG, "Failed to setup port forward $config: ${e.message}", e)
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
                AppLog.w(TAG, "Local port forwarder error: ${e.message}", e)
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
        val remoteForward = net.schmizz.sshj.connection.channel.forwarded.RemotePortForwarder.Forward(LOCAL_HOST, config.remotePort)
        val host = if (config.remoteHost.isEmpty()) LOCAL_HOST else config.remoteHost
        val localTargetAddress = InetSocketAddress(host, config.localPort)
        val localListener = net.schmizz.sshj.connection.channel.forwarded.SocketForwardingConnectListener(localTargetAddress)
        client.remotePortForwarder.bind(remoteForward, localListener)
    }

    private fun startDynamicPortForward(client: SSHClient, config: com.adamoutler.ssh.data.PortForwardConfig) {
        val serverSocket = ServerSocket()
        serverSocket.reuseAddress = true
        serverSocket.soTimeout = SERVER_SOCKET_SO_TIMEOUT_MS
        serverSocket.bind(InetSocketAddress(LOCAL_HOST, config.localPort))
        localServerSockets.add(serverSocket)

        thread(name = "DynamicPortForwarder_${config.localPort}") {
            try {
                while (!serverSocket.isClosed) {
                    val socket = try {
                        serverSocket.accept()
                    } catch (e: java.net.SocketTimeoutException) {
                        continue
                    } catch (e: Exception) {
                        if (!serverSocket.isClosed) {
                            AppLog.e(TAG, "Dynamic port forwarder accept error: ${e.message}", e)
                        }
                        break
                    }

                    if (activeClientSockets.size >= MAX_CONCURRENT_FORWARD_CONNECTIONS) {
                        AppLog.w(TAG, "Max concurrent connections reached ($MAX_CONCURRENT_FORWARD_CONNECTIONS), rejecting socket")
                        try {
                            socket.close()
                        } catch (e: Exception) {
                            // ignore
                        }
                        continue
                    }

                    activeClientSockets.add(socket)
                    socksExecutor.execute {
                        handleSocks5Connection(client, socket)
                    }
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Dynamic port forwarder error: ${e.message}", e)
            } finally {
                try {
                    serverSocket.close()
                } catch (e: Exception) {
                    AppLog.w(TAG, "Error closing dynamic server socket: ${e.message}")
                }
            }
        }
    }

    private fun handleSocks5Connection(client: SSHClient, socket: Socket) {
        try {
            socket.soTimeout = SOCKS5_HANDSHAKE_TIMEOUT_MS
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
            val channel = net.schmizz.sshj.connection.channel.direct.ChannelFactory.createDirectTCPIPChannel(client.connection, LOCAL_HOST, socket.port, targetHost, targetPort)

            // Reset socket to streaming mode once tunnel opens
            socket.soTimeout = 0
            socket.keepAlive = true
            socket.tcpNoDelay = true

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
        } catch (e: Exception) {
            AppLog.w(TAG, "SOCKS5 connection error: ${e.message}")
        } finally {
            activeClientSockets.remove(socket)
            try {
                socket.close()
            } catch (ignored: Exception) {
                AppLog.w(TAG, "Failed to close socket: ${ignored.message}")
            }
        }
    }

    open fun stopAll() {
        try {
            localServerSockets.forEach {
                try {
                    it.close()
                } catch (ignored: Exception) {}
            }
            localServerSockets.clear()

            activeClientSockets.forEach {
                try {
                    it.close()
                } catch (ignored: Exception) {}
            }
            activeClientSockets.clear()

            socksExecutor.shutdownNow()
            socksExecutor.awaitTermination(1, TimeUnit.SECONDS)
        } catch (e: Exception) {
            AppLog.e(TAG, "Error closing local port forwarders: ${e.message}", e)
        }
    }
}
