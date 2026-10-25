package com.adamoutler.ssh.network

import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.Protocol
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LocalShellConnectionHandlerTest {

    @Test
    fun testLocalShellExecutionAndOutput() = runBlocking {
        val handler = LocalShellConnectionHandler()
        val profile = ConnectionProfile.createLocalShellProfile()

        val outputBuffer = StringBuilder()

        val connectJob = launch {
            handler.connect(
                profile = profile,
                onOutput = { bytes, len ->
                    val text = String(bytes, 0, len, Charsets.UTF_8)
                    outputBuffer.append(text)
                },
                onConnect = { outStream, _ ->
                    // Send command to print marker and exit
                    val cmd = "echo 'COSSH_LOCAL_SHELL_OK'\nexit\n"
                    outStream.write(cmd.toByteArray(Charsets.UTF_8))
                    outStream.flush()
                },
            )
        }

        withTimeout(5000L) {
            while (!outputBuffer.contains("COSSH_LOCAL_SHELL_OK")) {
                delay(50)
            }
        }

        connectJob.join()
        assertTrue("Output should contain marker", outputBuffer.contains("COSSH_LOCAL_SHELL_OK"))
    }

    @Test
    fun testLocalShellWithInitialDirectoryAndEnvVars() = runBlocking {
        val tempDir = File(System.getProperty("java.io.tmpdir") ?: "/tmp")
        val handler = LocalShellConnectionHandler()
        val profile = ConnectionProfile(
            id = "test-local",
            nickname = "Local Test",
            host = "localhost",
            protocol = Protocol.LOCAL,
            initialDirectory = tempDir.absolutePath,
            envVars = mapOf("COSSH_CUSTOM_VAR" to "FORTRESS_ACTIVE"),
        )

        val outputBuffer = StringBuilder()

        val connectJob = launch {
            handler.connect(
                profile = profile,
                onOutput = { bytes, len ->
                    val text = String(bytes, 0, len, Charsets.UTF_8)
                    outputBuffer.append(text)
                },
                onConnect = { outStream, _ ->
                    val cmd = "echo VAR=\$COSSH_CUSTOM_VAR\npwd\nexit\n"
                    outStream.write(cmd.toByteArray(Charsets.UTF_8))
                    outStream.flush()
                },
            )
        }

        withTimeout(5000L) {
            while (!outputBuffer.contains("VAR=FORTRESS_ACTIVE")) {
                delay(50)
            }
        }

        connectJob.join()
        assertTrue("Output should contain custom env var", outputBuffer.contains("VAR=FORTRESS_ACTIVE"))
    }

    @Test
    fun testDisconnectTerminatesProcess() = runBlocking {
        val handler = LocalShellConnectionHandler()
        val profile = ConnectionProfile.createLocalShellProfile()

        val connectJob = launch {
            handler.connect(
                profile = profile,
                onOutput = { _, _ -> },
                onConnect = { _, _ -> },
            )
        }

        // Wait until process is spawned
        withTimeout(3000L) {
            while (handler.process == null) {
                delay(20)
            }
        }

        val proc = handler.process
        assertNotNull(proc)
        assertTrue(proc!!.isAlive)

        // Disconnect
        handler.disconnect()
        connectJob.join()

        // Wait briefly for process cleanup
        delay(100)
        assertFalse(proc.isAlive)
    }

    @Test
    fun testUpdateWindowSizeNoOp() {
        val handler = LocalShellConnectionHandler()
        // Window size update should safely execute without exceptions
        handler.updateWindowSize(cols = 120, rows = 40, widthPx = 1080, heightPx = 1920)
    }

    @Test
    fun testConnectionProtocolFactoryCreatesLocalHandler() {
        val orchestrator = PortForwardingOrchestrator()
        val factory = ConnectionProtocolFactory(portForwardingOrchestrator = orchestrator)
        val handler = factory.create(Protocol.LOCAL)
        assertTrue(handler is LocalShellConnectionHandler)
    }

    @Test
    fun testConnectionProfileFactoryFunctions() {
        val shellProfile = ConnectionProfile.createLocalShellProfile()
        assertEquals(Protocol.LOCAL, shellProfile.protocol)
        assertEquals("Local Shell", shellProfile.nickname)
        assertEquals("localhost", shellProfile.host)
        assertEquals(0, shellProfile.port)

        val vmProfile = ConnectionProfile.createLocalVmProfile(port = 2222)
        assertEquals(Protocol.SSH, vmProfile.protocol)
        assertEquals("Micro-VM (AVF)", vmProfile.nickname)
        assertEquals("127.0.0.1", vmProfile.host)
        assertEquals(2222, vmProfile.port)

        val customPortVm = ConnectionProfile.createLocalVmProfile(port = 22222)
        assertEquals(22222, customPortVm.port)
    }

    @Test
    fun testInvalidShellBinaryHandlesErrorCleanly() = runBlocking {
        val handler = LocalShellConnectionHandler(shellPath = "/nonexistent/path/to/dummy_sh")
        val profile = ConnectionProfile.createLocalShellProfile()

        var connected = false
        var exceptionThrown = false
        try {
            handler.connect(
                profile = profile,
                onOutput = { _, _ -> },
                onConnect = { _, _ -> connected = true },
            )
        } catch (_: java.io.IOException) {
            exceptionThrown = true
        }

        assertFalse("onConnect should not execute", connected)
        assertTrue("IOException should be thrown", exceptionThrown)
    }
}
