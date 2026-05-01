package com.adamoutler.ssh.network

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.io.InputStream

class PtyStreamBridge(
    private val inputStream: InputStream,
    private val onOutput: suspend (ByteArray, Int) -> Unit,
) {
    suspend fun startBridge() {
        val buffer = ByteArray(4096)
        try {
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                val read = inputStream.read(buffer)
                if (read == -1) break
                if (read > 0) {
                    onOutput(buffer.copyOf(read), read)
                }
            }
        } catch (e: Exception) {
            // Stream closed or coroutine cancelled
            println("PtyStreamBridge: Bridge terminated: ${e.message}")
        }
    }
}
