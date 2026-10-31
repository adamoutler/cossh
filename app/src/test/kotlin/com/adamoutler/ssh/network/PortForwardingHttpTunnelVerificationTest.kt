package com.adamoutler.ssh.network

import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.PortForwardConfig
import com.adamoutler.ssh.data.PortForwardType
import com.adamoutler.ssh.data.Protocol
import com.sun.net.httpserver.HttpServer
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import org.apache.sshd.common.session.Session
import org.apache.sshd.common.util.net.SshdSocketAddress
import org.apache.sshd.server.Environment
import org.apache.sshd.server.ExitCallback
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import org.apache.sshd.server.command.CommandFactory
import org.apache.sshd.server.forward.ForwardingFilter
import org.apache.sshd.server.forward.TcpForwardingFilter
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.server.shell.ShellFactory
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Deterministic integration tests verifying PortForwardingOrchestrator HTTP tunneling:
 * - Local Port Forwarding (-L) with HTTP GET and 32KB POST echo SHA-256 integrity.
 * - Dynamic SOCKS5 Forwarding (-D) with IPv4 (ATYP 0x01) and Domain Name (ATYP 0x03).
 * - Multiplexed interactive SSH shell concurrent with 4 port forwarded HTTP streams.
 * - Remote Port Forwarding (-R) reverse HTTP delivery and clean shutdown on stopAll().
 */
class PortForwardingHttpTunnelVerificationTest {

    private lateinit var orchestrator: PortForwardingOrchestrator
    private lateinit var sshd: SshServer
    private var sshdPort: Int = 0
    private lateinit var client: SSHClient

    private lateinit var mockHttpServer: HttpServer
    private var mockHttpServerPort: Int = 0
    private var hostKeyFile: File? = null

    @Before
    fun setUp() {
        orchestrator = PortForwardingOrchestrator()

        // 1. Set up embedded HTTP Server implementing GET /, GET /status, and POST /echo
        mockHttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                try {
                    val path = exchange.requestURI.path
                    val method = exchange.requestMethod
                    when {
                        method.equals("GET", ignoreCase = true) && (path == "/" || path == "/status") -> {
                            val response = "status: ok".toByteArray(Charsets.UTF_8)
                            exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
                            exchange.sendResponseHeaders(200, response.size.toLong())
                            exchange.responseBody.use { it.write(response) }
                        }
                        method.equals("POST", ignoreCase = true) && path == "/echo" -> {
                            val bodyBytes = exchange.requestBody.readBytes()
                            val sha256Hex = computeSha256Hex(bodyBytes)
                            exchange.responseHeaders.set("X-Payload-Sha256", sha256Hex)
                            exchange.responseHeaders.set("Content-Type", "application/octet-stream")
                            exchange.sendResponseHeaders(200, bodyBytes.size.toLong())
                            exchange.responseBody.use { it.write(bodyBytes) }
                        }
                        else -> {
                            exchange.sendResponseHeaders(404, -1)
                        }
                    }
                } catch (_: Exception) {
                    try {
                        exchange.sendResponseHeaders(500, -1)
                    } catch (_: Exception) {}
                }
            }
            executor = Executors.newCachedThreadPool { runnable ->
                Thread(runnable, "MockHttpServer_Worker").apply { isDaemon = true }
            }
            start()
        }
        mockHttpServerPort = mockHttpServer.address.port

        // 2. Set up embedded Apache MINA SshServer
        hostKeyFile = File.createTempFile("hostkey_port_forward_test", ".ser").apply { deleteOnExit() }
        sshd = SshServer.setUpDefaultServer().apply {
            host = "127.0.0.1"
            port = 0
            keyPairProvider = SimpleGeneratorHostKeyProvider(hostKeyFile!!.toPath())
            passwordAuthenticator = PasswordAuthenticator { username, password, _ ->
                username == "cossh_test" && password == "cossh_secret"
            }
            // Enforce HTTP-only forwarding policy
            forwardingFilter = object : ForwardingFilter {
                override fun canForwardAgent(session: Session, requestType: String): Boolean = false

                override fun canForwardX11(session: Session, requestType: String): Boolean = false

                override fun canListen(address: SshdSocketAddress, session: Session): Boolean =
                    address.hostName in listOf("127.0.0.1", "localhost")

                override fun canConnect(type: TcpForwardingFilter.Type, address: SshdSocketAddress, session: Session): Boolean =
                    address.hostName in listOf("127.0.0.1", "localhost") && address.port == mockHttpServerPort
            }

            // Shell factory for interactive multiplexing test
            shellFactory = ShellFactory { _ ->
                object : Command {
                    private var inStream: InputStream? = null
                    private var outStream: OutputStream? = null
                    private var exitCallback: ExitCallback? = null
                    private var shellWorker: Thread? = null

                    override fun setInputStream(`in`: InputStream) { inStream = `in` }
                    override fun setOutputStream(out: OutputStream) { outStream = out }
                    override fun setErrorStream(err: OutputStream) {}
                    override fun setExitCallback(callback: ExitCallback) { exitCallback = callback }

                    override fun start(channel: ChannelSession, env: Environment) {
                        shellWorker = thread(name = "MockSshShell") {
                            try {
                                val reader = BufferedReader(InputStreamReader(inStream ?: return@thread))
                                val writer = OutputStreamWriter(outStream ?: return@thread)
                                writer.write("COSSH_SHELL_INITIALIZED\r\n")
                                writer.flush()
                                while (!Thread.currentThread().isInterrupted) {
                                    val line = reader.readLine() ?: break
                                    if (line.trim() == "exit") break
                                    writer.write("SHELL_ECHO: $line\r\n")
                                    writer.flush()
                                }
                            } catch (_: Exception) {
                            } finally {
                                exitCallback?.onExit(0)
                            }
                        }
                    }

                    override fun destroy(channel: ChannelSession) {
                        shellWorker?.interrupt()
                    }
                }
            }

            // Command factory for exec channel
            commandFactory = CommandFactory { _, command ->
                object : Command {
                    private var outStream: OutputStream? = null
                    private var exitCallback: ExitCallback? = null

                    override fun setInputStream(`in`: InputStream) {}
                    override fun setOutputStream(out: OutputStream) { outStream = out }
                    override fun setErrorStream(err: OutputStream) {}
                    override fun setExitCallback(callback: ExitCallback) { exitCallback = callback }

                    override fun start(channel: ChannelSession, env: Environment) {
                        thread(name = "MockSshExec") {
                            try {
                                outStream?.write("EXEC_RESPONSE: $command\n".toByteArray(Charsets.UTF_8))
                                outStream?.flush()
                            } finally {
                                exitCallback?.onExit(0)
                            }
                        }
                    }

                    override fun destroy(channel: ChannelSession) {}
                }
            }
            start()
        }
        sshdPort = sshd.port

        // 3. Connect SSHClient
        client = SSHClient().apply {
            addHostKeyVerifier(PromiscuousVerifier())
            connect("127.0.0.1", sshdPort)
            authPassword("cossh_test", "cossh_secret")
        }
    }

    @After
    fun tearDown() {
        orchestrator.stopAll()
        try {
            if (client.isConnected) {
                client.disconnect()
            }
            client.close()
        } catch (_: Exception) {}
        try {
            sshd.stop()
        } catch (_: Exception) {}
        try {
            mockHttpServer.stop(0)
        } catch (_: Exception) {}
        try {
            hostKeyFile?.delete()
        } catch (_: Exception) {}
    }

    @Test
    fun localPortForward_tunnelsHttpGetStatusToBasicHttpServer() {
        val localPort = findFreePort()
        val profile = createProfile(
            listOf(PortForwardConfig(PortForwardType.LOCAL, localPort, "127.0.0.1", mockHttpServerPort)),
        )

        orchestrator.startPortForwards(client, profile)

        val url = URL("http://127.0.0.1:$localPort/status")
        val (code, body) = fetchHttpGetWithRetry(url)

        assertEquals("Expected HTTP 200 OK from forwarded status endpoint", 200, code)
        assertEquals("Expected 'status: ok' response body", "status: ok", body)
    }

    @Test
    fun localPortForward_tunnels32KbHttpPostEchoWithSha256Integrity() {
        val localPort = findFreePort()
        val profile = createProfile(
            listOf(PortForwardConfig(PortForwardType.LOCAL, localPort, "127.0.0.1", mockHttpServerPort)),
        )

        orchestrator.startPortForwards(client, profile)

        // Generate deterministic 32,768-byte ASCII payload
        val payloadSize = 32768
        val payload = ByteArray(payloadSize) { index ->
            ('A'.code + (index % 26)).toByte()
        }
        val expectedSha256 = computeSha256Hex(payload)

        val url = URL("http://127.0.0.1:$localPort/echo")
        val (code, headerSha, responseBody) = postHttpEchoWithRetry(url, payload)

        assertEquals("Expected HTTP 200 OK", 200, code)
        assertEquals("X-Payload-Sha256 header must match local digest", expectedSha256, headerSha)
        assertArrayEquals("Echoed payload must match byte-for-byte", payload, responseBody)
    }

    @Test
    fun dynamicSocks5Forward_tunnelsIpv4AndDomainHttpRequestsToBasicHttpServer() {
        val socksPort = findFreePort()
        val profile = createProfile(
            listOf(PortForwardConfig(PortForwardType.DYNAMIC, socksPort, "", 0)),
        )

        orchestrator.startPortForwards(client, profile)

        // 1. RFC 1928 SOCKS5 with IPv4 (ATYP 0x01) -> GET /status
        val ipv4GetReq = "GET /status HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n".toByteArray(Charsets.UTF_8)
        val ipv4GetResp = executeSocks5TunnelRequest(socksPort, "127.0.0.1", mockHttpServerPort, isDomain = false, ipv4GetReq)
        val ipv4GetRespStr = String(ipv4GetResp, Charsets.UTF_8)
        assertTrue("IPv4 SOCKS5 GET response must contain HTTP/1.1 200 OK", ipv4GetRespStr.contains("HTTP/1.1 200 OK"))
        assertTrue("IPv4 SOCKS5 GET response must contain 'status: ok'", ipv4GetRespStr.contains("status: ok"))

        // 2. RFC 1928 SOCKS5 with IPv4 (ATYP 0x01) -> POST /echo (8KB payload)
        val payload8K = ByteArray(8192) { index -> ('1'.code + (index % 10)).toByte() }
        val sha256Expected8K = computeSha256Hex(payload8K)
        val postIpv4Headers = ("POST /echo HTTP/1.1\r\n" +
            "Host: 127.0.0.1\r\n" +
            "Content-Type: application/octet-stream\r\n" +
            "Content-Length: ${payload8K.size}\r\n" +
            "Connection: close\r\n\r\n").toByteArray(Charsets.UTF_8)
        val ipv4PostResp = executeSocks5TunnelRequest(socksPort, "127.0.0.1", mockHttpServerPort, isDomain = false, postIpv4Headers + payload8K)
        val hEndIpv4 = findHttpHeaderEnd(ipv4PostResp)
        assertTrue("SOCKS5 IPv4 POST response must contain valid HTTP header delimiter", hEndIpv4 > 0)
        val headersIpv4Str = String(ipv4PostResp.copyOfRange(0, hEndIpv4), Charsets.UTF_8)
        val bodyIpv4Bytes = ipv4PostResp.copyOfRange(hEndIpv4, ipv4PostResp.size)
        assertTrue("Headers must contain 200 OK", headersIpv4Str.contains("200 OK"))
        assertEquals("Headers must contain correct SHA-256 header", sha256Expected8K, extractHeader(headersIpv4Str, "X-Payload-Sha256"))
        assertArrayEquals("SOCKS5 IPv4 POST body must match byte-for-byte", payload8K, bodyIpv4Bytes)

        // 3. RFC 1928 SOCKS5 with Domain Name (ATYP 0x03) -> GET /status
        val domainGetReq = "GET /status HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".toByteArray(Charsets.UTF_8)
        val domainGetResp = executeSocks5TunnelRequest(socksPort, "localhost", mockHttpServerPort, isDomain = true, domainGetReq)
        val domainGetRespStr = String(domainGetResp, Charsets.UTF_8)
        assertTrue("Domain SOCKS5 GET response must contain HTTP/1.1 200 OK", domainGetRespStr.contains("HTTP/1.1 200 OK"))
        assertTrue("Domain SOCKS5 GET response must contain 'status: ok'", domainGetRespStr.contains("status: ok"))

        // 4. RFC 1928 SOCKS5 with Domain Name (ATYP 0x03) -> POST /echo (16KB payload)
        val payload16K = ByteArray(16384) { index -> ('a'.code + (index % 26)).toByte() }
        val sha256Expected16K = computeSha256Hex(payload16K)
        val postReqHeaders = ("POST /echo HTTP/1.1\r\n" +
            "Host: localhost\r\n" +
            "Content-Type: application/octet-stream\r\n" +
            "Content-Length: ${payload16K.size}\r\n" +
            "Connection: close\r\n\r\n").toByteArray(Charsets.UTF_8)
        val domainPostResp = executeSocks5TunnelRequest(socksPort, "localhost", mockHttpServerPort, isDomain = true, postReqHeaders + payload16K)

        val headerEnd = findHttpHeaderEnd(domainPostResp)
        assertTrue("SOCKS5 POST response must contain valid HTTP header delimiter", headerEnd > 0)
        val headersStr = String(domainPostResp.copyOfRange(0, headerEnd), Charsets.UTF_8)
        val bodyBytes = domainPostResp.copyOfRange(headerEnd, domainPostResp.size)

        assertTrue("Headers must contain 200 OK", headersStr.contains("200 OK"))
        assertEquals("Headers must contain correct SHA-256 header", sha256Expected16K, extractHeader(headersStr, "X-Payload-Sha256"))
        assertArrayEquals("SOCKS5 domain POST body must match byte-for-byte", payload16K, bodyBytes)
    }

    @Test
    fun multiplexedSessionAndPortForward_concurrentShellAndHttpTunnelsWithoutCrossTalk() {
        val localPort1 = findFreePort()
        val localPort2 = findFreePort()
        val socksPort = findFreePort()

        val profile = createProfile(
            listOf(
                PortForwardConfig(PortForwardType.LOCAL, localPort1, "127.0.0.1", mockHttpServerPort),
                PortForwardConfig(PortForwardType.LOCAL, localPort2, "127.0.0.1", mockHttpServerPort),
                PortForwardConfig(PortForwardType.DYNAMIC, socksPort, "", 0),
            ),
        )

        orchestrator.startPortForwards(client, profile)

        // Open an interactive SSH session and shell channel on the same SSHClient
        val session = client.startSession()
        val shell = session.startShell()
        val shellIn = BufferedReader(InputStreamReader(shell.inputStream))
        val shellOut = OutputStreamWriter(shell.outputStream)

        // Read initial shell greeting
        val greeting = shellIn.readLine()
        assertEquals("COSSH_SHELL_INITIALIZED", greeting)

        val pool = Executors.newFixedThreadPool(5)
        val futures = mutableListOf<Future<Unit>>()

        // Concurrent Task 1: Interactive shell commands
        val shellResponses = mutableListOf<String>()
        futures.add(
            pool.submit<Unit> {
                for (i in 1..5) {
                    val token = "token_$i"
                    shellOut.write("$token\r\n")
                    shellOut.flush()
                    val responseLine = shellIn.readLine()
                    if (responseLine != null) {
                        shellResponses.add(responseLine)
                    }
                    Thread.sleep(20)
                }
            },
        )

        // Concurrent Task 2: HTTP GET /status via Local Port 1
        val get1Results = mutableListOf<String>()
        futures.add(
            pool.submit<Unit> {
                for (i in 1..4) {
                    val url = URL("http://127.0.0.1:$localPort1/status")
                    val (_, body) = fetchHttpGetWithRetry(url, 4000L)
                    get1Results.add(body)
                }
            },
        )

        // Concurrent Task 3: HTTP POST /echo (32KB) via Local Port 2
        val postPayload = ByteArray(32768) { (it % 128).toByte() }
        val expectedPostSha = computeSha256Hex(postPayload)
        val postShas = mutableListOf<String>()
        futures.add(
            pool.submit<Unit> {
                for (i in 1..2) {
                    val url = URL("http://127.0.0.1:$localPort2/echo")
                    val (code, headerSha, body) = postHttpEchoWithRetry(url, postPayload, 4000L)
                    assertEquals(200, code)
                    assertArrayEquals(postPayload, body)
                    if (headerSha != null) {
                        postShas.add(headerSha)
                    }
                }
            },
        )

        // Concurrent Task 4: HTTP GET /status via Dynamic SOCKS5 (Domain)
        val socksGetResults = mutableListOf<String>()
        futures.add(
            pool.submit<Unit> {
                val req = "GET /status HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".toByteArray(Charsets.UTF_8)
                for (i in 1..3) {
                    val rawResp = executeSocks5TunnelRequest(socksPort, "localhost", mockHttpServerPort, true, req)
                    val respStr = String(rawResp, Charsets.UTF_8)
                    socksGetResults.add(respStr)
                }
            },
        )

        // Concurrent Task 5: HTTP POST /echo (8KB) via Dynamic SOCKS5 (IPv4)
        val socksPayload = ByteArray(8192) { (it % 128).toByte() }
        val expectedSocksSha = computeSha256Hex(socksPayload)
        val socksPostShas = mutableListOf<String>()
        futures.add(
            pool.submit<Unit> {
                val reqHeaders = ("POST /echo HTTP/1.1\r\n" +
                    "Host: 127.0.0.1\r\n" +
                    "Content-Length: ${socksPayload.size}\r\n" +
                    "Connection: close\r\n\r\n").toByteArray(Charsets.UTF_8)
                val rawResp = executeSocks5TunnelRequest(socksPort, "127.0.0.1", mockHttpServerPort, false, reqHeaders + socksPayload)
                val hEnd = findHttpHeaderEnd(rawResp)
                val hStr = String(rawResp.copyOfRange(0, hEnd), Charsets.UTF_8)
                val bBytes = rawResp.copyOfRange(hEnd, rawResp.size)
                assertArrayEquals(socksPayload, bBytes)
                val sha = extractHeader(hStr, "X-Payload-Sha256")
                if (sha != null) {
                    socksPostShas.add(sha)
                }
            },
        )

        // Await all concurrent tasks
        futures.forEach { it.get(10, TimeUnit.SECONDS) }
        pool.shutdown()

        // Close shell channel
        try {
            shellOut.write("exit\r\n")
            shellOut.flush()
            shell.close()
            session.close()
        } catch (_: Exception) {}

        // Assert zero cross-talk contamination
        assertEquals(5, shellResponses.size)
        shellResponses.forEachIndexed { idx, line ->
            assertEquals("SHELL_ECHO: token_${idx + 1}", line)
            assertFalse("Shell stream must not contain HTTP fragments", line.contains("HTTP") || line.contains("status"))
        }

        assertEquals(4, get1Results.size)
        get1Results.forEach {
            assertEquals("status: ok", it)
            assertFalse("HTTP GET result must not contain shell output", it.contains("SHELL_ECHO"))
        }

        assertEquals(2, postShas.size)
        postShas.forEach {
            assertEquals(expectedPostSha, it)
        }

        assertEquals(3, socksGetResults.size)
        socksGetResults.forEach {
            assertTrue(it.contains("HTTP/1.1 200 OK"))
            assertTrue(it.contains("status: ok"))
            assertFalse("SOCKS5 stream must not contain shell data", it.contains("SHELL_ECHO"))
        }

        assertEquals(1, socksPostShas.size)
        assertEquals(expectedSocksSha, socksPostShas[0])
    }

    @Test
    fun remotePortForward_bindsLoopbackAndDeliversReverseHttpRequestAndCleansUpOnStopAll() {
        // Start a dedicated local HTTP server that will receive the reverse forwarded traffic
        val localHttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/status") { exchange ->
                val resp = "status: ok".toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.set("Content-Type", "text/plain")
                exchange.sendResponseHeaders(200, resp.size.toLong())
                exchange.responseBody.use { it.write(resp) }
            }
            executor = Executors.newSingleThreadExecutor()
            start()
        }
        val localHttpPort = localHttpServer.address.port
        val remoteBoundPort = findFreePort()

        // Also add a local port forward so localServerSockets is populated and verified
        val dummyLocalPort = findFreePort()

        try {
            val profile = createProfile(
                listOf(
                    PortForwardConfig(PortForwardType.REMOTE, localHttpPort, "127.0.0.1", remoteBoundPort),
                    PortForwardConfig(PortForwardType.LOCAL, dummyLocalPort, "127.0.0.1", mockHttpServerPort),
                ),
            )

            orchestrator.startPortForwards(client, profile)

            // Verify reverse forwarding: Send request to remoteBoundPort on SSH server
            val remoteUrl = URL("http://127.0.0.1:$remoteBoundPort/status")
            val (code, body) = fetchHttpGetWithRetry(remoteUrl, 3000L)
            assertEquals("Reverse forward must deliver HTTP 200", 200, code)
            assertEquals("Reverse forward must return 'status: ok'", "status: ok", body)

            // Verify orchestrator state before stopAll
            assertFalse("localServerSockets should not be empty before stopAll()", orchestrator.localServerSockets.isEmpty())
            val serverSocketsSnapshot = orchestrator.localServerSockets.toList()

            // Connect a dummy client socket to track in activeClientSockets
            val dummyClient = Socket("127.0.0.1", dummyLocalPort)
            orchestrator.activeClientSockets.add(dummyClient)
            val clientSocketsSnapshot = orchestrator.activeClientSockets.toList()

            // Call stopAll()
            orchestrator.stopAll()

            // Verify 100% cleanup of localServerSockets and activeClientSockets
            assertTrue("localServerSockets must be cleared after stopAll()", orchestrator.localServerSockets.isEmpty())
            serverSocketsSnapshot.forEach { serverSock ->
                assertTrue("Every local ServerSocket must be closed by stopAll()", serverSock.isClosed)
            }

            assertTrue("activeClientSockets must be cleared after stopAll()", orchestrator.activeClientSockets.isEmpty())
            clientSocketsSnapshot.forEach { clientSock ->
                assertTrue("Every active client Socket must be closed by stopAll()", clientSock.isClosed)
            }
        } finally {
            localHttpServer.stop(0)
        }
    }

    // Helper functions

    private fun findFreePort(): Int {
        val s = ServerSocket(0)
        val port = s.localPort
        s.close()
        return port
    }

    private fun computeSha256Hex(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }

    private fun extractHeader(headersBlock: String, headerName: String): String? {
        return headersBlock.lines()
            .firstOrNull { it.startsWith("$headerName:", ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()
    }

    private fun createProfile(configs: List<PortForwardConfig>): ConnectionProfile {
        return ConnectionProfile(
            id = "test-tunnel-profile",
            nickname = "Test Tunnel Profile",
            host = "127.0.0.1",
            port = sshdPort,
            username = "cossh_test",
            authType = AuthType.PASSWORD,
            protocol = Protocol.SSH,
            portForwards = configs,
        )
    }

    private fun fetchHttpGetWithRetry(url: URL, maxWaitMs: Long = 3000L): Pair<Int, String> {
        val deadline = System.currentTimeMillis() + maxWaitMs
        var lastEx: Exception? = null
        while (System.currentTimeMillis() < deadline) {
            var conn: HttpURLConnection? = null
            try {
                conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 1000
                conn.readTimeout = 2000
                val code = conn.responseCode
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                return Pair(code, body)
            } catch (e: Exception) {
                lastEx = e
                Thread.sleep(50)
            } finally {
                conn?.disconnect()
            }
        }
        throw IllegalStateException("Failed to GET $url within ${maxWaitMs}ms", lastEx)
    }

    private fun postHttpEchoWithRetry(
        url: URL,
        payload: ByteArray,
        maxWaitMs: Long = 3000L,
    ): Triple<Int, String?, ByteArray> {
        val deadline = System.currentTimeMillis() + maxWaitMs
        var lastEx: Exception? = null
        while (System.currentTimeMillis() < deadline) {
            var conn: HttpURLConnection? = null
            try {
                conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setFixedLengthStreamingMode(payload.size)
                conn.connectTimeout = 1000
                conn.readTimeout = 3000
                conn.outputStream.use { it.write(payload) }

                val code = conn.responseCode
                val headerSha = conn.getHeaderField("X-Payload-Sha256")
                val responseBody = conn.inputStream.use { it.readBytes() }
                return Triple(code, headerSha, responseBody)
            } catch (e: Exception) {
                lastEx = e
                Thread.sleep(50)
            } finally {
                conn?.disconnect()
            }
        }
        throw IllegalStateException("Failed to POST $url within ${maxWaitMs}ms", lastEx)
    }

    private fun executeSocks5TunnelRequest(
        socksPort: Int,
        targetHost: String,
        targetPort: Int,
        isDomain: Boolean,
        httpRequest: ByteArray,
    ): ByteArray {
        Socket("127.0.0.1", socksPort).use { socket ->
            socket.soTimeout = 4000
            val out = DataOutputStream(socket.getOutputStream())
            val input = DataInputStream(socket.getInputStream())

            // SOCKS5 Handshake
            out.writeByte(0x05) // VER
            out.writeByte(0x01) // 1 method
            out.writeByte(0x00) // NO AUTH
            out.flush()

            val ver = input.readUnsignedByte()
            val method = input.readUnsignedByte()
            check(ver == 0x05 && method == 0x00) { "SOCKS5 handshake failed: ver=$ver, method=$method" }

            // SOCKS5 CONNECT
            out.writeByte(0x05) // VER
            out.writeByte(0x01) // CMD: CONNECT
            out.writeByte(0x00) // RSV
            if (isDomain) {
                out.writeByte(0x03) // ATYP: DOMAIN
                val domainBytes = targetHost.toByteArray(Charsets.UTF_8)
                out.writeByte(domainBytes.size)
                out.write(domainBytes)
            } else {
                out.writeByte(0x01) // ATYP: IPv4
                val inet = InetAddress.getByName(targetHost)
                out.write(inet.address)
            }
            out.writeShort(targetPort)
            out.flush()

            // SOCKS5 Reply
            val repVer = input.readUnsignedByte()
            val repStatus = input.readUnsignedByte()
            input.readUnsignedByte() // RSV
            val repAtyp = input.readUnsignedByte()
            val addrLen = when (repAtyp) {
                0x01 -> 4
                0x03 -> input.readUnsignedByte()
                0x04 -> 16
                else -> 4
            }
            val boundAddr = ByteArray(addrLen)
            input.readFully(boundAddr)
            input.readUnsignedShort() // BND.PORT
            check(repVer == 0x05 && repStatus == 0x00) { "SOCKS5 connect rejected: status=$repStatus" }

            // Send HTTP payload
            out.write(httpRequest)
            out.flush()

            // Read response
            val respBuffer = ByteArrayOutputStream()
            val chunk = ByteArray(4096)
            var bytesRead: Int
            try {
                while (input.read(chunk).also { bytesRead = it } != -1) {
                    respBuffer.write(chunk, 0, bytesRead)
                    val currentStr = respBuffer.toString(Charsets.UTF_8.name())
                    if (currentStr.contains("\r\n\r\n")) {
                        val headerEnd = currentStr.indexOf("\r\n\r\n") + 4
                        val headers = currentStr.substring(0, headerEnd)
                        val clMatch = "Content-Length: (\\d+)".toRegex(RegexOption.IGNORE_CASE).find(headers)
                        if (clMatch != null) {
                            val contentLength = clMatch.groupValues[1].toInt()
                            if (respBuffer.size() - headerEnd >= contentLength) {
                                break
                            }
                        }
                    }
                }
            } catch (_: SocketTimeoutException) {}
            return respBuffer.toByteArray()
        }
    }

    private fun findHttpHeaderEnd(data: ByteArray): Int {
        for (i in 0 until data.size - 3) {
            if (data[i] == '\r'.code.toByte() &&
                data[i + 1] == '\n'.code.toByte() &&
                data[i + 2] == '\r'.code.toByte() &&
                data[i + 3] == '\n'.code.toByte()
            ) {
                return i + 4
            }
        }
        return -1
    }
}
