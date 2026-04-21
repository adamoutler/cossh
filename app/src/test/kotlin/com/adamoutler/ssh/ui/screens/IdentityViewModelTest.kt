package com.adamoutler.ssh.ui.screens

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.crypto.IdentityStorageManager
import com.adamoutler.ssh.data.IdentityProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
    fun testDeleteIdentity() = runTest {
        val identity = IdentityProfile(id = "to-delete", name = "Delete Me", username = "gone")
        viewModel.saveIdentity(identity)
        
        var identities = viewModel.identities.first()
        assertTrue(identities.any { it.id == "to-delete" })
        
        viewModel.deleteIdentity("to-delete")
        
        identities = viewModel.identities.first()
        assertTrue(identities.none { it.id == "to-delete" })
    }
}
