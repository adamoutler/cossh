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
}
