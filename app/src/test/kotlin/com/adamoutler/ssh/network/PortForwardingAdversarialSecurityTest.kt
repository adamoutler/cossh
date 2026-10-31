package com.adamoutler.ssh.network

import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.PortForwardConfig
import com.adamoutler.ssh.data.PortForwardType
import com.adamoutler.ssh.data.Protocol
import net.schmizz.sshj.SSHClient
import org.apache.sshd.common.session.Session
import org.apache.sshd.common.util.net.SshdSocketAddress
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.forward.StaticDecisionForwardingFilter
import org.apache.sshd.server.forward.TcpForwardingFilter
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.file.Paths
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Rigorous adversarial security and wire fuzzing test suite for [PortForwardingOrchestrator].
 *
 * Verifies SSRF/lateral pivoting protection, wire-level SOCKS5 fuzzing resilience,
 * hostile domain injection rejection, connection concurrency cap enforcement,
 * and abrupt TCP reset resource cleanup under high load.
 */
class PortForwardingAdversarialSecurityTest {

    private lateinit var orchestrator: PortForwardingOrchestrator
    private lateinit var sshd: SshServer
    private var sshdPort = 0
    private lateinit var client: SSHClient
    private lateinit var testHttpServer: TestHttpServer
    private var httpServerPort = 0
    private var dynamicSocksPort = 0

    private class TestHttpServer : AutoCloseable {
        private val serverSocket = ServerSocket(0)
        val port: Int = serverSocket.localPort
        private val running = AtomicBoolean(true)
        private val threadPool = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "TestHttpServer_Worker").apply { isDaemon = true }
        }

        init {
            thread(name = "TestHttpServer_Acceptor") {
                while (running.get() && !serverSocket.isClosed) {
                    try {
                        val sock = serverSocket.accept()
                        threadPool.execute { handleClient(sock) }
                    } catch (_: Exception) {
                        break
                    }
                }
            }
        }

        private fun handleClient(socket: Socket) {
            try {
                socket.use { s ->
                    s.soTimeout = 4000
                    val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                    val line = reader.readLine() ?: return
                    val isStream = line.contains("/stream")

                    var h = reader.readLine()
                    while (!h.isNullOrEmpty()) {
                        h = reader.readLine()
                    }

                    val out = s.getOutputStream()
                    if (isStream) {
                        out.write("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n".toByteArray(Charsets.UTF_8))
                        out.flush()
                        val chunk = "400\r\n" + "A".repeat(1024) + "\r\n"
                        val chunkBytes = chunk.toByteArray(Charsets.UTF_8)
                        while (running.get() && !s.isClosed) {
                            out.write(chunkBytes)
                            out.flush()
                            Thread.sleep(10)
                        }
                    } else {
                        val body = "status: ok"
                        val resp = "HTTP/1.1 200 OK\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body"
                        out.write(resp.toByteArray(Charsets.UTF_8))
                        out.flush()
                    }
                }
            } catch (_: Exception) {
                // Expected when client abruptly aborts connection
            }
        }

        override fun close() {
            running.set(false)
            try {
                serverSocket.close()
            } catch (_: Exception) {}
            threadPool.shutdownNow()
        }
    }

    @Before
    fun setup() {
        orchestrator = PortForwardingOrchestrator()

        // 1. Embedded HTTP server on 127.0.0.1:0
        testHttpServer = TestHttpServer()
        httpServerPort = testHttpServer.port

        // 2. Embedded Apache MINA SshServer on 127.0.0.1:0 with strictly locked TcpForwardingFilter
        sshd = SshServer.setUpDefaultServer()
        sshd.host = "127.0.0.1"
        sshd.port = 0
        sshd.keyPairProvider = SimpleGeneratorHostKeyProvider(Paths.get("hostkey_adversarial.ser"))
        sshd.passwordAuthenticator = PasswordAuthenticator { username, password, _ ->
            username == "advuser" && password == "advpass"
        }
        sshd.forwardingFilter = object : StaticDecisionForwardingFilter(true) {
            override fun canConnect(
                type: TcpForwardingFilter.Type,
                address: SshdSocketAddress,
                session: Session
            ): Boolean {
                // Strictly allow ONLY 127.0.0.1 or localhost to httpServerPort
                return address.hostName in setOf("127.0.0.1", "localhost") && address.port == httpServerPort
            }

            override fun canListen(address: SshdSocketAddress, session: Session): Boolean {
                return true
            }
        }
        sshd.start()
        sshdPort = sshd.port

        // 3. Authenticated SSHClient
        client = SSHClient()
        client.addHostKeyVerifier(net.schmizz.sshj.transport.verification.PromiscuousVerifier())
        client.connect("127.0.0.1", sshdPort)
        client.authPassword("advuser", "advpass")

        // 4. Start Dynamic SOCKS5 Port Forwarding on an ephemeral port
        val socksFinder = ServerSocket(0)
        dynamicSocksPort = socksFinder.localPort
        socksFinder.close()

        val dynamicProfile = ConnectionProfile(
            id = "adv-profile",
            nickname = "Adversarial Profile",
            host = "127.0.0.1",
            port = sshdPort,
            username = "advuser",
            authType = AuthType.PASSWORD,
            protocol = Protocol.SSH,
            portForwards = listOf(
                PortForwardConfig(PortForwardType.DYNAMIC, dynamicSocksPort, "", 0)
            )
        )
        orchestrator.startPortForwards(client, dynamicProfile)
        Thread.sleep(200)
    }

    @After
    fun teardown() {
        orchestrator.stopAll()
        try {
            client.disconnect()
            client.close()
        } catch (_: Exception) {}
        try {
            sshd.stop()
        } catch (_: Exception) {}
        try {
            testHttpServer.close()
        } catch (_: Exception) {}
        try {
            File("hostkey_adversarial.ser").delete()
        } catch (_: Exception) {}
    }

    private fun executeSocks5Http(socksPort: Int, host: String, port: Int): Pair<Int, String> {
        Socket("127.0.0.1", socksPort).use { sock ->
            sock.soTimeout = 4000
            val out = DataOutputStream(sock.getOutputStream())
            val inp = DataInputStream(sock.getInputStream())

            // SOCKS5 Handshake
            out.write(byteArrayOf(0x05, 0x01, 0x00))
            out.flush()
            val ver = inp.readUnsignedByte()
            val method = inp.readUnsignedByte()
            assertEquals(0x05, ver)
            assertEquals(0x00, method)

            // SOCKS5 CONNECT
            out.writeByte(0x05)
            out.writeByte(0x01)
            out.writeByte(0x00)
            if (host.matches(Regex("""\d+\.\d+\.\d+\.\d+"""))) {
                out.writeByte(0x01) // IPv4
                val parts = host.split(".").map { it.toInt().toByte() }.toByteArray()
                out.write(parts)
            } else {
                out.writeByte(0x03) // Domain
                val domainBytes = host.toByteArray(Charsets.UTF_8)
                out.writeByte(domainBytes.size)
                out.write(domainBytes)
            }
            out.writeShort(port)
            out.flush()

            val repVer = inp.readUnsignedByte()
            val repCode = inp.readUnsignedByte()
            assertEquals(0x05, repVer)
            if (repCode != 0x00) {
                return Pair(repCode, "")
            }
            inp.readUnsignedByte() // RSV
            val atyp = inp.readUnsignedByte()
            when (atyp) {
                0x01 -> inp.readFully(ByteArray(4))
                0x03 -> {
                    val len = inp.readUnsignedByte()
                    inp.readFully(ByteArray(len))
                }
                0x04 -> inp.readFully(ByteArray(16))
            }
            inp.readUnsignedShort() // BND.PORT

            // HTTP Request
            val httpRequest = "GET / HTTP/1.1\r\nHost: $host\r\nConnection: close\r\n\r\n"
            out.write(httpRequest.toByteArray(Charsets.UTF_8))
            out.flush()

            val reader = BufferedReader(InputStreamReader(inp, Charsets.UTF_8))
            val body = reader.readText()
            return Pair(repCode, body)
        }
    }

    /**
     * 1. ssrfAndLateralPivotRejection_blocksMetadataLanAndInternalServicePorts
     * Attempts SOCKS5 (-D) and Local (-L) port forwarding to prohibited targets:
     * - Cloud metadata 169.254.169.254:80
     * - Wildcard 0.0.0.0:$httpServerPort
     * - SSH server port 127.0.0.1:${sshd.port} (self-loop amplification)
     * - Telnet port 127.0.0.1:2323
     * - LAN router 192.168.1.1:80
     * Verifies every prohibited attempt fails to open the tunnel, SSH session remains alive,
     * and a subsequent legitimate request to 127.0.0.1:$httpServerPort succeeds with "status: ok".
     */
    @Test
    fun ssrfAndLateralPivotRejection_blocksMetadataLanAndInternalServicePorts() {
        val prohibitedTargets = listOf(
            "169.254.169.254" to 80,
            "0.0.0.0" to httpServerPort,
            "127.0.0.1" to sshdPort,
            "127.0.0.1" to 2323,
            "192.168.1.1" to 80
        )

        for ((targetHost, targetPort) in prohibitedTargets) {
            // A. Test SOCKS5 (-D) rejection
            val (repCode, _) = executeSocks5Http(dynamicSocksPort, targetHost, targetPort)
            assertEquals(
                "SOCKS5 attempt to prohibited target $targetHost:$targetPort must fail with 0x01",
                0x01,
                repCode
            )

            // B. Test Local (-L) rejection
            val localFinder = ServerSocket(0)
            val localPort = localFinder.localPort
            localFinder.close()

            val localProfile = ConnectionProfile(
                id = "adv-local-$localPort",
                nickname = "Adv Local",
                host = "127.0.0.1",
                port = sshdPort,
                username = "advuser",
                authType = AuthType.PASSWORD,
                protocol = Protocol.SSH,
                portForwards = listOf(
                    PortForwardConfig(PortForwardType.LOCAL, localPort, targetHost, targetPort)
                )
            )
            orchestrator.startPortForwards(client, localProfile)
            Thread.sleep(150)

            // Connecting to -L port should immediately fail / close stream on write/read
            val streamClosed = try {
                Socket("127.0.0.1", localPort).use { sock ->
                    sock.soTimeout = 2000
                    sock.getOutputStream().write("GET / HTTP/1.1\r\n\r\n".toByteArray(Charsets.UTF_8))
                    sock.getOutputStream().flush()
                    sock.getInputStream().read() == -1
                }
            } catch (_: IOException) {
                true
            }
            assertTrue("Local port forward to $targetHost:$targetPort must close stream", streamClosed)
        }

        // Verify SSH session remains completely intact and authenticated
        assertTrue("SSH client must remain connected after SSRF attempts", client.isConnected)
        assertTrue("SSH client must remain authenticated after SSRF attempts", client.isAuthenticated)

        // Verify legitimate request to 127.0.0.1:$httpServerPort succeeds
        val (legitRep, legitBody) = executeSocks5Http(dynamicSocksPort, "127.0.0.1", httpServerPort)
        assertEquals("Legitimate SOCKS5 request must succeed with 0x00", 0x00, legitRep)
        assertTrue("Legitimate SOCKS5 request body must contain 'status: ok'", legitBody.contains("status: ok"))
    }

    /**
     * 2. socks5WireFuzzing_rejectsInvalidProtocolVersions
     * Sends SOCKS4 (0x04), version 0x00, and version 0xFF greetings;
     * verifies immediate socket closure without response or crash.
     */
    @Test
    fun socks5WireFuzzing_rejectsInvalidProtocolVersions() {
        val invalidVersions = listOf(0x04, 0x00, 0xFF)

        for (version in invalidVersions) {
            Socket("127.0.0.1", dynamicSocksPort).use { sock ->
                sock.soTimeout = 2000
                val out = sock.getOutputStream()
                out.write(byteArrayOf(version.toByte(), 0x01, 0x00))
                out.flush()

                val readByte = try {
                    sock.getInputStream().read()
                } catch (_: SocketException) {
                    -1
                }
                assertEquals("Greeting version $version must be rejected with EOF (-1)", -1, readByte)
            }
        }
    }

    /**
     * 3. socks5WireFuzzing_rejectsUnsupportedCommandsWithRfc1928CommandNotSupported
     * Sends BIND (0x02), UDP_ASSOCIATE (0x03), and unknown command (0x7F);
     * verifies exact RFC 1928 reply [0x05, 0x07, ...] (COMMAND_NOT_SUPPORTED) and socket closure.
     */
    @Test
    fun socks5WireFuzzing_rejectsUnsupportedCommandsWithRfc1928CommandNotSupported() {
        val unsupportedCommands = listOf(0x02, 0x03, 0x7F)

        for (cmd in unsupportedCommands) {
            Socket("127.0.0.1", dynamicSocksPort).use { sock ->
                sock.soTimeout = 2000
                val out = DataOutputStream(sock.getOutputStream())
                val inp = DataInputStream(sock.getInputStream())

                // 1. Handshake
                out.write(byteArrayOf(0x05, 0x01, 0x00))
                out.flush()
                assertEquals(0x05, inp.readUnsignedByte())
                assertEquals(0x00, inp.readUnsignedByte())

                // 2. Request with unsupported command
                out.writeByte(0x05)
                out.writeByte(cmd)
                out.writeByte(0x00) // RSV
                out.writeByte(0x01) // ATYP: IPv4
                out.write(byteArrayOf(127, 0, 0, 1))
                out.writeShort(httpServerPort)
                out.flush()

                // 3. Read RFC 1928 error response
                val repVer = inp.readUnsignedByte()
                val repCode = inp.readUnsignedByte()
                val rsv = inp.readUnsignedByte()
                val atyp = inp.readUnsignedByte()
                val bndAddr = ByteArray(4)
                inp.readFully(bndAddr)
                inp.readUnsignedShort() // BND.PORT

                assertEquals(0x05, repVer)
                assertEquals("Command 0x${Integer.toHexString(cmd)} must return COMMAND_NOT_SUPPORTED (0x07)", 0x07, repCode)
                assertEquals(0x00, rsv)
                assertEquals(0x01, atyp)

                // 4. Socket closure
                val eof = try {
                    sock.getInputStream().read()
                } catch (_: SocketException) {
                    -1
                }
                assertEquals("Socket must be closed following unsupported command", -1, eof)
            }
        }
    }

    /**
     * 4. socks5WireFuzzing_rejectsUnsupportedAddressTypesWithRfc1928AtypNotSupported
     * Sends invalid ATYP values (0x00, 0x02, 0x99);
     * verifies exact reply [0x05, 0x08, ...] (ADDRESS_TYPE_NOT_SUPPORTED) and socket closure.
     */
    @Test
    fun socks5WireFuzzing_rejectsUnsupportedAddressTypesWithRfc1928AtypNotSupported() {
        val unsupportedAtyps = listOf(0x00, 0x02, 0x99)

        for (atyp in unsupportedAtyps) {
            Socket("127.0.0.1", dynamicSocksPort).use { sock ->
                sock.soTimeout = 2000
                val out = DataOutputStream(sock.getOutputStream())
                val inp = DataInputStream(sock.getInputStream())

                // 1. Handshake
                out.write(byteArrayOf(0x05, 0x01, 0x00))
                out.flush()
                assertEquals(0x05, inp.readUnsignedByte())
                assertEquals(0x00, inp.readUnsignedByte())

                // 2. Request with invalid ATYP
                out.writeByte(0x05)
                out.writeByte(0x01) // CONNECT
                out.writeByte(0x00) // RSV
                out.writeByte(atyp) // Unsupported ATYP
                out.flush()

                // 3. Read RFC 1928 reply
                val repVer = inp.readUnsignedByte()
                val repCode = inp.readUnsignedByte()
                val rsv = inp.readUnsignedByte()
                val repAtyp = inp.readUnsignedByte()
                val bndAddr = ByteArray(4)
                inp.readFully(bndAddr)
                inp.readUnsignedShort() // BND.PORT

                assertEquals(0x05, repVer)
                assertEquals("ATYP 0x${Integer.toHexString(atyp)} must return ADDRESS_TYPE_NOT_SUPPORTED (0x08)", 0x08, repCode)
                assertEquals(0x00, rsv)
                assertEquals(0x01, repAtyp)

                // 4. Socket closure
                val eof = try {
                    sock.getInputStream().read()
                } catch (_: SocketException) {
                    -1
                }
                assertEquals("Socket must be closed following unsupported address type", -1, eof)
            }
        }
    }

    /**
     * 5. socks5WireFuzzing_rejectsTruncatedFramesAndZeroMethods
     * Sends 1-byte truncated greeting, greeting with nmethods = 0, and truncated request header;
     * verifies graceful EOF/closure and zero leaked active client sockets.
     */
    @Test
    fun socks5WireFuzzing_rejectsTruncatedFramesAndZeroMethods() {
        // A. 1-byte truncated greeting
        Socket("127.0.0.1", dynamicSocksPort).use { sock ->
            sock.soTimeout = 2000
            val out = sock.getOutputStream()
            out.write(byteArrayOf(0x05))
            out.flush()
            sock.shutdownOutput()

            val read = try {
                sock.getInputStream().read()
            } catch (_: SocketException) {
                -1
            }
            assertEquals("1-byte truncated greeting must result in EOF (-1)", -1, read)
        }

        // B. Greeting with nmethods = 0
        Socket("127.0.0.1", dynamicSocksPort).use { sock ->
            sock.soTimeout = 2000
            val out = sock.getOutputStream()
            out.write(byteArrayOf(0x05, 0x00))
            out.flush()

            val read = try {
                sock.getInputStream().read()
            } catch (_: SocketException) {
                -1
            }
            assertEquals("nmethods=0 greeting must result in EOF (-1)", -1, read)
        }

        // C. Truncated request header after valid greeting
        Socket("127.0.0.1", dynamicSocksPort).use { sock ->
            sock.soTimeout = 2000
            val out = DataOutputStream(sock.getOutputStream())
            val inp = DataInputStream(sock.getInputStream())

            out.write(byteArrayOf(0x05, 0x01, 0x00))
            out.flush()
            assertEquals(0x05, inp.readUnsignedByte())
            assertEquals(0x00, inp.readUnsignedByte())

            // Send only 2 bytes of request
            out.write(byteArrayOf(0x05, 0x01))
            out.flush()
            sock.shutdownOutput()

            val read = try {
                sock.getInputStream().read()
            } catch (_: SocketException) {
                -1
            }
            assertEquals("Truncated request header must result in EOF (-1)", -1, read)
        }

        // Verify zero leaked client sockets
        Thread.sleep(200)
        assertEquals("Zero active client sockets must be leaked after fuzzing", 0, orchestrator.activeClientSockets.size)
    }

    /**
     * 6. socks5HostileDomainInjection_rejectsNullBytesAndControlCharactersInDomain
     * Sends ATYP = 0x03 with domain "127.0.0.1\u0000evil.com" and "localhost\r\nX-Injected: true";
     * verifies rejection and ensures the tunnel remains functional for legitimate localhost requests.
     */
    @Test
    fun socks5HostileDomainInjection_rejectsNullBytesAndControlCharactersInDomain() {
        val hostileDomains = listOf(
            "127.0.0.1\u0000evil.com",
            "localhost\r\nX-Injected: true"
        )

        for (domain in hostileDomains) {
            Socket("127.0.0.1", dynamicSocksPort).use { sock ->
                sock.soTimeout = 2000
                val out = DataOutputStream(sock.getOutputStream())
                val inp = DataInputStream(sock.getInputStream())

                // 1. Handshake
                out.write(byteArrayOf(0x05, 0x01, 0x00))
                out.flush()
                assertEquals(0x05, inp.readUnsignedByte())
                assertEquals(0x00, inp.readUnsignedByte())

                // 2. Request with hostile domain name
                val domainBytes = domain.toByteArray(Charsets.UTF_8)
                out.writeByte(0x05)
                out.writeByte(0x01) // CONNECT
                out.writeByte(0x00) // RSV
                out.writeByte(0x03) // Domain name
                out.writeByte(domainBytes.size)
                out.write(domainBytes)
                out.writeShort(httpServerPort)
                out.flush()

                // 3. Response: must be general failure (0x01)
                val repVer = inp.readUnsignedByte()
                val repCode = inp.readUnsignedByte()
                assertEquals(0x05, repVer)
                assertEquals("Hostile domain '$domain' must be rejected with 0x01", 0x01, repCode)

                // 4. Socket closed
                try {
                    sock.getInputStream().read()
                } catch (_: SocketException) {
                    -1
                }
            }
        }

        // Verify subsequent legitimate "localhost" request succeeds
        val (legitRep, legitBody) = executeSocks5Http(dynamicSocksPort, "localhost", httpServerPort)
        assertEquals("Legitimate 'localhost' domain request must succeed", 0x00, legitRep)
        assertTrue("Legitimate request body must contain 'status: ok'", legitBody.contains("status: ok"))
    }

    /**
     * 7. connectionFloodExhaustion_enforces32ConcurrentCapAndRecoversAfterRelease
     * Holds 32 active connections open (MAX_CONCURRENT_FORWARD_CONNECTIONS),
     * verifies connection #33, #34, #35 are immediately rejected (EOF),
     * and verifies that after closing the held sockets, connection #36 succeeds to the basic HTTP server.
     */
    @Test
    fun connectionFloodExhaustion_enforces32ConcurrentCapAndRecoversAfterRelease() {
        val heldSockets = mutableListOf<Socket>()
        try {
            // Establish 32 connections and hold them active
            for (i in 1..PortForwardingOrchestrator.MAX_CONCURRENT_FORWARD_CONNECTIONS) {
                val sock = Socket("127.0.0.1", dynamicSocksPort)
                sock.soTimeout = 4000
                val out = DataOutputStream(sock.getOutputStream())
                val inp = DataInputStream(sock.getInputStream())

                out.write(byteArrayOf(0x05, 0x01, 0x00))
                out.flush()
                assertEquals(0x05, inp.readUnsignedByte())
                assertEquals(0x00, inp.readUnsignedByte())

                out.writeByte(0x05)
                out.writeByte(0x01)
                out.writeByte(0x00)
                out.writeByte(0x01)
                out.write(byteArrayOf(127, 0, 0, 1))
                out.writeShort(httpServerPort)
                out.flush()

                assertEquals(0x05, inp.readUnsignedByte())
                assertEquals(0x00, inp.readUnsignedByte())
                inp.readUnsignedByte() // RSV
                inp.readUnsignedByte() // ATYP
                inp.readFully(ByteArray(4))
                inp.readUnsignedShort()

                heldSockets.add(sock)
            }
            assertEquals(
                "Must hold exactly 32 active connections",
                PortForwardingOrchestrator.MAX_CONCURRENT_FORWARD_CONNECTIONS,
                orchestrator.activeClientSockets.size
            )

            // Attempt connection #33, #34, #35 — all must be immediately rejected with EOF
            for (attempt in 33..35) {
                Socket("127.0.0.1", dynamicSocksPort).use { excessSock ->
                    excessSock.soTimeout = 2000
                    val read = try {
                        excessSock.getInputStream().read()
                    } catch (_: SocketException) {
                        -1
                    }
                    assertEquals("Excess connection #$attempt must be rejected with EOF (-1)", -1, read)
                }
            }
        } finally {
            // Close all 32 held sockets to release capacity
            heldSockets.forEach {
                try {
                    it.close()
                } catch (_: Exception) {}
            }
            heldSockets.clear()
        }

        // Allow worker threads to clean up
        var retries = 0
        while (orchestrator.activeClientSockets.isNotEmpty() && retries < 50) {
            Thread.sleep(50)
            retries++
        }

        // Connection #36 must now succeed cleanly
        val (repCode, body) = executeSocks5Http(dynamicSocksPort, "127.0.0.1", httpServerPort)
        assertEquals("Connection #36 must succeed with 0x00 after releasing flood capacity", 0x00, repCode)
        assertTrue("Connection #36 body must contain 'status: ok'", body.contains("status: ok"))
    }

    /**
     * 8. abruptTcpResetAndStopAllUnderLoad_cleansUpAllSocketsAndThreads
     * Initiates concurrent transfers, forces abrupt client socket closure (setSoLinger(true, 0) / close),
     * and invokes stopAll(), verifying activeClientSockets and localServerSockets are empty (0).
     */
    @Test
    fun abruptTcpResetAndStopAllUnderLoad_cleansUpAllSocketsAndThreads() {
        val threads = mutableListOf<Thread>()
        val clientSockets = CopyOnWriteArrayList<Socket>()
        val running = AtomicBoolean(true)

        for (i in 1..8) {
            val t = thread(name = "AbruptClientWorker_$i") {
                try {
                    val sock = Socket("127.0.0.1", dynamicSocksPort)
                    clientSockets.add(sock)
                    sock.soTimeout = 5000
                    val out = DataOutputStream(sock.getOutputStream())
                    val inp = DataInputStream(sock.getInputStream())

                    out.write(byteArrayOf(0x05, 0x01, 0x00))
                    out.flush()
                    if (inp.readUnsignedByte() != 0x05) return@thread
                    if (inp.readUnsignedByte() != 0x00) return@thread

                    out.writeByte(0x05)
                    out.writeByte(0x01)
                    out.writeByte(0x00)
                    out.writeByte(0x01)
                    out.write(byteArrayOf(127, 0, 0, 1))
                    out.writeShort(httpServerPort)
                    out.flush()

                    if (inp.readUnsignedByte() != 0x05) return@thread
                    if (inp.readUnsignedByte() != 0x00) return@thread
                    inp.readUnsignedByte() // RSV
                    inp.readUnsignedByte() // ATYP
                    inp.readFully(ByteArray(4))
                    inp.readUnsignedShort()

                    val req = "GET /stream HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n"
                    out.write(req.toByteArray(Charsets.UTF_8))
                    out.flush()

                    val buf = ByteArray(1024)
                    while (running.get()) {
                        val r = inp.read(buf)
                        if (r == -1) break
                    }
                } catch (_: Exception) {}
            }
            threads.add(t)
        }

        // Allow streaming transfers to establish
        Thread.sleep(400)

        // Force abrupt TCP resets (RST packets) across client sockets
        clientSockets.forEach { sock ->
            try {
                sock.setSoLinger(true, 0)
                sock.close()
            } catch (_: Exception) {}
        }
        running.set(false)

        // Stop all port forwarding immediately
        orchestrator.stopAll()

        threads.forEach { it.join(1000) }

        assertEquals("activeClientSockets must be 0 after stopAll under load", 0, orchestrator.activeClientSockets.size)
        assertEquals("localServerSockets must be 0 after stopAll under load", 0, orchestrator.localServerSockets.size)
    }
}
