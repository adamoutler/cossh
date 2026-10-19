package com.adamoutler.ssh.network

import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.Protocol
import com.adamoutler.ssh.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class TelnetConnectionHandlerCoverageTest {

    @Test
    fun testDisconnectWhenClientIsNull() {
        val handler = TelnetConnectionHandler()
        assertNull(handler.telnetClient)
        // Should not throw any exception
        handler.disconnect()
        assertNull(handler.telnetClient)
    }

    @Test
    fun testTelnetSessionLifecycleOptionsAndStreaming() = runBlocking {
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort

        val serverAccepted = CountDownLatch(1)
        val serverReceivedBytes = mutableListOf<Byte>()
        val serverDone = AtomicBoolean(false)
        var serverClientSocket: Socket? = null

        val serverThread = Thread {
            try {
                serverClientSocket = serverSocket.accept()
                serverAccepted.countDown()
                val inputStream = serverClientSocket!!.getInputStream()
                val outputStream = serverClientSocket!!.getOutputStream()

                // Respond with a banner
                outputStream.write("Welcome to Telnet\r\n".toByteArray(Charsets.UTF_8))
                outputStream.flush()

                val buf = ByteArray(256)
                while (!serverDone.get()) {
                    val read = inputStream.read(buf)
                    if (read == -1) break
                    synchronized(serverReceivedBytes) {
                        for (i in 0 until read) {
                            serverReceivedBytes.add(buf[i])
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore socket closed
            }
        }
        serverThread.start()

        val handler = TelnetConnectionHandler()
        val connectedLatch = CountDownLatch(1)
        val outputReceivedLatch = CountDownLatch(1)
        var capturedOutputStream: OutputStream? = null
        val receivedOutput = StringBuilder()

        val profile = ConnectionProfile(
            id = "test-telnet-id",
            nickname = "TestTelnet",
            host = "127.0.0.1",
            port = port,
            protocol = Protocol.TELNET,
            initialDirectory = "my/custom'dir"
        )

        val testScope = CoroutineScope(Dispatchers.IO)
        val connectJob = testScope.launch {
            try {
                handler.connect(
                    profile = profile,
                    keyPair = null,
                    onOutput = { bytes, len ->
                        val str = String(bytes, 0, len, Charsets.UTF_8)
                        receivedOutput.append(str)
                        if (receivedOutput.contains("Welcome to Telnet")) {
                            outputReceivedLatch.countDown()
                        }
                    },
                    onConnect = { stream, _ ->
                        capturedOutputStream = stream
                        connectedLatch.countDown()
                    }
                )
            } catch (e: Exception) {
                // Normal on cancellation
            }
        }

        assertTrue("Timed out waiting for server accept", serverAccepted.await(5, TimeUnit.SECONDS))
        assertTrue("Timed out waiting for onConnect callback", connectedLatch.await(5, TimeUnit.SECONDS))
        assertNotNull(handler.telnetClient)
        assertNotNull(capturedOutputStream)

        val stream = capturedOutputStream!!

        // Test writing single byte
        stream.write('X'.code)

        // Test writing ByteArray
        stream.write("hello".toByteArray(Charsets.UTF_8))

        // Test writing ByteArray with offset and len
        stream.write("123456".toByteArray(Charsets.UTF_8), 1, 3)

        // Test CR followed by LF (lastWasCr branch)
        stream.write('\r'.code)
        stream.write('\n'.code)

        // Test LF alone (!lastWasCr branch)
        stream.write('\n'.code)

        // Test flush
        stream.flush()

        // Verify output from server received via PtyStreamBridge
        assertTrue("Timed out waiting for server output", outputReceivedLatch.await(5, TimeUnit.SECONDS))
        assertTrue(receivedOutput.toString().contains("Welcome to Telnet"))

        // Allow some time for writeChannel IO coroutine to flush data to server
        delay(200)

        // Test stream close
        stream.close()

        // Disconnect handler
        handler.disconnect()

        // Cancel the connect job
        connectJob.cancel()

        serverDone.set(true)
        serverClientSocket?.close()
        serverSocket.close()
        serverThread.join(2000)
        testScope.cancel()
    }

    @Test
    fun testConnectWithoutInitialDirectoryAndFailedHost() = runBlocking {
        val handler = TelnetConnectionHandler()
        val profile = ConnectionProfile(
            id = "failing-telnet-id",
            nickname = "FailingTelnet",
            host = "127.0.0.1",
            port = 65534,
            protocol = Protocol.TELNET
        )

        var connectCalled = false
        val job = launch(Dispatchers.IO) {
            try {
                handler.connect(
                    profile = profile,
                    keyPair = null,
                    onOutput = { _, _ -> },
                    onConnect = { _, _ -> connectCalled = true }
                )
            } catch (e: Exception) {
                // Expected connection failure
            }
        }

        withTimeoutOrNull(2000) {
            job.join()
        }
        assertTrue(!connectCalled)
    }

    @Test
    fun testConnectionProtocolFactoryCreation() {
        val orchestrator = PortForwardingOrchestrator()
        val factory = ConnectionProtocolFactory(portForwardingOrchestrator = orchestrator)
        val telnetHandler = factory.create(Protocol.TELNET)
        assertTrue(telnetHandler is TelnetConnectionHandler)
        val sshHandler = factory.create(Protocol.SSH)
        assertTrue(sshHandler is SshConnectionHandler)
    }

    @Test
    fun testPtyStreamBridgeStreamingAndClearing() = runBlocking {
        val inputData = "PtyStreamBridge direct test line\n".toByteArray(Charsets.UTF_8)
        val inputStream = ByteArrayInputStream(inputData)
        val receivedOutput = StringBuilder()
        val bridge = PtyStreamBridge(inputStream, { bytes, len ->
            receivedOutput.append(String(bytes, 0, len, Charsets.UTF_8))
        })
        bridge.startBridge()
        assertEquals("PtyStreamBridge direct test line\n", receivedOutput.toString())
    }

    @Test
    fun testAppLogFallbacks() {
        AppLog.v("TestTag", "Verbose message")
        AppLog.d("TestTag", "Debug message")
        AppLog.i("TestTag", "Info message")
        AppLog.w("TestTag", "Warning message without throwable")
        AppLog.w("TestTag", "Warning message with throwable", RuntimeException("Warning cause"))
        AppLog.e("TestTag", "Error message without throwable")
        AppLog.e("TestTag", "Error message with throwable", RuntimeException("Error cause"))
    }
}
