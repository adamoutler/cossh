package com.adamoutler.ssh.util

/**
 * Robust cross-platform application logger for CoSSH.
 * Dispatches to android.util.Log on Android devices, and falls back to stdout/stderr in unit test JVM environments.
 */
object AppLog {
    private const val DEFAULT_TAG = "CoSSH"

    fun v(tag: String = DEFAULT_TAG, message: String) {
        try {
            android.util.Log.v(tag, message)
        } catch (_: Throwable) {
            println("VERBOSE: [$tag] $message")
        }
    }

    fun d(tag: String = DEFAULT_TAG, message: String) {
        try {
            android.util.Log.d(tag, message)
        } catch (_: Throwable) {
            println("DEBUG: [$tag] $message")
        }
    }

    fun i(tag: String = DEFAULT_TAG, message: String) {
        try {
            android.util.Log.i(tag, message)
        } catch (_: Throwable) {
            println("INFO: [$tag] $message")
        }
    }

    fun w(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        try {
            if (throwable != null) {
                android.util.Log.w(tag, message, throwable)
            } else {
                android.util.Log.w(tag, message)
            }
        } catch (_: Throwable) {
            val suffix = throwable?.let { " - ${it.message}" } ?: ""
            println("WARN: [$tag] $message$suffix")
        }
    }

    fun e(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        try {
            if (throwable != null) {
                android.util.Log.e(tag, message, throwable)
            } else {
                android.util.Log.e(tag, message)
            }
        } catch (_: Throwable) {
            val suffix = throwable?.let { " - ${it.message}" } ?: ""
            System.err.println("ERROR: [$tag] $message$suffix")
        }
    }
}
