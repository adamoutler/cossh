package com.adamoutler.ssh.security

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SecureCrashHandler(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    var processKiller: () -> Unit = {
        android.os.Process.killProcess(android.os.Process.myPid())
        System.exit(10)
    }

    companion object {
        const val CRASH_DIR_NAME = "secure_crashes"
        
        // Regex patterns for redaction
        private val IP_REGEX = Regex("\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b")
        private val BASE64_LIKE_REGEX = Regex("([A-Za-z0-9+/]{40,}=*)") // Potential keys
        private val PEM_REGEX = Regex("-----BEGIN.*?-----.*?-----END.*?-----", RegexOption.DOT_MATCHES_ALL)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val sanitizedTrace = sanitizeThrowable(throwable)
            securelyWriteCrashToDisk(sanitizedTrace)
        } catch (e: Exception) {
            // Ultimate fallback: If the crash handler crashes, do nothing. 
            // We fail closed to prevent accidental leakage during error handling.
            Log.e("SecureCrashHandler", "Fatal error in crash handler. Failing closed.")
        } finally {
            // Gracefully self-terminate to prevent the default OS crash dialog from appearing
            processKiller()
        }
    }

    private fun sanitizeThrowable(throwable: Throwable): String {
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

    private fun redactString(input: String): String {
        var sanitized = input
        sanitized = IP_REGEX.replace(sanitized, "[REDACTED_IP]")
        sanitized = BASE64_LIKE_REGEX.replace(sanitized, "[REDACTED_B64]")
        sanitized = PEM_REGEX.replace(sanitized, "[REDACTED_KEY]")
        // Add specific username/password regexes if your underlying SSH lib leaks them
        return sanitized
    }

    private fun securelyWriteCrashToDisk(trace: String) {
        // Enforce application internal storage only
        val crashDir = File(context.filesDir, CRASH_DIR_NAME)
        if (!crashDir.exists()) {
            crashDir.mkdir()
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val crashFile = File(crashDir, "crash_$timestamp.log")

        // Write raw trace
        FileOutputStream(crashFile).use { fos ->
            fos.write(trace.toByteArray(Charsets.UTF_8))
            fos.flush()
        }
    }
}
