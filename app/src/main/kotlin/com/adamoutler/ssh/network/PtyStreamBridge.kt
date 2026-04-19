package com.adamoutler.ssh.network

import java.io.InputStream

class PtyStreamBridge(
    private val inputStream: InputStream,
    private val onOutput: suspend (ByteArray, Int) -> Unit
) {
    suspend fun startBridge() {
        val buffer = ByteArray(4096)
        var read: Int
        while (inputStream.read(buffer).also { read = it } != -1) {
            if (read > 0) {
                onOutput(buffer.copyOf(read), read)
            }
        }
    }
}
