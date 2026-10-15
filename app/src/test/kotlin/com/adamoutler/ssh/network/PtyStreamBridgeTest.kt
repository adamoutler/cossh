package com.adamoutler.ssh.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PtyStreamBridgeTest {

    @Test
    fun testPtyStreamBridge_forwardsStreamBytesAndZeroFillsOnCompletion() = runBlocking {
        val testData = "Hello Secure SSH Terminal!".toByteArray(Charsets.UTF_8)
        val inputStream = ByteArrayInputStream(testData)
        val collectedBytes = mutableListOf<ByteArray>()
        val collectedLengths = mutableListOf<Int>()

        val customBuffer = ByteArray(4096)
        val bridge = PtyStreamBridge(
            inputStream = inputStream,
            onOutput = { bytes, len ->
                collectedBytes.add(bytes)
                collectedLengths.add(len)
            },
            buffer = customBuffer,
        )

        bridge.startBridge()

        // 1. Verify all bytes were forwarded correctly
        assertEquals(1, collectedBytes.size)
        assertEquals(testData.size, collectedLengths[0])
        assertArrayEquals(testData, collectedBytes[0])

        // 2. Verify internal read buffer is zero-filled upon completion
        assertTrue(
            "Buffer should be completely zeroed after stream completion",
            customBuffer.all { it == 0.toByte() },
        )
    }

    @Test
    fun testPtyStreamBridge_zeroFillsOnCoroutineCancellation() = runBlocking {
        val dataChunk = "First Chunk".toByteArray(Charsets.UTF_8)
        val chunkReceivedLatch = CountDownLatch(1)
        val releaseStreamLatch = CountDownLatch(1)

        val blockingInputStream = object : InputStream() {
            private var sent = false

            override fun read(): Int {
                val buf = ByteArray(1)
                val count = read(buf, 0, 1)
                return if (count == -1) -1 else buf[0].toInt() and 0xFF
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (!sent) {
                    val toCopy = minOf(len, dataChunk.size)
                    System.arraycopy(dataChunk, 0, b, off, toCopy)
                    sent = true
                    return toCopy
                }
                // Block until test cancels the coroutine or releases the stream
                try {
                    releaseStreamLatch.await(5, TimeUnit.SECONDS)
                } catch (_: InterruptedException) {
                    return -1
                }
                return -1
            }

            override fun close() {
                releaseStreamLatch.countDown()
            }
        }

        val collectedBytes = mutableListOf<ByteArray>()
        val customBuffer = ByteArray(4096)
        val bridge = PtyStreamBridge(
            inputStream = blockingInputStream,
            onOutput = { bytes, _ ->
                collectedBytes.add(bytes)
                chunkReceivedLatch.countDown()
            },
            buffer = customBuffer,
        )

        val job = launch(Dispatchers.IO) {
            bridge.startBridge()
        }

        assertTrue("First chunk should be received", chunkReceivedLatch.await(5, TimeUnit.SECONDS))
        assertEquals(1, collectedBytes.size)
        assertArrayEquals(dataChunk, collectedBytes[0])

        // Cancel the coroutine and release blocking stream
        job.cancel()
        blockingInputStream.close()
        job.join()

        // Verify buffer is zeroed out in finally block
        assertTrue(
            "Buffer should be completely zeroed after coroutine cancellation",
            customBuffer.all { it == 0.toByte() },
        )
    }

    @Test
    fun testPtyStreamBridge_zeroFillsOnStreamException() = runBlocking {
        val throwingInputStream = object : InputStream() {
            override fun read(): Int = throw IOException("Simulated IO failure")
            override fun read(b: ByteArray, off: Int, len: Int): Int = throw IOException("Simulated IO failure")
        }

        val customBuffer = ByteArray(4096)
        // Fill buffer initially with dummy non-zero data
        customBuffer.fill(42.toByte())

        val bridge = PtyStreamBridge(
            inputStream = throwingInputStream,
            onOutput = { _, _ -> },
            buffer = customBuffer,
        )

        bridge.startBridge()

        // Verify buffer is zeroed out in finally block despite exception
        assertTrue(
            "Buffer should be completely zeroed after stream exception",
            customBuffer.all { it == 0.toByte() },
        )
    }

    @Test
    fun testPtyStreamBridge_defaultBufferAllocationSanitized() = runBlocking {
        val testData = "Testing default buffer parameter".toByteArray(Charsets.UTF_8)
        val inputStream = ByteArrayInputStream(testData)
        val collectedBytes = mutableListOf<ByteArray>()

        val bridge = PtyStreamBridge(
            inputStream = inputStream,
            onOutput = { bytes, _ -> collectedBytes.add(bytes) },
        )

        bridge.startBridge()

        assertEquals(1, collectedBytes.size)
        assertArrayEquals(testData, collectedBytes[0])
        assertEquals(4096, bridge.buffer.size)
        assertTrue(
            "Default buffer should be completely zeroed after bridge terminates",
            bridge.buffer.all { it == 0.toByte() },
        )
    }
}
