package com.adamoutler.ssh.ui.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adamoutler.ssh.crypto.PasswordCipher
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.crypto.SettingsManager
import com.adamoutler.ssh.network.ConnectionStateRepository
import com.adamoutler.ssh.security.AppLockManager
import com.adamoutler.ssh.ui.screens.AppLockScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AppLockNavigationGateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var settingsManager: SettingsManager
    private lateinit var securityStorageManager: SecurityStorageManager
    private lateinit var appLockManager: AppLockManager

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

        val cipher = PasswordCipher.initEncryptCipher()
        securityStorageManager.setupAppLock(cipher)
        settingsManager.isAppLockEnabled = true

        appLockManager = AppLockManager(settingsManager, securityStorageManager)
    }

    @Test
    fun testAppLockScreenComposeElementsAndClick() {
        var unlockClicked = false

        composeTestRule.setContent {
            AppLockScreen(onUnlockClick = { unlockClicked = true })
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("app_lock_screen").assertExists()
        composeTestRule.onNodeWithText("CoSSH Locked").assertExists()
        composeTestRule.onNodeWithText("Biometric authentication is required to access your SSH sessions and encrypted credentials.").assertExists()
        composeTestRule.onNodeWithTag("unlock_biometrics_button").assertExists()

        composeTestRule.onNodeWithTag("unlock_biometrics_button").performClick()
        composeTestRule.waitForIdle()

        assertTrue("onUnlockClick must be invoked when button is clicked", unlockClicked)
    }

    @Test
    fun testAppNavigationBuffersDeepLinkWhenLocked() {
        assertTrue("appLockManager must be locked initially", appLockManager.isLocked.value)

        val deepLinkIntent = Intent(Intent.ACTION_VIEW, Uri.parse("ssh://testuser@192.168.1.100:22"))

        composeTestRule.setContent {
            AppNavigation(appLockManager = appLockManager)
        }

        composeTestRule.waitForIdle()

        // Buffer the intent into pendingResumeIntent while locked
        appLockManager.pendingResumeIntent.value = deepLinkIntent
        composeTestRule.waitForIdle()

        assertEquals("Intent must remain buffered while locked", deepLinkIntent, appLockManager.pendingResumeIntent.value)
    }

    @Test
    fun testAppNavigationClearsAndDispatchesBufferedIntentUponUnlock() {
        assertTrue(appLockManager.isLocked.value)

        val testIntent = Intent(Intent.ACTION_VIEW, Uri.parse("ssh://admin@server.local:2222"))
        appLockManager.pendingResumeIntent.value = testIntent

        composeTestRule.setContent {
            AppNavigation(appLockManager = appLockManager)
        }

        composeTestRule.waitForIdle()

        // Perform unlock with valid decrypt cipher
        val iv = securityStorageManager.getAppLockIv()!!
        val decryptCipher = PasswordCipher.initDecryptCipher(iv)
        val unlockSuccess = appLockManager.unlockWithCipher(decryptCipher)

        assertTrue("Unlock should succeed", unlockSuccess)
        assertFalse("isLocked should be false", appLockManager.isLocked.value)

        composeTestRule.waitForIdle()

        // Once unlocked, the LaunchedEffect(isLocked) pops the pending intent and clears it
        assertNull("Pending resume intent should be cleared after dispatch", appLockManager.pendingResumeIntent.value)
    }

    @Test
    fun testOnNewIntentBuffersWhileLockedAndProcessesWhenUnlocked() {
        val manager = AppLockManager(settingsManager, securityStorageManager)
        assertTrue(manager.isLocked.value)

        val newIntent = Intent(Intent.ACTION_VIEW, Uri.parse("ssh://user2@router.lan"))
        manager.pendingResumeIntent.value = newIntent

        assertNotNull(manager.pendingResumeIntent.value)
        assertEquals("router.lan", manager.pendingResumeIntent.value?.data?.host)
        assertEquals("user2", manager.pendingResumeIntent.value?.data?.userInfo)

        val iv = securityStorageManager.getAppLockIv()!!
        val decryptCipher = PasswordCipher.initDecryptCipher(iv)
        manager.unlockWithCipher(decryptCipher)

        assertFalse(manager.isLocked.value)
    }
}
