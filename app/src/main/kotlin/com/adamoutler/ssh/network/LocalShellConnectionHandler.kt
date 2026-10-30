package com.adamoutler.ssh.network

import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.security.KeyPair

class LocalShellConnectionHandler(
    private val shellPath: String = if (File("/system/bin/sh").exists()) "/system/bin/sh" else "/bin/sh",
) : ConnectionProtocol {

    companion object {
        private const val TAG = "LocalShellConnectionHandler"
    }

    internal var process: Process? = null
        private set

    override suspend fun connect(
        profile: ConnectionProfile,
        keyPair: KeyPair?,
        onOutput: suspend (ByteArray, Int) -> Unit,
        onConnect: (OutputStream, net.schmizz.sshj.connection.channel.direct.Session.Shell?) -> Unit,
        isAutonomousReconnect: Boolean,
        sessionId: String?,
    ): Unit = withContext(Dispatchers.IO) {
        val processBuilder = ProcessBuilder(shellPath)
        processBuilder.redirectErrorStream(true)

        val env = processBuilder.environment()
        env["TERM"] = "xterm-256color"
        for ((key, value) in profile.envVars) {
            if (key.equals("LD_PRELOAD", ignoreCase = true) || key.equals("LD_LIBRARY_PATH", ignoreCase = true)) {
                continue
            }
            env[key] = value
        }
        env.remove("LD_PRELOAD")
        env.remove("LD_LIBRARY_PATH")

        if (!profile.initialDirectory.isNullOrEmpty()) {
            val sanitized = profile.initialDirectory.filter { ch -> ch.code >= 0x20 && ch.code != 0x7F }
            if (sanitized.isNotBlank()) {
                val initialDir = File(sanitized)
                if (initialDir.exists() && initialDir.isDirectory) {
                    processBuilder.directory(initialDir)
                }
            }
        }

        AppLog.i(TAG, "Starting local shell process: $shellPath")
        val proc = try {
            processBuilder.start()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to start local shell process: ${e.message}", e)
            throw e
        }
        process = proc

        try {
            onConnect(proc.outputStream, null)
            val bridge = PtyStreamBridge(proc.inputStream, onOutput)
            bridge.startBridge()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(TAG, "Local shell execution terminated: ${e.message}", e)
        } finally {
            cleanupProcess(proc)
        }
    }

    override fun disconnect() {
        AppLog.i(TAG, "Disconnecting local shell process")
        val proc = process
        process = null
        if (proc != null) {
            cleanupProcess(proc)
        }
    }

    override fun updateWindowSize(cols: Int, rows: Int, widthPx: Int, heightPx: Int) {
        // Local subshell window resize is a no-op when running unprivileged subshell
    }

    private fun cleanupProcess(proc: Process) {
        try {
            proc.outputStream.close()
        } catch (_: Exception) {}
        try {
            proc.inputStream.close()
        } catch (_: Exception) {}
        try {
            proc.errorStream.close()
        } catch (_: Exception) {}
        try {
            proc.destroy()
        } catch (_: Exception) {}
        try {
            if (proc.isAlive) {
                proc.destroyForcibly()
            }
        } catch (_: Exception) {}
    }
}
