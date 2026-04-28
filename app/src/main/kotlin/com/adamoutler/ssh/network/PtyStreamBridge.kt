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
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                if (inputStream.available() > 0) {
                    val read = inputStream.read(buffer)
                    if (read == -1) break
                    if (read > 0) {
                        onOutput(buffer.copyOf(read), read)
                    }
                } else {
                    kotlinx.coroutines.delay(50)
                }
            }
        } catch (e: Exception) {
            // Stream closed or coroutine cancelled
            println("PtyStreamBridge: Bridge terminated: ${e.message}")
        }
    }
}
