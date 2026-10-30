package com.adamoutler.ssh.security

import android.content.Context
import android.content.Intent
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.crypto.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.crypto.Cipher

/**
 * State machine managing biometric app lock state, navigation intent buffering,
 * and background/foreground lifecycle timeout tracking.
 */
class AppLockManager(
    private val settingsManager: SettingsManager,
    private val securityStorageManager: SecurityStorageManager,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val _isLocked = MutableStateFlow(shouldBeLockedInitially())
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    val pendingResumeIntent: MutableStateFlow<Intent?> = MutableStateFlow(null)

    /**
     * Test-only override flag allowing headless test environments to unlock on mock biometric failures.
     * Strictly false in production to prevent biometric authentication bypass on exception.
     */
    var allowHeadlessTestFallback: Boolean = false

    private var backgroundedTimestampMs: Long = 0L

    private fun shouldBeLockedInitially(): Boolean = settingsManager.isAppLockEnabled && securityStorageManager.isAppLockConfigured()

    fun isAppLockConfigured(): Boolean = securityStorageManager.isAppLockConfigured()

    fun isAppLockEnabled(): Boolean = settingsManager.isAppLockEnabled

    fun enableAppLock(cipher: Cipher) {
        securityStorageManager.setupAppLock(cipher)
        settingsManager.isAppLockEnabled = true
        _isLocked.value = false
        backgroundedTimestampMs = 0L
        try {
            com.adamoutler.ssh.security.audit.SecurityAuditLogManager.getInstanceOrNull()?.let { audit ->
                kotlinx.coroutines.runBlocking {
                    audit.logAppLockEvent(com.adamoutler.ssh.security.audit.AuditEventType.APP_LOCK_CONFIGURED, "Enabled")
                }
            }
        } catch (_: Exception) {}
    }

    fun disableAppLock() {
        settingsManager.isAppLockEnabled = false
        securityStorageManager.clearAppLock()
        _isLocked.value = false
        pendingResumeIntent.value = null
        backgroundedTimestampMs = 0L
        try {
            com.adamoutler.ssh.security.audit.SecurityAuditLogManager.getInstanceOrNull()?.let { audit ->
                kotlinx.coroutines.runBlocking {
                    audit.logAppLockEvent(com.adamoutler.ssh.security.audit.AuditEventType.APP_LOCK_CONFIGURED, "Disabled")
                }
            }
        } catch (_: Exception) {}
    }

    fun unlockWithCipher(cipher: Cipher): Boolean {
        val success = securityStorageManager.verifyAppLockToken(cipher)
        if (success) {
            _isLocked.value = false
            backgroundedTimestampMs = 0L
            try {
                com.adamoutler.ssh.security.audit.SecurityAuditLogManager.getInstanceOrNull()?.let { audit ->
                    kotlinx.coroutines.runBlocking {
                        audit.logAppLockEvent(com.adamoutler.ssh.security.audit.AuditEventType.APP_LOCK_UNLOCKED)
                    }
                }
            } catch (_: Exception) {}
        } else {
            try {
                com.adamoutler.ssh.security.audit.SecurityAuditLogManager.getInstanceOrNull()?.let { audit ->
                    kotlinx.coroutines.runBlocking {
                        audit.logAppLockEvent(com.adamoutler.ssh.security.audit.AuditEventType.APP_LOCK_FAILED)
                    }
                }
            } catch (_: Exception) {}
        }
        return success
    }

    fun lock() {
        if (settingsManager.isAppLockEnabled && securityStorageManager.isAppLockConfigured()) {
            _isLocked.value = true
        }
    }

    fun onAppBackgrounded() {
        if (!settingsManager.isAppLockEnabled || !securityStorageManager.isAppLockConfigured()) {
            return
        }
        backgroundedTimestampMs = clock()
        if (settingsManager.appLockTimeoutSeconds == 0) {
            _isLocked.value = true
        }
    }

    fun onAppForegrounded() {
        if (!settingsManager.isAppLockEnabled || !securityStorageManager.isAppLockConfigured()) {
            return
        }
        if (!_isLocked.value && backgroundedTimestampMs > 0L) {
            val elapsedMs = clock() - backgroundedTimestampMs
            val timeoutMs = settingsManager.appLockTimeoutSeconds * 1000L
            if (elapsedMs >= timeoutMs) {
                _isLocked.value = true
            }
        }
        backgroundedTimestampMs = 0L
    }

    companion object {
        @Volatile
        private var instance: AppLockManager? = null

        fun getInstance(context: Context): AppLockManager = instance ?: synchronized(this) {
            instance ?: AppLockManager(
                settingsManager = SettingsManager(context.applicationContext),
                securityStorageManager = SecurityStorageManager(context.applicationContext),
            ).also { instance = it }
        }

        fun resetInstanceForTesting(mockManager: AppLockManager? = null) {
            instance = mockManager
        }
    }
}
