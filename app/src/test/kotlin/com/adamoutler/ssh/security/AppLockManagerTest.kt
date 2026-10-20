package com.adamoutler.ssh.security

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.crypto.PasswordCipher
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.crypto.SettingsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppLockManagerTest {

    private lateinit var context: Context
    private lateinit var settingsManager: SettingsManager
    private lateinit var securityStorageManager: SecurityStorageManager
    private var simulatedTimeMs: Long = 100000L

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val settingsPrefs = context.getSharedPreferences("cossh_settings", Context.MODE_PRIVATE)
        settingsPrefs.edit().clear().commit()
        val storagePrefs = context.getSharedPreferences("secret_ssh_profiles", Context.MODE_PRIVATE)
        storagePrefs.edit().clear().commit()

        settingsManager = SettingsManager(context)
        securityStorageManager = SecurityStorageManager(context, storagePrefs)
        simulatedTimeMs = 100000L
    }

    private fun createManager(): AppLockManager {
        return AppLockManager(
            settingsManager = settingsManager,
            securityStorageManager = securityStorageManager,
            clock = { simulatedTimeMs },
        )
    }

    @Test
    fun testInitialStateWhenDisabled() {
        val manager = createManager()
        assertFalse("isLocked should be false when app lock is disabled", manager.isLocked.value)
        assertFalse("isAppLockEnabled should be false", manager.isAppLockEnabled())
        assertFalse("isAppLockConfigured should be false", manager.isAppLockConfigured())
        assertNull("pendingResumeIntent should be null initially", manager.pendingResumeIntent.value)
    }

    @Test
    fun testInitialStateWhenEnabledAndConfiguredLocksOnColdLaunch() {
        val encryptCipher = PasswordCipher.initEncryptCipher()
        securityStorageManager.setupAppLock(encryptCipher)
        settingsManager.isAppLockEnabled = true

        val manager = createManager()
        assertTrue("isLocked must be true on cold launch when enabled and configured", manager.isLocked.value)
    }

    @Test
    fun testEnableAppLockConfiguresLockAndUnlocks() {
        val manager = createManager()
        val encryptCipher = PasswordCipher.initEncryptCipher()

        manager.enableAppLock(encryptCipher)

        assertTrue("settingsManager.isAppLockEnabled should be true", settingsManager.isAppLockEnabled)
        assertTrue("securityStorageManager.isAppLockConfigured should be true", securityStorageManager.isAppLockConfigured())
        assertFalse("manager.isLocked should be false immediately after enabling", manager.isLocked.value)
    }

    @Test
    fun testDisableAppLockClearsStateAndPendingIntent() {
        val manager = createManager()
        val encryptCipher = PasswordCipher.initEncryptCipher()
        manager.enableAppLock(encryptCipher)

        val testIntent = Intent(Intent.ACTION_VIEW)
        manager.pendingResumeIntent.value = testIntent

        manager.disableAppLock()

        assertFalse("settingsManager.isAppLockEnabled should be false", settingsManager.isAppLockEnabled)
        assertFalse("securityStorageManager.isAppLockConfigured should be false", securityStorageManager.isAppLockConfigured())
        assertFalse("manager.isLocked should be false", manager.isLocked.value)
        assertNull("pendingResumeIntent must be cleared upon disable", manager.pendingResumeIntent.value)
    }

    @Test
    fun testUnlockWithCipherSuccessAndFailure() {
        val manager = createManager()
        val encryptCipher = PasswordCipher.initEncryptCipher()
        manager.enableAppLock(encryptCipher)

        manager.lock()
        assertTrue("manager should be locked after manual lock()", manager.isLocked.value)

        val iv = securityStorageManager.getAppLockIv()!!
        val decryptCipher = PasswordCipher.initDecryptCipher(iv)

        val unlockSuccess = manager.unlockWithCipher(decryptCipher)
        assertTrue("unlockWithCipher must return true with valid cipher", unlockSuccess)
        assertFalse("isLocked must transition to false on successful unlock", manager.isLocked.value)

        manager.lock()
        assertTrue(manager.isLocked.value)

        securityStorageManager.encryptedPrefs.edit()
            .putString("app_lock_token_sha256", "corrupted")
            .commit()

        val decryptCipher2 = PasswordCipher.initDecryptCipher(iv)
        val failedUnlock = manager.unlockWithCipher(decryptCipher2)
        assertFalse("unlockWithCipher must return false with corrupted token", failedUnlock)
        assertTrue("isLocked must remain true on failed unlock", manager.isLocked.value)
    }

    @Test
    fun testTimeoutZeroImmediatelyLocksOnBackground() {
        val manager = createManager()
        val encryptCipher = PasswordCipher.initEncryptCipher()
        manager.enableAppLock(encryptCipher)
        settingsManager.appLockTimeoutSeconds = 0

        assertFalse(manager.isLocked.value)

        manager.onAppBackgrounded()
        assertTrue("With timeout 0, app must lock immediately on background", manager.isLocked.value)
    }

    @Test
    fun testTimeoutThirtySecondsDelaysLockUntilExceeded() {
        val manager = createManager()
        val encryptCipher = PasswordCipher.initEncryptCipher()
        manager.enableAppLock(encryptCipher)
        settingsManager.appLockTimeoutSeconds = 30

        assertFalse(manager.isLocked.value)

        manager.onAppBackgrounded()
        assertFalse("App must not lock immediately on background when timeout is 30s", manager.isLocked.value)

        // Advance by 15 seconds (less than 30s timeout)
        simulatedTimeMs += 15000L
        manager.onAppForegrounded()
        assertFalse("App must not lock if foregrounded within timeout window", manager.isLocked.value)

        // Background again
        manager.onAppBackgrounded()

        // Advance by 35 seconds (exceeds 30s timeout)
        simulatedTimeMs += 35000L
        manager.onAppForegrounded()
        assertTrue("App must lock when foregrounded after timeout expired", manager.isLocked.value)
    }

    @Test
    fun testTimeoutDoesNotLockWhenAppLockDisabled() {
        val manager = createManager()
        settingsManager.isAppLockEnabled = false
        settingsManager.appLockTimeoutSeconds = 0

        manager.onAppBackgrounded()
        assertFalse("Disabled app lock must not lock on background", manager.isLocked.value)

        manager.onAppForegrounded()
        assertFalse("Disabled app lock must not lock on foreground", manager.isLocked.value)
    }

    @Test
    fun testPendingResumeIntentBuffering() {
        val manager = createManager()
        val encryptCipher = PasswordCipher.initEncryptCipher()
        manager.enableAppLock(encryptCipher)
        manager.lock()

        val sampleIntent = Intent(Intent.ACTION_VIEW).apply {
            putExtra("EXTRA_PROFILE", "server-42")
        }

        manager.pendingResumeIntent.value = sampleIntent
        assertNotNull(manager.pendingResumeIntent.value)
        assertEquals("server-42", manager.pendingResumeIntent.value?.getStringExtra("EXTRA_PROFILE"))

        val iv = securityStorageManager.getAppLockIv()!!
        val decryptCipher = PasswordCipher.initDecryptCipher(iv)
        manager.unlockWithCipher(decryptCipher)

        assertFalse(manager.isLocked.value)
        assertEquals(sampleIntent, manager.pendingResumeIntent.value)
    }
}
