package com.adamoutler.ssh.ui.components

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlin.system.measureTimeMillis

class TerminalWriteBatcherTest {

    private class TrackingOutputStream(
        private val flushDelayMs: Long = 0L,
    ) : OutputStream() {
        private val buffer = ByteArrayOutputStream()
        var flushCount = 0
            private set
        var writeCount = 0
            private set

        override fun write(b: Int) {
            synchronized(this) {
                writeCount++
                buffer.write(b)
            }
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            synchronized(this) {
                writeCount++
                buffer.write(b, off, len)
            }
        }

        override fun flush() {
            synchronized(this) {
                flushCount++
                if (flushDelayMs > 0) {
                    Thread.sleep(flushDelayMs)
                }
            }
        }

        fun toByteArray(): ByteArray = synchronized(this) { buffer.toByteArray() }
    }

    private class FailingOutputStream : OutputStream() {
        var errorCount = 0
        override fun write(b: Int): Unit = throw IOException("Simulated write failure")
        override fun write(b: ByteArray, off: Int, len: Int): Unit = throw IOException("Simulated write failure")
    }

    @Test
    fun testCoalescedMicroBatching_reducesFlushesByMoreThan95Percent() = runBlocking {
        val channel = Channel<ByteArray>(Channel.UNLIMITED)
        val stream = TrackingOutputStream()
        val totalBytes = 1000

        val job = launch(Dispatchers.Default) {
            TerminalWriteBatcher.processChannel(channel, stream)
        }

        val expectedBytes = ByteArray(totalBytes) { (it % 94 + 33).toByte() }
        for (i in 0 until totalBytes) {
            channel.send(byteArrayOf(expectedBytes[i]))
        }

        channel.close()
        job.join()

        // 1. All 1,000 bytes arrive intact in exact order
        assertArrayEquals("All 1,000 bytes must arrive in exact sequential order", expectedBytes, stream.toByteArray())

        // 2. Flush count is reduced by >95% (flushCount <= 50, typically <= 10)
        val flushCount = stream.flushCount
        val reductionPercentage = (1.0 - (flushCount.toDouble() / totalBytes)) * 100.0
        println("Test 1 (Coalesced Micro-Batching): Total bytes=$totalBytes, flushCount=$flushCount, reduction=${String.format("%.2f", reductionPercentage)}%")
        assertTrue(
            "Flush count ($flushCount) must be reduced by >95% (expected <= 50 out of 1000, got $flushCount)",
            flushCount <= 50,
        )
        assertTrue(
            "Flush reduction percentage must exceed 95% (was $reductionPercentage%)",
            reductionPercentage >= 95.0,
        )
    }

    @Test
    fun testCoalescedMicroBatching_preQueuedItemsSingleFlush() = runBlocking {
        val channel = Channel<ByteArray>(Channel.UNLIMITED)
        val stream = TrackingOutputStream()
        val totalBytes = 1000

        val expectedBytes = ByteArray(totalBytes) { (it % 94 + 33).toByte() }
        for (i in 0 until totalBytes) {
            channel.send(byteArrayOf(expectedBytes[i]))
        }
        channel.close()

        TerminalWriteBatcher.processChannel(channel, stream)

        assertArrayEquals(expectedBytes, stream.toByteArray())
        assertEquals("Pre-queued items within 64KB cap should flush in exactly 1 batch", 1, stream.flushCount)
    }

    @Test
    fun testBenchmark_throughputProvesAtLeast10xReductionInFlushesAndDuration() = runBlocking {
        val charCount = 1000
        val testPayload = ByteArray(charCount) { (it % 26 + 65).toByte() }
        val simulatedFlushDelayMs = 1L

        // 1. Unbatched per-byte write + flush
        val unbatchedStream = TrackingOutputStream(flushDelayMs = simulatedFlushDelayMs)
        val unbatchedDuration = measureTimeMillis {
            for (i in 0 until charCount) {
                unbatchedStream.write(byteArrayOf(testPayload[i]))
                unbatchedStream.flush()
            }
        }

        // 2. Batched write + flush via TerminalWriteBatcher
        val batchedStream = TrackingOutputStream(flushDelayMs = simulatedFlushDelayMs)
        val channel = Channel<ByteArray>(Channel.UNLIMITED)
        val batchedDuration = measureTimeMillis {
            val job = launch(Dispatchers.Default) {
                TerminalWriteBatcher.processChannel(channel, batchedStream)
            }
            for (i in 0 until charCount) {
                channel.send(byteArrayOf(testPayload[i]))
            }
            channel.close()
            job.join()
        }

        assertArrayEquals(testPayload, unbatchedStream.toByteArray())
        assertArrayEquals(testPayload, batchedStream.toByteArray())

        val flushReductionRatio = unbatchedStream.flushCount.toDouble() / batchedStream.flushCount.toDouble()
        val durationReductionRatio = unbatchedDuration.toDouble() / batchedDuration.coerceAtLeast(1).toDouble()

        println("Test 2 (Benchmark Throughput):")
        println("  Unbatched: ${unbatchedStream.flushCount} flushes in ${unbatchedDuration}ms")
        println("  Batched:   ${batchedStream.flushCount} flushes in ${batchedDuration}ms")
        println("  Flush Count Reduction: ${String.format("%.2f", flushReductionRatio)}x")
        println("  Duration Reduction:    ${String.format("%.2f", durationReductionRatio)}x")

        assertTrue(
            "Flush count must be reduced by at least 10x (got ${flushReductionRatio}x)",
            flushReductionRatio >= 10.0,
        )
        assertTrue(
            "Write duration must be reduced by at least 10x (got ${durationReductionRatio}x)",
            durationReductionRatio >= 10.0,
        )
    }

    @Test
    fun testSingleKeystroke_flushesImmediatelyWithoutWaiting() = runBlocking {
        val channel = Channel<ByteArray>(Channel.UNLIMITED)
        val stream = TrackingOutputStream()

        val job = launch(Dispatchers.Default) {
            TerminalWriteBatcher.processChannel(channel, stream)
        }

        val singleKey = "x".toByteArray(Charsets.UTF_8)
        val startTime = System.currentTimeMillis()
        channel.send(singleKey)

        while (stream.flushCount == 0 && System.currentTimeMillis() - startTime < 1000) {
            delay(5)
        }
        val elapsed = System.currentTimeMillis() - startTime

        assertEquals("Single keystroke must trigger exactly 1 flush", 1, stream.flushCount)
        assertArrayEquals("Single keystroke must be written intact", singleKey, stream.toByteArray())
        assertTrue("Single keystroke must flush immediately without waiting (took ${elapsed}ms)", elapsed < 200)
        assertTrue("Batcher coroutine should remain active waiting for next inputs", job.isActive)

        channel.close()
        job.join()
    }

    @Test
    fun testMaxBatchCap_forcesFlushWhenExceeded() = runBlocking {
        val channel = Channel<ByteArray>(Channel.UNLIMITED)
        val stream = TrackingOutputStream()
        val chunk = ByteArray(1024) { 0x41 } // 1 KB
        val totalChunks = 70 // 70 KB > 64 KB default cap

        for (i in 0 until totalChunks) {
            channel.send(chunk)
        }
        channel.close()

        TerminalWriteBatcher.processChannel(channel, stream, maxBatchBytes = 32 * 1024)

        assertEquals(70 * 1024, stream.toByteArray().size)
        // With 32KB cap and 70KB data, it should have flushed at least 2 times
        assertTrue("Exceeding batch cap must cause multiple flushes (got ${stream.flushCount})", stream.flushCount >= 2)
    }

    @Test
    fun testNullStream_discardsSafelyWithoutCrashing() = runBlocking {
        val channel = Channel<ByteArray>(Channel.UNLIMITED)
        channel.send(byteArrayOf(1, 2, 3))
        channel.close()

        TerminalWriteBatcher.processChannel(channel, getOutputStream = { null })
    }

    @Test
    fun testOutputStreamError_invokesErrorHandlerWithoutCrashing() = runBlocking {
        val channel = Channel<ByteArray>(Channel.UNLIMITED)
        val failingStream = FailingOutputStream()
        var caughtException: Exception? = null

        channel.send(byteArrayOf(1, 2, 3))
        channel.close()

        TerminalWriteBatcher.processChannel(
            channel = channel,
            outputStream = failingStream,
            onError = { caughtException = it },
        )

        assertTrue("Error handler should have caught IOException", caughtException is IOException)
    }
}
