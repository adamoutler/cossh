package com.adamoutler.ssh.network

import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.PortForwardConfig
import com.adamoutler.ssh.data.PortForwardType
import com.adamoutler.ssh.data.Protocol
import net.schmizz.sshj.SSHClient
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.forward.AcceptAllForwardingFilter
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Paths
import kotlin.concurrent.thread

class PortForwardingOrchestratorCoverageTest {

    private lateinit var orchestrator: PortForwardingOrchestrator
    private lateinit var sshd: SshServer
    private var sshdPort = 0
    private lateinit var client: SSHClient
    private lateinit var echoServer: ServerSocket
    private var echoPort = 0
    private var echoRunning = true

    @Before
    fun setup() {
        orchestrator = PortForwardingOrchestrator()

        // Start mock echo server
        echoServer = ServerSocket(0)
        echoPort = echoServer.localPort
        echoRunning = true
        thread(name = "EchoServerThread") {
            try {
                while (echoRunning && !echoServer.isClosed) {
                    val clientSock = echoServer.accept()
                    thread(name = "EchoClientWorker") {
                        try {
                            clientSock.use { s ->
                                val buffer = ByteArray(1024)
                                val inStream = s.getInputStream()
                                val outStream = s.getOutputStream()
                                var read: Int
                                while (inStream.read(buffer).also { read = it } != -1) {
                                    outStream.write(buffer, 0, read)
                                    outStream.flush()
                                }
                            }
                        } catch (ignored: Exception) {}
                    }
                }
            } catch (ignored: Exception) {}
        }

        // Start SSH server
        sshd = SshServer.setUpDefaultServer()
        sshd.host = "127.0.0.1"
        sshd.port = 0
        sshd.keyPairProvider = SimpleGeneratorHostKeyProvider(Paths.get("hostkey_orchestrator.ser"))
        sshd.passwordAuthenticator = PasswordAuthenticator { username, password, _ ->
            username == "testuser" && password == "testpass"
        }
        sshd.forwardingFilter = AcceptAllForwardingFilter()
        sshd.start()
        sshdPort = sshd.port

        client = SSHClient()
        client.addHostKeyVerifier(net.schmizz.sshj.transport.verification.PromiscuousVerifier())
        client.connect("127.0.0.1", sshdPort)
        client.authPassword("testuser", "testpass")
    }

    @After
    fun teardown() {
        echoRunning = false
        try {
            echoServer.close()
        } catch (ignored: Exception) {}
        orchestrator.stopAll()
        try {
            client.disconnect()
            client.close()
        } catch (ignored: Exception) {}
        try {
            sshd.stop()
        } catch (ignored: Exception) {}
    }

    @Test
    fun testDynamicPortForwarding_Socks5_IPv4_EndToEnd() {
        val socksServer = ServerSocket(0)
        val socksPort = socksServer.localPort
        socksServer.close()

        val profile = ConnectionProfile(
            id = "test-socks",
            nickname = "test-socks",
            host = "localhost",
            port = sshdPort,
            username = "testuser",
            authType = AuthType.PASSWORD,
            protocol = Protocol.SSH,
            portForwards = listOf(
                PortForwardConfig(PortForwardType.DYNAMIC, socksPort, "", 0),
            ),
        )

        orchestrator.startPortForwards(client, profile)
        Thread.sleep(300)

        Socket("127.0.0.1", socksPort).use { sock ->
            sock.soTimeout = 5000
            val out = DataOutputStream(sock.getOutputStream())
            val inp = DataInputStream(sock.getInputStream())

            // 1. Handshake
            out.writeByte(0x05) // SOCKS5
            out.writeByte(0x01) // 1 method
            out.writeByte(0x00) // NO AUTH
            out.flush()

            val sVer = inp.readUnsignedByte()
            val sMethod = inp.readUnsignedByte()
            assertEquals(0x05, sVer)
            assertEquals(0x00, sMethod)

            // 2. CONNECT IPv4 127.0.0.1:echoPort
            out.writeByte(0x05) // VER
            out.writeByte(0x01) // CMD: CONNECT
            out.writeByte(0x00) // RSV
            out.writeByte(0x01) // ATYP: IPv4
            out.write(byteArrayOf(127, 0, 0, 1))
            out.writeShort(echoPort)
            out.flush()

            val repVer = inp.readUnsignedByte()
            val repCode = inp.readUnsignedByte()
            inp.readUnsignedByte() // RSV
            val repAtyp = inp.readUnsignedByte()
            val bndAddr = ByteArray(4)
            inp.readFully(bndAddr)
            inp.readUnsignedShort() // BND.PORT

            assertEquals(0x05, repVer)
            assertEquals(0x00, repCode) // Success
            assertEquals(0x01, repAtyp)

            // 3. Send payload through tunnel
            val testMsg = "HELLO_SOCKS5\n"
            out.write(testMsg.toByteArray(Charsets.UTF_8))
            out.flush()

            val responseBytes = ByteArray(testMsg.length)
            inp.readFully(responseBytes)
            assertEquals(testMsg, String(responseBytes, Charsets.UTF_8))
        }
    }

    @Test
    fun testDynamicPortForwarding_Socks5_DomainName() {
        val socksServer = ServerSocket(0)
        val socksPort = socksServer.localPort
        socksServer.close()

        val profile = ConnectionProfile(
            id = "test-socks-domain",
            nickname = "test-socks-domain",
            host = "localhost",
            port = sshdPort,
            username = "testuser",
            authType = AuthType.PASSWORD,
            protocol = Protocol.SSH,
            portForwards = listOf(
                PortForwardConfig(PortForwardType.DYNAMIC, socksPort, "", 0),
            ),
        )

        orchestrator.startPortForwards(client, profile)
        Thread.sleep(300)

        Socket("127.0.0.1", socksPort).use { sock ->
            sock.soTimeout = 5000
            val out = DataOutputStream(sock.getOutputStream())
            val inp = DataInputStream(sock.getInputStream())

            // 1. Handshake
            out.write(byteArrayOf(0x05, 0x01, 0x00))
            out.flush()
            assertEquals(0x05, inp.readUnsignedByte())
            assertEquals(0x00, inp.readUnsignedByte())

            // 2. CONNECT Domain name "127.0.0.1" : echoPort
            val domainBytes = "127.0.0.1".toByteArray(Charsets.UTF_8)
            out.writeByte(0x05)
            out.writeByte(0x01)
            out.writeByte(0x00)
            out.writeByte(0x03) // ATYP: Domain
            out.writeByte(domainBytes.size)
            out.write(domainBytes)
            out.writeShort(echoPort)
            out.flush()

            assertEquals(0x05, inp.readUnsignedByte())
            assertEquals(0x00, inp.readUnsignedByte())
        }
    }

    @Test
    fun testDynamicPortForwarding_Socks5_IPv6() {
        val socksServer = ServerSocket(0)
        val socksPort = socksServer.localPort
        socksServer.close()

        val profile = ConnectionProfile(
            id = "test-socks-ipv6",
            nickname = "test-socks-ipv6",
            host = "localhost",
            port = sshdPort,
            username = "testuser",
            authType = AuthType.PASSWORD,
            protocol = Protocol.SSH,
            portForwards = listOf(
                PortForwardConfig(PortForwardType.DYNAMIC, socksPort, "", 0),
            ),
        )

        orchestrator.startPortForwards(client, profile)
        Thread.sleep(300)

        Socket("127.0.0.1", socksPort).use { sock ->
            sock.soTimeout = 5000
            val out = DataOutputStream(sock.getOutputStream())
            val inp = DataInputStream(sock.getInputStream())

            // 1. Handshake
            out.write(byteArrayOf(0x05, 0x01, 0x00))
            out.flush()
            assertEquals(0x05, inp.readUnsignedByte())
            assertEquals(0x00, inp.readUnsignedByte())

            // 2. CONNECT IPv6 loopback ::1
            val ipv6Bytes = InetAddress.getByName("::1").address
            out.writeByte(0x05)
            out.writeByte(0x01)
            out.writeByte(0x00)
            out.writeByte(0x04) // ATYP: IPv6
            out.write(ipv6Bytes)
            out.writeShort(echoPort)
            out.flush()

            // Read response
            val ver = inp.readUnsignedByte()
            assertEquals(0x05, ver)
        }
    }

    @Test
    fun testDynamicPortForwarding_Socks5_UnsupportedVersion() {
        val socksServer = ServerSocket(0)
        val socksPort = socksServer.localPort
        socksServer.close()

        val profile = ConnectionProfile(
            id = "test-socks-bad-ver",
            nickname = "test-socks-bad-ver",
            host = "localhost",
            port = sshdPort,
            username = "testuser",
            authType = AuthType.PASSWORD,
            protocol = Protocol.SSH,
            portForwards = listOf(
                PortForwardConfig(PortForwardType.DYNAMIC, socksPort, "", 0),
            ),
        )

        orchestrator.startPortForwards(client, profile)
        Thread.sleep(300)

        Socket("127.0.0.1", socksPort).use { sock ->
            sock.soTimeout = 2000
            val out = sock.getOutputStream()
            out.write(byteArrayOf(0x04, 0x01, 0x00)) // SOCKS4
            out.flush()
            val read = sock.getInputStream().read()
            assertEquals(-1, read)
        }
    }

    @Test
    fun testDynamicPortForwarding_Socks5_UnsupportedRequestVersion() {
        val socksServer = ServerSocket(0)
        val socksPort = socksServer.localPort
        socksServer.close()

        val profile = ConnectionProfile(
            id = "test-socks-bad-req-ver",
            nickname = "test-socks-bad-req-ver",
            host = "localhost",
            port = sshdPort,
            username = "testuser",
            authType = AuthType.PASSWORD,
            protocol = Protocol.SSH,
            portForwards = listOf(
                PortForwardConfig(PortForwardType.DYNAMIC, socksPort, "", 0),
            ),
        )

        orchestrator.startPortForwards(client, profile)
        Thread.sleep(300)

        Socket("127.0.0.1", socksPort).use { sock ->
            sock.soTimeout = 2000
            val out = DataOutputStream(sock.getOutputStream())
            val inp = DataInputStream(sock.getInputStream())

            // Handshake OK
            out.write(byteArrayOf(0x05, 0x01, 0x00))
            out.flush()
            assertEquals(0x05, inp.readUnsignedByte())
            assertEquals(0x00, inp.readUnsignedByte())

            // Request with version 0x04
            out.writeByte(0x04)
            out.writeByte(0x01)
            out.writeByte(0x00)
            out.writeByte(0x01)
            out.write(byteArrayOf(127, 0, 0, 1))
            out.writeShort(echoPort)
            out.flush()

            val read = sock.getInputStream().read()
            assertEquals(-1, read)
        }
    }

    @Test
    fun testDynamicPortForwarding_Socks5_UnsupportedCommand() {
        val socksServer = ServerSocket(0)
        val socksPort = socksServer.localPort
        socksServer.close()

        val profile = ConnectionProfile(
            id = "test-socks-bad-cmd",
            nickname = "test-socks-bad-cmd",
            host = "localhost",
            port = sshdPort,
            username = "testuser",
            authType = AuthType.PASSWORD,
            protocol = Protocol.SSH,
            portForwards = listOf(
                PortForwardConfig(PortForwardType.DYNAMIC, socksPort, "", 0),
            ),
        )

        orchestrator.startPortForwards(client, profile)
        Thread.sleep(300)

        Socket("127.0.0.1", socksPort).use { sock ->
            sock.soTimeout = 2000
            val out = DataOutputStream(sock.getOutputStream())
            val inp = DataInputStream(sock.getInputStream())

            // Handshake OK
            out.write(byteArrayOf(0x05, 0x01, 0x00))
            out.flush()
            assertEquals(0x05, inp.readUnsignedByte())
            assertEquals(0x00, inp.readUnsignedByte())

            // Command 0x02 (BIND)
            out.writeByte(0x05)
            out.writeByte(0x02) // Unsupported CMD
            out.writeByte(0x00)
            out.writeByte(0x01)
            out.write(byteArrayOf(127, 0, 0, 1))
            out.writeShort(echoPort)
            out.flush()

            val ver = inp.readUnsignedByte()
            val rep = inp.readUnsignedByte()
            assertEquals(0x05, ver)
            assertEquals(0x07, rep) // Command not supported
        }
    }

    @Test
    fun testDynamicPortForwarding_Socks5_UnsupportedAddressType() {
        val socksServer = ServerSocket(0)
        val socksPort = socksServer.localPort
        socksServer.close()

        val profile = ConnectionProfile(
            id = "test-socks-bad-atyp",
            nickname = "test-socks-bad-atyp",
            host = "localhost",
            port = sshdPort,
            username = "testuser",
            authType = AuthType.PASSWORD,
            protocol = Protocol.SSH,
            portForwards = listOf(
                PortForwardConfig(PortForwardType.DYNAMIC, socksPort, "", 0),
            ),
        )

        orchestrator.startPortForwards(client, profile)
        Thread.sleep(300)

        Socket("127.0.0.1", socksPort).use { sock ->
            sock.soTimeout = 2000
            val out = DataOutputStream(sock.getOutputStream())
            val inp = DataInputStream(sock.getInputStream())

            out.write(byteArrayOf(0x05, 0x01, 0x00))
            out.flush()
            assertEquals(0x05, inp.readUnsignedByte())
            assertEquals(0x00, inp.readUnsignedByte())

            // Address type 0x09 (Unsupported)
            out.writeByte(0x05)
            out.writeByte(0x01)
            out.writeByte(0x00)
            out.writeByte(0x09) // Bad ATYP
            out.flush()

            val read = sock.getInputStream().read()
            assertEquals(-1, read)
        }
    }

    @Test
    fun testRemotePortForwarding_WithNonEmptyRemoteHost() {
        val s = ServerSocket(0)
        val localPort = s.localPort
        s.close()

        val profile = ConnectionProfile(
            id = "test-remote-custom-host",
            nickname = "test-remote-custom-host",
            host = "localhost",
            port = sshdPort,
            username = "testuser",
            authType = AuthType.PASSWORD,
            protocol = Protocol.SSH,
            portForwards = listOf(
                PortForwardConfig(PortForwardType.REMOTE, localPort, "127.0.0.1", 9090),
            ),
        )

        orchestrator.startPortForwards(client, profile)
        Thread.sleep(300)
    }

    @Test
    fun testStopAll_WithActiveDynamicClientThreads() {
        val socksServer = ServerSocket(0)
        val socksPort = socksServer.localPort
        socksServer.close()

        val profile = ConnectionProfile(
            id = "test-socks-stop",
            nickname = "test-socks-stop",
            host = "localhost",
            port = sshdPort,
            username = "testuser",
            authType = AuthType.PASSWORD,
            protocol = Protocol.SSH,
            portForwards = listOf(
                PortForwardConfig(PortForwardType.DYNAMIC, socksPort, "", 0),
            ),
        )

        orchestrator.startPortForwards(client, profile)
        Thread.sleep(300)

        val sock = Socket("127.0.0.1", socksPort)
        val out = sock.getOutputStream()
        out.write(byteArrayOf(0x05, 0x01, 0x00))
        out.flush()

        orchestrator.stopAll()
        sock.close()
    }
}
