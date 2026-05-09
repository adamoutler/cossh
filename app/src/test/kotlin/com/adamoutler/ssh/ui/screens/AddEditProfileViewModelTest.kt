package com.adamoutler.ssh.ui.screens

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.data.AuthType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AddEditProfileViewModelTest {

    @Test
    fun `test save and retrieve profile`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = app.getSharedPreferences("test_prefs", android.content.Context.MODE_PRIVATE)
        val storageManager = com.adamoutler.ssh.crypto.SecurityStorageManager(app, prefs)
        val identityStorageManager = com.adamoutler.ssh.crypto.IdentityStorageManager(app, prefs)
        val viewModel = AddEditProfileViewModel(app, storageManager, identityStorageManager)

        viewModel.saveProfile(
            id = "test-id-123",
            nickname = "Local Test",
            host = "127.0.0.1",
            port = "2222",
            protocol = com.adamoutler.ssh.data.Protocol.SSH,
            username = "root",
            authType = AuthType.PASSWORD,
            password = "testpassword".toByteArray(),
            keyReference = null,
        )

        val profile = viewModel.getProfile("test-id-123")
        assertNotNull(profile)
        assertEquals("Local Test", profile?.nickname)
        assertEquals("127.0.0.1", profile?.host)
        assertEquals(2222, profile?.port)
        assertEquals("root", profile?.username)
        assertEquals(AuthType.PASSWORD, profile?.authType)
        assertEquals("testpassword", profile?.password?.decodeToString())
    }

    @Test
    fun `test updateState and resetState`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = app.getSharedPreferences("test_prefs", android.content.Context.MODE_PRIVATE)
        val viewModel = AddEditProfileViewModel(app, com.adamoutler.ssh.crypto.SecurityStorageManager(app, prefs), com.adamoutler.ssh.crypto.IdentityStorageManager(app, prefs))

        viewModel.updateState { it.copy(nickname = "New Name", host = "1.2.3.4") }
        assertEquals("New Name", viewModel.uiState.value.nickname)
        assertEquals("1.2.3.4", viewModel.uiState.value.host)

        viewModel.resetState()
        assertEquals("", viewModel.uiState.value.nickname)
        assertEquals("", viewModel.uiState.value.host)
    }

    @Test
    fun `test loadProfileIfNeeded`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = app.getSharedPreferences("test_prefs", android.content.Context.MODE_PRIVATE)
        val storageManager = com.adamoutler.ssh.crypto.SecurityStorageManager(app, prefs)
        val viewModel = AddEditProfileViewModel(app, storageManager, com.adamoutler.ssh.crypto.IdentityStorageManager(app, prefs))

        val profile = com.adamoutler.ssh.data.ConnectionProfile(
            id = "load-test", nickname = "Load", host = "test", envVars = mapOf("A" to "B")
        )
        storageManager.saveProfile(profile)

        viewModel.loadProfileIfNeeded("load-test")
        assertEquals(true, viewModel.uiState.value.isLoaded)
        assertEquals("Load", viewModel.uiState.value.nickname)
        assertEquals("A=B", viewModel.uiState.value.envVarsText)

        // Loading again shouldn't overwrite if modified
        viewModel.updateState { it.copy(nickname = "Modified") }
        viewModel.loadProfileIfNeeded("load-test")
        assertEquals("Modified", viewModel.uiState.value.nickname)
    }

    @Test
    fun `test getAvailableKeys and getIdentities`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = app.getSharedPreferences("test_prefs", android.content.Context.MODE_PRIVATE)
        val storageManager = com.adamoutler.ssh.crypto.SecurityStorageManager(app, prefs)
        val identityStorageManager = com.adamoutler.ssh.crypto.IdentityStorageManager(app, prefs)
        val viewModel = AddEditProfileViewModel(app, storageManager, identityStorageManager)

        identityStorageManager.saveIdentity(com.adamoutler.ssh.data.IdentityProfile(id = "id-1", name = "Test Identity", username = "test"))

        val keys = viewModel.getAvailableKeys()
        org.junit.Assert.assertNotNull(keys)

        val identities = viewModel.getIdentities()
        org.junit.Assert.assertTrue(identities.any { it.name == "Test Identity" })
    }
}
