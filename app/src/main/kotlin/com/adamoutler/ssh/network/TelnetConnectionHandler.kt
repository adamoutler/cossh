package com.adamoutler.ssh.network

import com.adamoutler.ssh.data.ConnectionProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.net.telnet.EchoOptionHandler
import org.apache.commons.net.telnet.SuppressGAOptionHandler
import org.apache.commons.net.telnet.TelnetClient
import org.apache.commons.net.telnet.TerminalTypeOptionHandler
import java.io.OutputStream

class TelnetConnectionHandler : ConnectionProtocol {
    var telnetClient: TelnetClient? = null
        private set

    override suspend fun connect(
        profile: ConnectionProfile,
        keyPair: java.security.KeyPair?,
        onOutput: suspend (ByteArray, Int) -> Unit,
        onConnect: (OutputStream, net.schmizz.sshj.connection.channel.direct.Session.Shell?) -> Unit,
    ): Unit = withContext(Dispatchers.IO) {
        val tc = TelnetClient()
        telnetClient = tc
        tc.connectTimeout = 10000

        try {
            tc.addOptionHandler(TerminalTypeOptionHandler("xterm-256color", false, false, true, false))
            tc.addOptionHandler(EchoOptionHandler(false, false, false, true))
            tc.addOptionHandler(SuppressGAOptionHandler(false, false, true, true))

            tc.connect(profile.host, profile.port)

            val writeChannel = Channel<ByteArray>(Channel.UNLIMITED)

            val ioJob = CoroutineScope(Dispatchers.IO).launch {
                for (bytes in writeChannel) {
                    try {
                        tc.outputStream.write(bytes)
                        tc.outputStream.flush()
                    } catch (e: Exception) {
                        android.util.Log.e("TelnetConnectionHandler", "Error writing to telnet stream", e)
                        break
                    }
                }
            }

            val autoFlushingStream = object : OutputStream() {
                var lastWasCr = false
                private fun translateAndSend(b: Int) {
                    val byteVal = b.toByte()
                    val result = if (byteVal == '\r'.code.toByte()) {
                        lastWasCr = true
                        writeChannel.trySend(byteArrayOf('\r'.code.toByte(), '\n'.code.toByte()))
                    } else if (byteVal == '\n'.code.toByte()) {
                        val res = if (!lastWasCr) {
                            writeChannel.trySend(byteArrayOf('\r'.code.toByte(), '\n'.code.toByte()))
                        } else {
                            null
                        }
                        lastWasCr = false
                        res
                    } else {
                        lastWasCr = false
                        writeChannel.trySend(byteArrayOf(byteVal))
                    }
                    if (result?.isFailure == true) {
                        android.util.Log.e("TelnetConnectionHandler", "Failed to send to writeChannel")
                    }
                }
                override fun write(b: Int) {
                    translateAndSend(b)
                }
                override fun write(b: ByteArray) {
                    write(b, 0, b.size)
                }
                override fun write(b: ByteArray, off: Int, len: Int) {
                    for (i in off until off + len) {
                        translateAndSend(b[i].toInt())
                    }
                }
                override fun flush() {
                    // No-op: OutputStream does not require explicit flushing
                }
                override fun close() {
                    writeChannel.close()
                    ioJob.cancel()
                    try {
                        tc.outputStream.close()
                    } catch (e: Exception) {}
                }
            }

            if (!profile.initialDirectory.isNullOrEmpty()) {
                val escapedDir = profile.initialDirectory.replace("'", "'\\''")
                val cdCmd = "cd '$escapedDir'\r\n"
                autoFlushingStream.write(cdCmd.toByteArray(Charsets.UTF_8))
                autoFlushingStream.flush()
            }

            onConnect(autoFlushingStream, null)

            val bridgeJob = CoroutineScope(Dispatchers.IO).launch {
                val bridge = PtyStreamBridge(tc.inputStream, onOutput)
                bridge.startBridge()
            }

            try {
                awaitCancellation()
            } finally {
                bridgeJob.cancel()
            }
        } finally {
            try {
                tc.disconnect()
            } catch (e: Exception) {
                android.util.Log.e("TelnetConnectionHandler", "Error during telnet disconnect", e)
            }
        }
    }

    override fun disconnect() {
        try {
            telnetClient?.disconnect()
        } catch (e: Exception) {
            android.util.Log.e("TelnetConnectionHandler", "Error during manual telnet disconnect", e)
        }
    }
}
