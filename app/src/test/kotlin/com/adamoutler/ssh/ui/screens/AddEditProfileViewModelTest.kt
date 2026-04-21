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
            username = "root",
            authType = AuthType.PASSWORD,
            password = "testpassword".toByteArray(),
            keyReference = null
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
}
