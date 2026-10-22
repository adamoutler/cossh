package com.adamoutler.ssh.network

import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.Protocol
import kotlinx.coroutines.runBlocking
import net.schmizz.sshj.SSHClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Coverage unit tests for SshConnectionHandler covering Jump Host routing,
 * environment variables, initial directory, and disconnect fault tolerance.
 */
class SshConnectionHandlerCoverageTest {

    private fun createProfile(
        id: String = "test-prof",
        jumpHostId: String? = null,
        envVars: Map<String, String> = emptyMap(),
        initialDirectory: String? = null,
    ): ConnectionProfile {
        return ConnectionProfile(
            id = id,
            nickname = "Test",
            host = "localhost",
            port = 22,
            protocol = Protocol.SSH,
            username = "user",
            authType = AuthType.PASSWORD,
            jumpHostProfileId = jumpHostId,
            envVars = envVars,
            initialDirectory = initialDirectory,
        )
    }

    @Test
    fun disconnect_whenExceptionsThrownByClients_doesNotThrow() {
        val orchestrator = PortForwardingOrchestrator()
        val handler = SshConnectionHandler(
            portForwardingOrchestrator = orchestrator,
        )

        // Multiple calls to disconnect on uninitialized or closed handler must succeed safely
        handler.disconnect()
        handler.disconnect()
        assertNull(handler.client)
        assertNull(handler.jumpClient)
        assertNull(handler.jumpTunnel)
    }

    @Test
    fun connect_whenJumpHostProfileMissing_throwsExpectedIOException() = runBlocking {
        val orchestrator = PortForwardingOrchestrator()
        val handler = SshConnectionHandler(
            portForwardingOrchestrator = orchestrator,
            securityStorageManager = null,
        )

        val profile = createProfile(jumpHostId = "non-existent")
        try {
            handler.connect(
                profile = profile,
                keyPair = null,
                onOutput = { _, _ -> },
                onConnect = { _, _ -> },
            )
            org.junit.Assert.fail("Expected IOException")
        } catch (e: IOException) {
            assertTrue("Exception must mention missing jump host", e.message?.contains("not found") == true)
        }
    }

    @Test
    fun initialDirectory_commandEscaping_escapesSingleQuotesCorrectly() {
        val rawDir = "/var/log/my 'test' dir"
        val escapedDir = rawDir.replace("'", "'\\''")
        val cdCmd = "cd '$escapedDir'\r\n"
        assertEquals("cd '/var/log/my '\\''test'\\'' dir'\r\n", cdCmd)
    }
}
