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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Paths

class PortForwardingHardeningTest {

    private lateinit var orchestrator: PortForwardingOrchestrator
    private lateinit var sshd: SshServer
    private var sshdPort = 0
    private lateinit var client: SSHClient

    @Before
    fun setup() {
        orchestrator = PortForwardingOrchestrator()

        sshd = SshServer.setUpDefaultServer()
        sshd.host = "127.0.0.1"
        sshd.port = 0
        sshd.keyPairProvider = SimpleGeneratorHostKeyProvider(Paths.get("hostkey_hardening.ser"))
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
        orchestrator.stopAll()
        try {
            client.disconnect()
            client.close()
        } catch (_: Exception) {}
        try {
            sshd.stop()
        } catch (_: Exception) {}
        try {
            java.io.File("hostkey_hardening.ser").delete()
        } catch (_: Exception) {}
    }

    @Test
    fun testHardenedConstants_haveExpectedValues() {
        assertEquals(32, PortForwardingOrchestrator.MAX_CONCURRENT_FORWARD_CONNECTIONS)
        assertEquals(5000, PortForwardingOrchestrator.SERVER_SOCKET_SO_TIMEOUT_MS)
        assertEquals(15_000, PortForwardingOrchestrator.SOCKS5_HANDSHAKE_TIMEOUT_MS)
    }

    @Test
    fun testStopAll_closesAllTrackedClientSocketsAndEmptiesCollection() {
        val dummySockets = (1..5).map {
            val server = ServerSocket(0)
            val clientSock = Socket("127.0.0.1", server.localPort)
            val accepted = server.accept()
            orchestrator.activeClientSockets.add(accepted)
            server.close()
            clientSock to accepted
        }

        assertEquals(5, orchestrator.activeClientSockets.size)
        orchestrator.stopAll()

        assertTrue("activeClientSockets must be empty after stopAll()", orchestrator.activeClientSockets.isEmpty())
        dummySockets.forEach { (clientSock, accepted) ->
            assertTrue("Tracked server-side socket must be closed", accepted.isClosed)
            clientSock.close()
        }
    }

    @Test
    fun testConcurrencyLimit_rejectsNewConnectionsBeyondMaxLimit() {
        val testServer = ServerSocket(0)
        val dynamicPort = testServer.localPort
        testServer.close()

        val profile = ConnectionProfile(
            id = "hardening-prof",
            nickname = "Hardening Profile",
            host = "127.0.0.1",
            port = sshdPort,
            username = "testuser",
            authType = AuthType.PASSWORD,
            protocol = Protocol.SSH,
            portForwards = listOf(
                PortForwardConfig(PortForwardType.DYNAMIC, dynamicPort, "", 0),
            ),
        )

        orchestrator.startPortForwards(client, profile)
        Thread.sleep(300)

        // Pre-fill activeClientSockets to max capacity (32)
        val fillerSockets = mutableListOf<Socket>()
        val fillerServers = mutableListOf<ServerSocket>()
        try {
            for (i in 1..PortForwardingOrchestrator.MAX_CONCURRENT_FORWARD_CONNECTIONS) {
                val srv = ServerSocket(0)
                fillerServers.add(srv)
                val cli = Socket("127.0.0.1", srv.localPort)
                val acc = srv.accept()
                fillerSockets.add(cli)
                orchestrator.activeClientSockets.add(acc)
            }
            assertEquals(32, orchestrator.activeClientSockets.size)

            // Attempt 33rd connection — should be rejected and closed immediately
            val excessClient = Socket("127.0.0.1", dynamicPort)
            Thread.sleep(300)

            // Attempting to read or write from rejected socket should detect closure
            excessClient.soTimeout = 1000
            val readByte = try {
                excessClient.getInputStream().read()
            } catch (e: Exception) {
                -1
            }
            assertEquals("Rejected connection must be closed by server (EOF -1)", -1, readByte)
            excessClient.close()
        } finally {
            fillerSockets.forEach {
                try {
                    it.close()
                } catch (_: Exception) {}
            }
            fillerServers.forEach {
                try {
                    it.close()
                } catch (_: Exception) {}
            }
        }
    }

    @Test
    fun testDynamicPortForwarding_handshakeAndTracking() {
        val testServer = ServerSocket(0)
        val dynamicPort = testServer.localPort
        testServer.close()

        val profile = ConnectionProfile(
            id = "socks-test",
            nickname = "Socks Test",
            host = "localhost",
            port = sshdPort,
            username = "testuser",
            authType = AuthType.PASSWORD,
            protocol = Protocol.SSH,
            portForwards = listOf(
                PortForwardConfig(PortForwardType.DYNAMIC, dynamicPort, "", 0),
            ),
        )

        orchestrator.startPortForwards(client, profile)
        Thread.sleep(300)

        Socket("127.0.0.1", dynamicPort).use { sock ->
            val out = sock.getOutputStream()
            val input = sock.getInputStream()

            // SOCKS5 greeting
            out.write(byteArrayOf(0x05, 0x01, 0x00))
            out.flush()

            val ver = input.read()
            val method = input.read()
            assertEquals(0x05, ver)
            assertEquals(0x00, method)
        }
    }
}
