package com.adamoutler.ssh.ui.components

import kotlinx.coroutines.channels.ReceiveChannel
import java.io.OutputStream

/**
 * High-performance write batcher for terminal input streams.
 *
 * Coalesces rapidly queued byte arrays (such as during soft keyboard paste,
 * clipboard insertion, or automation) into micro-batches, writing them sequentially
 * and invoking [OutputStream.flush] once per drained batch.
 *
 * Operates with sub-millisecond single-keystroke latency by suspending immediately
 * on the first byte array without artificial timers or delays.
 */
object TerminalWriteBatcher {

    /**
     * Default safe cap for micro-batch size (64 KB).
     * Prevents unbounded buffer memory consumption or prolonged socket write blocking.
     */
    const val DEFAULT_MAX_BATCH_BYTES = 64 * 1024

    /**
     * Consumes byte arrays from [channel] and writes them to the [OutputStream] supplied
     * by [getOutputStream], flushing once per drained batch.
     *
     * @param channel The incoming channel of byte arrays.
     * @param getOutputStream Provider function for the destination stream. If null is returned,
     *                        data is safely discarded without throwing.
     * @param maxBatchBytes The maximum batch size in bytes before forcing a flush.
     * @param onError Optional handler for I/O or flush exceptions.
     */
    suspend fun processChannel(
        channel: ReceiveChannel<ByteArray>,
        getOutputStream: () -> OutputStream?,
        maxBatchBytes: Int = DEFAULT_MAX_BATCH_BYTES,
        onError: (Exception) -> Unit = {},
    ) {
        for (firstBytes in channel) {
            try {
                val pty = getOutputStream()
                if (pty != null) {
                    synchronized(pty) {
                        pty.write(firstBytes)
                        var currentBatchBytes = firstBytes.size

                        while (currentBatchBytes < maxBatchBytes) {
                            val nextBytes = channel.tryReceive().getOrNull() ?: break
                            pty.write(nextBytes)
                            currentBatchBytes += nextBytes.size
                        }
                        pty.flush()
                    }
                }
            } catch (ex: Exception) {
                onError(ex)
            }
        }
    }

    /**
     * Convenience overload for direct [OutputStream] instances.
     */
    suspend fun processChannel(
        channel: ReceiveChannel<ByteArray>,
        outputStream: OutputStream,
        maxBatchBytes: Int = DEFAULT_MAX_BATCH_BYTES,
        onError: (Exception) -> Unit = {},
    ) {
        processChannel(
            channel = channel,
            getOutputStream = { outputStream },
            maxBatchBytes = maxBatchBytes,
            onError = onError,
        )
    }
}
