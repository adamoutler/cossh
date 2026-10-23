package com.adamoutler.ssh.network

import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.Protocol
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class TelnetResizeDebounceTest {

    @Test
    fun testUpdateWindowSizeDebounceCoalescesRapidCalls() = runBlocking {
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort

        val serverAccepted = CountDownLatch(1)
        val receivedSubnegs = CopyOnWriteArrayList<ByteArray>()
        val serverDone = AtomicBoolean(false)
        var clientSocket: Socket? = null

        val serverThread = Thread {
            try {
                clientSocket = serverSocket.accept()
                serverAccepted.countDown()
                val input = clientSocket!!.getInputStream()
                val output = clientSocket!!.getOutputStream()

                // Offer DO NAWS to client: IAC DO NAWS (0xFF 0xFD 0x1F)
                output.write(byteArrayOf(255.toByte(), 253.toByte(), 31.toByte()))
                output.flush()

                val buf = ByteArray(1024)
                while (!serverDone.get()) {
                    val read = input.read(buf)
                    if (read == -1) break
                    // Scan for IAC SB NAWS ... IAC SE
                    val chunk = buf.copyOf(read)
                    for (i in 0 until read - 2) {
                        if (chunk[i] == 255.toByte() && chunk[i + 1] == 250.toByte() && chunk[i + 2] == 31.toByte()) {
                            receivedSubnegs.add(chunk.copyOfRange(i, read))
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        serverThread.start()

        val handler = TelnetConnectionHandler()
        val connectedLatch = CountDownLatch(1)

        val profile = ConnectionProfile(
            id = "test-telnet-naws",
            nickname = "Telnet NAWS",
            host = "127.0.0.1",
            port = port,
            protocol = Protocol.TELNET,
        )

        val testScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
        val connectJob = testScope.launch {
            try {
                handler.connect(
                    profile = profile,
                    onOutput = { _, _ -> },
                    onConnect = { _, _ -> connectedLatch.countDown() },
                )
            } catch (_: Exception) {
            }
        }

        assertTrue("Server should accept connection", serverAccepted.await(5, TimeUnit.SECONDS))
        assertTrue("Client onConnect should fire", connectedLatch.await(5, TimeUnit.SECONDS))

        // Initial subnegotiation from WindowSizeOptionHandler may arrive
        delay(150)
        val initialCount = receivedSubnegs.size

        // Fire 5 rapid window size changes with 10ms intervals (shorter than 100ms debounce)
        for (i in 1..5) {
            handler.updateWindowSize(cols = 80 + i, rows = 24 + i, widthPx = 800, heightPx = 600)
            delay(10)
        }

        // Wait for debounce window (100ms) plus processing time
        delay(250)

        // Only ONE additional subnegotiation frame should be emitted (coalesced!)
        val afterDebounceCount = receivedSubnegs.size
        assertEquals(
            "Debounced rapid resizes should coalesce into a single subnegotiation dispatch",
            initialCount + 1,
            afterDebounceCount,
        )

        // Immediate send without debounce
        handler.updateWindowSizeWithDebounce(cols = 120, rows = 40, debounceMs = 0L)
        delay(50)
        assertEquals("Immediate send should dispatch instantly", afterDebounceCount + 1, receivedSubnegs.size)

        // Clean up
        serverDone.set(true)
        handler.disconnect()
        connectJob.cancel()
        clientSocket?.close()
        serverSocket.close()
        serverThread.join(2000)
    }
}
