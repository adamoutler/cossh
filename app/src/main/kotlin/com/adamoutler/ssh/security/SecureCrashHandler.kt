package com.adamoutler.ssh.security

import android.content.Context
import com.adamoutler.ssh.util.AppLog
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SecureCrashHandler(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

    var processKiller: () -> Unit = {
        android.os.Process.killProcess(android.os.Process.myPid())
        // System.exit(10) // Commented out to prevent Gradle Test Executor from crashing
    }

    companion object {
        private const val TAG = "SecureCrashHandler"
        const val CRASH_DIR_NAME = "secure_crashes"
        const val MAX_CRASH_LOGS = 5

        // Regex patterns for redaction
        private val IP_REGEX = Regex("\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b")
        private val BASE64_LIKE_REGEX = Regex("([A-Za-z0-9+/]{40,}=*)") // Potential keys
        private val PEM_REGEX = Regex("-----BEGIN.*?-----.*?-----END.*?-----", RegexOption.DOT_MATCHES_ALL)
        private val BEARER_TOKEN_REGEX = Regex("(?i)\\b(bearer|oauth)\\s+([a-zA-Z0-9_\\-\\.~+/]+=*)")
        private val TOKEN_ASSIGNMENT_REGEX = Regex("(?i)\\b(access_token|refresh_token)(\\s*[:=]\\s*)([^\\s,;\"'\\}\\]]+)")
        private val PASSWORD_ASSIGNMENT_REGEX = Regex("(?i)\\b(password|passwd|pwd)(\\s*[:=]\\s*)([^\\s,;\"'\\}\\]]+)")
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val sanitizedTrace = sanitizeThrowable(throwable)
            securelyWriteCrashToDisk(sanitizedTrace)
        } catch (e: Exception) {
            // Ultimate fallback: If the crash handler crashes, do nothing.
            // We fail closed to prevent accidental leakage during error handling.
            AppLog.e(TAG, "Fatal error in crash handler. Failing closed.", e)
        } finally {
            // Gracefully self-terminate to prevent the default OS crash dialog from appearing
            processKiller()
        }
    }

    fun sanitizeThrowable(throwable: Throwable): String {
        val rawMessage = throwable.message ?: "No message"

        // 1. Redact highly sensitive exception types completely
        val safeMessage = if (isSensitiveException(throwable)) {
            "[REDACTED_EXCEPTION_MESSAGE]"
        } else {
            redactString(rawMessage)
        }

        // 2. Build the sanitized trace
        val builder = java.lang.StringBuilder()
        builder.append("Exception: ${throwable.javaClass.name}\n")
        builder.append("Message: $safeMessage\n")
        builder.append("Stacktrace:\n")

        // 3. Keep stack trace lines, but verify they don't contain local var dumps
        throwable.stackTrace.forEach { element ->
            builder.append("\tat ${element.className}.${element.methodName}(${element.fileName}:${element.lineNumber})\n")
        }

        // 4. Handle causes recursively
        var cause = throwable.cause
        while (cause != null) {
            builder.append("\nCaused by: ${cause.javaClass.name}\n")
            val safeCauseMsg = if (isSensitiveException(cause)) "[REDACTED]" else redactString(cause.message ?: "")
            builder.append("Message: $safeCauseMsg\n")
            cause.stackTrace.forEach { element ->
                builder.append("\tat ${element.className}.${element.methodName}(${element.fileName}:${element.lineNumber})\n")
            }
            cause = cause.cause
        }

        return builder.toString()
    }

    private fun isSensitiveException(t: Throwable): Boolean {
        val name = t.javaClass.name.lowercase(Locale.ROOT)
        return name.contains("crypto") ||
            name.contains("security") ||
            name.contains("auth") ||
            name.contains("ssh") ||
            t is java.security.GeneralSecurityException
    }

    fun redactString(input: String): String {
        var sanitized = input
        sanitized = IP_REGEX.replace(sanitized, "[REDACTED_IP]")
        sanitized = BASE64_LIKE_REGEX.replace(sanitized, "[REDACTED_B64]")
        sanitized = PEM_REGEX.replace(sanitized, "[REDACTED_KEY]")
        sanitized = BEARER_TOKEN_REGEX.replace(sanitized, "$1 [REDACTED_TOKEN]")
        sanitized = TOKEN_ASSIGNMENT_REGEX.replace(sanitized, "$1$2[REDACTED_TOKEN]")
        sanitized = PASSWORD_ASSIGNMENT_REGEX.replace(sanitized, "$1$2[REDACTED_PASSWORD]")
        return sanitized
    }

    private fun securelyWriteCrashToDisk(trace: String) {
        // Enforce application internal storage only
        val crashDir = File(context.filesDir, CRASH_DIR_NAME)
        if (!crashDir.exists() && !crashDir.mkdirs()) {
            AppLog.e(TAG, "Failed to create crash directory")
            return
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val crashFile = File(crashDir, "crash_$timestamp.log")

        // Write raw trace
        FileOutputStream(crashFile).use { fos ->
            fos.write(trace.toByteArray(Charsets.UTF_8))
            fos.flush()
        }

        pruneOldCrashLogs(crashDir)
    }

    fun pruneOldCrashLogs(crashDir: File) {
        val files = crashDir.listFiles { file -> file.isFile } ?: return
        if (files.size > MAX_CRASH_LOGS) {
            val sortedFiles = files.sortedBy { it.lastModified() }
            val excessCount = files.size - MAX_CRASH_LOGS
            for (i in 0 until excessCount) {
                try {
                    sortedFiles[i].delete()
                } catch (e: Exception) {
                    AppLog.w(TAG, "Failed to prune old crash log: ${sortedFiles[i].name}")
                }
            }
        }
    }
}
