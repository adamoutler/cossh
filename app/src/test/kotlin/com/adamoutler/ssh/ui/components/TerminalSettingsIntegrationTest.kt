package com.adamoutler.ssh.ui.components

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adamoutler.ssh.crypto.IdentityStorageManager
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.KeepScreenOnMode
import com.adamoutler.ssh.ui.screens.AddEditProfileViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class TerminalSettingsIntegrationTest {

    @Test
    fun testConnectionCanStartWithButtonBarOpen() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val storageManager = SecurityStorageManager(app)
        val identityStorageManager = IdentityStorageManager(app)
        val viewModel = AddEditProfileViewModel(app, storageManager, identityStorageManager)

        viewModel.saveProfile(
            id = "test_profile_open",
            nickname = "Test Open",
            host = "localhost",
            port = "22",
            protocol = com.adamoutler.ssh.data.Protocol.SSH,
            username = "user",
            authType = com.adamoutler.ssh.data.AuthType.PASSWORD,
            password = null,
            keyReference = null,
            terminalInputState = 2, // OPEN
            keepScreenOnMode = KeepScreenOnMode.ALWAYS_ON,
        )

        val profile = storageManager.getProfile("test_profile_open")
        assertNotNull(profile)
        assertEquals(2, profile?.terminalInputState)
        assertEquals(KeepScreenOnMode.ALWAYS_ON, profile?.keepScreenOnMode)
    }

    @Test
    fun testConnectionCanStartWithButtonBarClosed() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val storageManager = SecurityStorageManager(app)
        val identityStorageManager = IdentityStorageManager(app)
        val viewModel = AddEditProfileViewModel(app, storageManager, identityStorageManager)

        viewModel.saveProfile(
            id = "test_profile_closed",
            nickname = "Test Closed",
            host = "localhost",
            port = "22",
            protocol = com.adamoutler.ssh.data.Protocol.SSH,
            username = "user",
            authType = com.adamoutler.ssh.data.AuthType.PASSWORD,
            password = null,
            keyReference = null,
            terminalInputState = 0, // CLOSED
            keepScreenOnMode = KeepScreenOnMode.SYSTEM_DEFAULT,
        )

        val profile = storageManager.getProfile("test_profile_closed")
        assertNotNull(profile)
        assertEquals(0, profile?.terminalInputState)
        assertEquals(KeepScreenOnMode.SYSTEM_DEFAULT, profile?.keepScreenOnMode)
    }

    @Test
    fun testTogglingButtonBarTriggersBackgroundWrite() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val storageManager = SecurityStorageManager(app)

        // Initial state
        val initialProfile = ConnectionProfile(
            id = "test_profile_toggle",
            nickname = "Test Toggle",
            host = "localhost",
            port = 22,
            protocol = com.adamoutler.ssh.data.Protocol.SSH,
            username = "user",
            authType = com.adamoutler.ssh.data.AuthType.PASSWORD,
            terminalInputState = 0, // CLOSED
        )
        storageManager.saveProfile(initialProfile)

        // Simulate UI toggle triggering a background write
        val job = CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val updatedProfile = initialProfile.copy(terminalInputState = 2) // OPEN
            storageManager.saveProfile(updatedProfile)
        }

        // Wait for background write to complete
        job.join()

        // Verify state is persisted
        val profile = storageManager.getProfile("test_profile_toggle")
        assertNotNull(profile)
        assertEquals(2, profile?.terminalInputState)
    }

    @Test
    fun testKeepScreenOnModeResolution() {
        // SYSTEM_DEFAULT is always false (relies on Android OS timeout)
        org.junit.Assert.assertFalse(com.adamoutler.ssh.data.shouldKeepScreenOn(KeepScreenOnMode.SYSTEM_DEFAULT, isProximityNear = false, isDataFlowing = false))
        org.junit.Assert.assertFalse(com.adamoutler.ssh.data.shouldKeepScreenOn(KeepScreenOnMode.SYSTEM_DEFAULT, isProximityNear = false, isDataFlowing = true))
        org.junit.Assert.assertFalse(com.adamoutler.ssh.data.shouldKeepScreenOn(KeepScreenOnMode.SYSTEM_DEFAULT, isProximityNear = true, isDataFlowing = true))

        // ALWAYS_ON keeps screen awake unless pocket/proximity is covered
        org.junit.Assert.assertTrue(com.adamoutler.ssh.data.shouldKeepScreenOn(KeepScreenOnMode.ALWAYS_ON, isProximityNear = false, isDataFlowing = false))
        org.junit.Assert.assertFalse(com.adamoutler.ssh.data.shouldKeepScreenOn(KeepScreenOnMode.ALWAYS_ON, isProximityNear = true, isDataFlowing = false))
        org.junit.Assert.assertFalse(com.adamoutler.ssh.data.shouldKeepScreenOn(KeepScreenOnMode.ALWAYS_ON, isProximityNear = true, isDataFlowing = true))

        // SMART_AWAKE only stays awake when data is actively flowing AND phone is not in pocket
        org.junit.Assert.assertFalse(com.adamoutler.ssh.data.shouldKeepScreenOn(KeepScreenOnMode.SMART_AWAKE, isProximityNear = false, isDataFlowing = false))
        org.junit.Assert.assertTrue(com.adamoutler.ssh.data.shouldKeepScreenOn(KeepScreenOnMode.SMART_AWAKE, isProximityNear = false, isDataFlowing = true))
        org.junit.Assert.assertFalse(com.adamoutler.ssh.data.shouldKeepScreenOn(KeepScreenOnMode.SMART_AWAKE, isProximityNear = true, isDataFlowing = true))
    }

    @Test
    fun testWindowFlagKeepScreenOnTransitions() {
        val activityController = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup()
        val window = activityController.get().window

        fun applyKeepScreenOn(mode: KeepScreenOnMode, isProximityNear: Boolean, isDataFlowing: Boolean) {
            val shouldKeepOn = com.adamoutler.ssh.data.shouldKeepScreenOn(mode, isProximityNear, isDataFlowing)
            if (shouldKeepOn) {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        // Test SYSTEM_DEFAULT: flag never set
        applyKeepScreenOn(KeepScreenOnMode.SYSTEM_DEFAULT, isProximityNear = false, isDataFlowing = true)
        assertEquals(0, window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Test ALWAYS_ON: flag set when far, cleared when near (pocket)
        applyKeepScreenOn(KeepScreenOnMode.ALWAYS_ON, isProximityNear = false, isDataFlowing = false)
        assertEquals(
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
        )

        applyKeepScreenOn(KeepScreenOnMode.ALWAYS_ON, isProximityNear = true, isDataFlowing = false)
        assertEquals(0, window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Test SMART_AWAKE: flag set only when data flowing and far, cleared when idle or pocket
        applyKeepScreenOn(KeepScreenOnMode.SMART_AWAKE, isProximityNear = false, isDataFlowing = false)
        assertEquals(0, window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        applyKeepScreenOn(KeepScreenOnMode.SMART_AWAKE, isProximityNear = false, isDataFlowing = true)
        assertEquals(
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
        )

        applyKeepScreenOn(KeepScreenOnMode.SMART_AWAKE, isProximityNear = true, isDataFlowing = true)
        assertEquals(0, window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
