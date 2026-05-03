package com.adamoutler.ssh.ui.screens

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.billing.BillingManager
import com.adamoutler.ssh.sync.DriveSyncManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsViewModelTest {

    private lateinit var application: Application
    private lateinit var billingManager: BillingManager
    private lateinit var driveSyncManager: DriveSyncManager
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        billingManager = BillingManager(application)
        driveSyncManager = DriveSyncManager(application)
        viewModel = SettingsViewModel(application, billingManager, driveSyncManager)
    }

    @Test
    fun testInitialState() = runTest {
        val state = viewModel.uiState.value
        assertFalse("Show passphrase dialog should be false initially", state.showPassphraseDialog)
        assertFalse("Is syncing should be false initially", state.isSyncing)
    }

    @Test
    fun testUpdateDefaultGroupName() = runTest {
        viewModel.updateDefaultGroupName("My Custom Group")
        val state = viewModel.uiState.value
        assertEquals("My Custom Group", state.defaultGroupName)
    }

    @Test
    fun testUpdateDefaultGroupName_emptyString_fallsBackToUncategorized() = runTest {
        viewModel.updateDefaultGroupName("   ")
        val state = viewModel.uiState.value
        assertEquals("Uncategorized", state.defaultGroupName)
    }

    @Test
    fun testShowAndDismissPassphraseDialog() = runTest {
        viewModel.showPassphraseDialog()
        assertTrue("Passphrase dialog should be visible", viewModel.uiState.value.showPassphraseDialog)

        viewModel.dismissPassphraseDialog()
        assertFalse("Passphrase dialog should be hidden", viewModel.uiState.value.showPassphraseDialog)
    }

    @Test
    fun testAuthenticateGoogleShowsPassphraseDialogIfNotSet() = runTest {
        // By default, passphrase is not set in a fresh Robolectric environment
        val activity = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        viewModel.authenticateGoogle(activity)

        val state = viewModel.uiState.value
        assertTrue("Passphrase dialog should show if passphrase is not set", state.showPassphraseDialog)
        assertFalse("Should not start syncing immediately", state.isSyncing)
    }
}
