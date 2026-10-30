package com.adamoutler.ssh.network

import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.util.AppLog
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
import org.apache.commons.net.telnet.WindowSizeOptionHandler
import java.io.OutputStream

class TelnetConnectionHandler : ConnectionProtocol {
    companion object {
        private const val TAG = "TelnetConnectionHandler"
    }

    var telnetClient: TelnetClient? = null
        private set

    private var resizeJob: kotlinx.coroutines.Job? = null
    private val resizeScope = CoroutineScope(Dispatchers.IO)

    override suspend fun connect(
        profile: ConnectionProfile,
        keyPair: java.security.KeyPair?,
        onOutput: suspend (ByteArray, Int) -> Unit,
        onConnect: (OutputStream, net.schmizz.sshj.connection.channel.direct.Session.Shell?) -> Unit,
        isAutonomousReconnect: Boolean,
        sessionId: String?,
    ): Unit = withContext(Dispatchers.IO) {
        val tc = TelnetClient()
        telnetClient = tc
        tc.connectTimeout = 10000

        try {
            tc.addOptionHandler(TerminalTypeOptionHandler("xterm-256color", false, false, true, false))
            tc.addOptionHandler(EchoOptionHandler(false, false, false, true))
            tc.addOptionHandler(SuppressGAOptionHandler(false, false, true, true))
            tc.addOptionHandler(
                WindowSizeOptionHandler(
                    TelnetNawsProtocol.DEFAULT_COLS,
                    TelnetNawsProtocol.DEFAULT_ROWS,
                    true,
                    false,
                    true,
                    false,
                ),
            )

            tc.connect(profile.host, profile.port)

            val writeChannel = Channel<ByteArray>(Channel.UNLIMITED)

            val ioJob = CoroutineScope(Dispatchers.IO).launch {
                for (bytes in writeChannel) {
                    try {
                        tc.outputStream.write(bytes)
                        tc.outputStream.flush()
                    } catch (e: Exception) {
                        AppLog.e(TAG, "Error writing to telnet stream: ${e.message}", e)
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
                        AppLog.w(TAG, "Failed to send to writeChannel")
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
                val sanitized = profile.initialDirectory.filter { ch -> ch.code >= 0x20 && ch.code != 0x7F }
                if (sanitized.isNotBlank()) {
                    val escapedDir = sanitized.replace("'", "'\\''")
                    val cdCmd = "cd '$escapedDir'\r\n"
                    autoFlushingStream.write(cdCmd.toByteArray(Charsets.UTF_8))
                    autoFlushingStream.flush()
                }
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
            resizeJob?.cancel()
            try {
                tc.disconnect()
            } catch (e: Exception) {
                AppLog.w(TAG, "Error during telnet disconnect: ${e.message}", e)
            }
        }
    }

    override fun updateWindowSize(cols: Int, rows: Int, widthPx: Int, heightPx: Int) {
        updateWindowSizeWithDebounce(cols, rows, 100L)
    }

    fun updateWindowSizeWithDebounce(cols: Int, rows: Int, debounceMs: Long = 100L) {
        resizeJob?.cancel()
        if (debounceMs <= 0L) {
            sendWindowSizeChange(cols, rows)
        } else {
            resizeJob = resizeScope.launch {
                kotlinx.coroutines.delay(debounceMs)
                sendWindowSizeChange(cols, rows)
            }
        }
    }

    fun sendWindowSizeChange(cols: Int, rows: Int) {
        val tc = telnetClient ?: return
        if (!tc.isConnected) return
        try {
            val payload = TelnetNawsProtocol.buildSubnegotiationPayload(cols, rows)
            tc.sendSubnegotiation(payload)
            AppLog.d(TAG, "Sent Telnet NAWS subnegotiation: cols=$cols, rows=$rows")
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to send Telnet NAWS subnegotiation: ${e.message}", e)
        }
    }

    override fun disconnect() {
        resizeJob?.cancel()
        try {
            telnetClient?.disconnect()
        } catch (e: Exception) {
            AppLog.w(TAG, "Error during manual telnet disconnect: ${e.message}", e)
        }
    }
}
