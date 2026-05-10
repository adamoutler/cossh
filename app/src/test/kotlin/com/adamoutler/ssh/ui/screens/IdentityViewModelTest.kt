package com.adamoutler.ssh.ui.screens

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.crypto.IdentityStorageManager
import com.adamoutler.ssh.data.IdentityProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IdentityViewModelTest {

    private lateinit var application: Application
    private lateinit var storageManager: IdentityStorageManager
    private lateinit var viewModel: IdentityViewModel

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        val testPrefs = application.getSharedPreferences("test_identity_vm_prefs", 0)
        storageManager = IdentityStorageManager(application, testPrefs)
        viewModel = IdentityViewModel(application, storageManager)
    }

    @Test
    fun testSaveAndLoadIdentities() = runTest {
        val identity = IdentityProfile(name = "Test Identity", username = "testuser")
        viewModel.saveIdentity(identity)

        val identities = viewModel.identities.first()
        assertTrue("Should contain the saved identity", identities.any { it.name == "Test Identity" })
    }

    @Test
    fun testUpdateStateAndResetState() {
        viewModel.updateState { it.copy(name = "Updated Name", username = "Updated Username") }
        var state = viewModel.uiState.value
        assertTrue(state.name == "Updated Name")
        assertTrue(state.username == "Updated Username")

        viewModel.resetState()
        state = viewModel.uiState.value
        assertTrue(state.name == "")
        assertTrue(state.username == "")
        assertTrue(!state.isLoaded)
    }

    @Test
    fun testLoadIdentityIfNeeded() = runTest {
        val identity = IdentityProfile(id = "load-me", name = "Load Me", username = "loaded", password = "pwd".toByteArray())
        viewModel.saveIdentity(identity)

        // Let flow settle
        kotlinx.coroutines.delay(50)

        viewModel.loadIdentityIfNeeded("load-me")
        val state = viewModel.uiState.value
        assertTrue(state.isLoaded)
        assertTrue(state.name == "Load Me")
        assertTrue(state.username == "loaded")
        assertTrue(state.isPasswordLocked)

        // Calling again should no-op
        viewModel.updateState { it.copy(name = "Changed") }
        viewModel.loadIdentityIfNeeded("load-me")
        assertTrue(viewModel.uiState.value.name == "Changed") // Didn't reload
    }

    @Test
    fun testLoadIdentityIfNeededNull() {
        viewModel.loadIdentityIfNeeded(null)
        assertTrue(viewModel.uiState.value.isLoaded)
    }

    @Test
    fun testGetIdentity() = runTest {
        val identity = IdentityProfile(id = "get-me", name = "Get Me", username = "got")
        viewModel.saveIdentity(identity)
        kotlinx.coroutines.delay(50)
        val got = viewModel.getIdentity("get-me")
        assertTrue(got?.name == "Get Me")
    }

    @Test
    fun testDeleteIdentity() = runTest {
        val identity = IdentityProfile(id = "to-delete", name = "Delete Me", username = "gone")
        viewModel.saveIdentity(identity)

        var identities = viewModel.identities.first()
        assertTrue(identities.any { it.id == "to-delete" })

        viewModel.deleteIdentity("to-delete")

        identities = viewModel.identities.first()
        assertTrue(identities.none { it.id == "to-delete" })
    }

    @Test
    fun testGenerateEd25519KeyPair() = kotlinx.coroutines.runBlocking {
        viewModel.generateEd25519KeyPair()

        var state = viewModel.uiState.value
        var attempts = 0
        while (state.publicKey.isEmpty() && attempts < 50) {
            kotlinx.coroutines.delay(10)
            state = viewModel.uiState.value
            attempts++
        }

        val privKey = state.privateKey
        assertTrue("Public key should not be empty", state.publicKey.isNotEmpty())
        assertTrue("Private key should not be empty", privKey != null && privKey.isNotEmpty())
        assertTrue("AuthType should be KEY", state.authType == com.adamoutler.ssh.data.AuthType.KEY)
        assertTrue("Public key should start with ssh-ed25519", state.publicKey.startsWith("ssh-ed25519"))
    }

    @Test
    fun testGenerateRSAKeyPair() = kotlinx.coroutines.runBlocking {
        viewModel.generateRSAKeyPair()

        var state = viewModel.uiState.value
        var attempts = 0
        while (state.publicKey.isEmpty() && attempts < 1000) {
            kotlinx.coroutines.delay(20)
            state = viewModel.uiState.value
            attempts++
        }

        val privKey = state.privateKey
        assertTrue("Public key should not be empty", state.publicKey.isNotEmpty())
        assertTrue("Private key should not be empty", privKey != null && privKey.isNotEmpty())
        assertTrue("AuthType should be KEY", state.authType == com.adamoutler.ssh.data.AuthType.KEY)
        assertTrue("Public key should start with ssh-rsa", state.publicKey.startsWith("ssh-rsa"))
    }

    @Test
    fun testInjectPublicKeyFailure() = runTest {
        viewModel.injectPublicKey("127.0.0.1", 12345, "testpass")
        Thread.sleep(500)
    }
}
