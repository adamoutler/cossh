package com.adamoutler.ssh.network

import java.io.InputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay

class PtyStreamBridge(
    private val inputStream: InputStream,
    private val onOutput: suspend (ByteArray, Int) -> Unit
) {
    suspend fun startBridge() {
        val buffer = ByteArray(4096)
        try {
            while (currentCoroutineContext().isActive) {
                if (inputStream.available() > 0) {
                    val read = inputStream.read(buffer)
                    if (read == -1) break
                    if (read > 0) {
                        onOutput(buffer.copyOf(read), read)
                    }
                } else {
                    delay(50) // Prevent tight polling loop
                    // Also attempt a 1-byte read if stream might be closed but available() is 0
                    // But available() usually returns > 0 or throws if closed. Let's just rely on disconnect() throwing or closing.
                }
            }
        } catch (e: Exception) {
            // Stream closed or coroutine cancelled
            println("PtyStreamBridge: Bridge terminated: ${e.message}")
        }
    }
}
