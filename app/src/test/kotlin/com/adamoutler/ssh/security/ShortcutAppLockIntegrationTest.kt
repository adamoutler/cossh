package com.adamoutler.ssh.security

import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adamoutler.ssh.crypto.PasswordCipher
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.crypto.SettingsManager
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.network.ConnectionStateRepository
import com.adamoutler.ssh.ui.navigation.AppNavigation
import com.adamoutler.ssh.ui.navigation.IntentSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ShortcutAppLockIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var settingsManager: SettingsManager
    private lateinit var securityStorageManager: SecurityStorageManager
    private lateinit var appLockManager: AppLockManager

    private val testProfile = ConnectionProfile(
        id = "shortcut-test-profile-1",
        nickname = "Production Bastion",
        host = "bastion.example.com",
        port = 22,
        username = "admin",
    )

    @Before
    fun setup() {
        ConnectionStateRepository.isHeadlessTest = true
        context = ApplicationProvider.getApplicationContext()
        val settingsPrefs = context.getSharedPreferences("cossh_settings", Context.MODE_PRIVATE)
        settingsPrefs.edit().clear().commit()
        val storagePrefs = context.getSharedPreferences("secret_ssh_profiles", Context.MODE_PRIVATE)
        storagePrefs.edit().clear().commit()

        settingsManager = SettingsManager(context)
        securityStorageManager = SecurityStorageManager(context, storagePrefs)

        // Save profile
        securityStorageManager.saveProfile(testProfile)

        // Configure App Lock
        val cipher = PasswordCipher.initEncryptCipher()
        securityStorageManager.setupAppLock(cipher)
        settingsManager.isAppLockEnabled = true

        appLockManager = AppLockManager(settingsManager, securityStorageManager)
    }

    @Test
    fun `shortcut intent is buffered when app is locked and not dispatched`() {
        assertTrue("App lock must be active initially", appLockManager.isLocked.value)

        val shortcutIntent = Intent(IntentSanitizer.ACTION_LAUNCH_PROFILE).apply {
            putExtra(IntentSanitizer.EXTRA_PROFILE_ID, testProfile.id)
        }

        val testActivity = Robolectric.buildActivity(ComponentActivity::class.java).get()
        testActivity.intent = shortcutIntent

        composeTestRule.setContent {
            AppNavigation(appLockManager = appLockManager, activity = testActivity)
        }
        composeTestRule.waitForIdle()

        // Verify intent was buffered in pendingResumeIntent and not executed
        assertEquals(
            "Shortcut intent must be buffered in pendingResumeIntent while app is locked",
            shortcutIntent,
            appLockManager.pendingResumeIntent.value,
        )
    }

    @Test
    fun `buffered shortcut intent is safely dispatched after biometric unlock`() {
        assertTrue(appLockManager.isLocked.value)

        val shortcutIntent = Intent(IntentSanitizer.ACTION_LAUNCH_PROFILE).apply {
            putExtra(IntentSanitizer.EXTRA_PROFILE_ID, testProfile.id)
        }

        val testActivity = Robolectric.buildActivity(ComponentActivity::class.java).get()
        testActivity.intent = shortcutIntent

        composeTestRule.setContent {
            AppNavigation(appLockManager = appLockManager, activity = testActivity)
        }
        composeTestRule.waitForIdle()

        assertEquals(shortcutIntent, appLockManager.pendingResumeIntent.value)

        // Unlock with valid cipher
        val iv = securityStorageManager.getAppLockIv()!!
        val decryptCipher = PasswordCipher.initDecryptCipher(iv)
        val unlocked = appLockManager.unlockWithCipher(decryptCipher)

        assertTrue("Biometric unlock should succeed", unlocked)
        assertFalse("App lock state should be unlocked", appLockManager.isLocked.value)

        composeTestRule.waitForIdle()

        // Pending intent should have been dispatched and cleared
        assertNull("Pending resume intent should be cleared after dispatch", appLockManager.pendingResumeIntent.value)
    }
}
