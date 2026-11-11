package com.adamoutler.ssh.ui.screens

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.billing.BillingManager
import com.adamoutler.ssh.crypto.SettingsManager
import com.adamoutler.ssh.data.TerminalBackButtonBehavior
import com.adamoutler.ssh.data.TerminalCursorStyle
import com.adamoutler.ssh.data.TerminalUrlConfirmationMode
import com.adamoutler.ssh.data.TerminalVolumeKeysAction
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
    private lateinit var settingsManager: SettingsManager
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        billingManager = BillingManager(application)
        driveSyncManager = DriveSyncManager(application)
        settingsManager = SettingsManager(application)
        viewModel = SettingsViewModel(application, billingManager, driveSyncManager, workManager = null)
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

    @Test
    fun testSavePassphraseAndSync() = runTest {
        val activity = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        try {
            viewModel.savePassphraseAndSync("test_passphrase", activity)
        } catch (e: Exception) {
            // CredentialManager fails
        }

        val state = viewModel.uiState.value
        assertTrue("Passphrase should be marked as set", state.isPassphraseSet)
        assertFalse("Passphrase dialog should be hidden", state.showPassphraseDialog)
    }

    @Test
    fun testAuthenticateGoogleWhenPassphraseIsSet() = runTest {
        val activity = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        try {
            viewModel.savePassphraseAndSync("test_passphrase", activity)
        } catch (_: Exception) {
            // Expected in test environment without Google Play Services
        }

        try {
            viewModel.authenticateGoogle(activity)
        } catch (_: Exception) {
            // Expected in test environment without Google Play Services
        }

        val state = viewModel.uiState.value
        assertFalse("Passphrase dialog should not show if passphrase is set", state.showPassphraseDialog)
    }

    @Test
    fun testPurchaseCloudSync() = runTest {
        val activity = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        viewModel.purchaseCloudSync(activity)
        // Verify it doesn't crash, logic handles billing flow silently without mock
    }

    @Test
    fun testUpdateCursorStyle() = runTest {
        viewModel.updateCursorStyle(TerminalCursorStyle.UNDERLINE)
        assertEquals(TerminalCursorStyle.UNDERLINE, viewModel.uiState.value.globalCursorStyle)
        assertEquals(TerminalCursorStyle.UNDERLINE, settingsManager.cursorStyle)

        viewModel.updateCursorStyle(TerminalCursorStyle.VERTICAL_BAR)
        assertEquals(TerminalCursorStyle.VERTICAL_BAR, viewModel.uiState.value.globalCursorStyle)
        assertEquals(TerminalCursorStyle.VERTICAL_BAR, settingsManager.cursorStyle)
    }

    @Test
    fun testUpdateCursorBlink() = runTest {
        viewModel.updateCursorBlink(false)
        assertFalse(viewModel.uiState.value.globalCursorBlink)
        assertFalse(settingsManager.globalCursorBlink)

        viewModel.updateCursorBlink(true)
        assertTrue(viewModel.uiState.value.globalCursorBlink)
        assertTrue(settingsManager.globalCursorBlink)
    }

    @Test
    fun testUpdateVolumeKeysAction() = runTest {
        viewModel.updateVolumeKeysAction(TerminalVolumeKeysAction.ZOOM_FONT)
        assertEquals(TerminalVolumeKeysAction.ZOOM_FONT, viewModel.uiState.value.volumeKeysAction)
        assertEquals(TerminalVolumeKeysAction.ZOOM_FONT, settingsManager.volumeKeysAction)

        viewModel.updateVolumeKeysAction(TerminalVolumeKeysAction.SYSTEM_VOLUME)
        assertEquals(TerminalVolumeKeysAction.SYSTEM_VOLUME, viewModel.uiState.value.volumeKeysAction)
        assertEquals(TerminalVolumeKeysAction.SYSTEM_VOLUME, settingsManager.volumeKeysAction)
    }

    @Test
    fun testUpdatePinchToZoomEnabled() = runTest {
        viewModel.updatePinchToZoomEnabled(false)
        assertFalse(viewModel.uiState.value.isPinchToZoomEnabled)
        assertFalse(settingsManager.isPinchToZoomEnabled)

        viewModel.updatePinchToZoomEnabled(true)
        assertTrue(viewModel.uiState.value.isPinchToZoomEnabled)
        assertTrue(settingsManager.isPinchToZoomEnabled)
    }

    @Test
    fun testUpdateTerminalBellHapticEnabled() = runTest {
        viewModel.updateTerminalBellHapticEnabled(false)
        assertFalse(viewModel.uiState.value.isTerminalBellHapticEnabled)
        assertFalse(settingsManager.isTerminalBellHapticEnabled)

        viewModel.updateTerminalBellHapticEnabled(true)
        assertTrue(viewModel.uiState.value.isTerminalBellHapticEnabled)
        assertTrue(settingsManager.isTerminalBellHapticEnabled)
    }

    @Test
    fun testUpdateBackButtonBehavior() = runTest {
        viewModel.updateBackButtonBehavior(TerminalBackButtonBehavior.SEND_ESC)
        assertEquals(TerminalBackButtonBehavior.SEND_ESC, viewModel.uiState.value.backButtonBehavior)
        assertEquals(TerminalBackButtonBehavior.SEND_ESC, settingsManager.backButtonBehavior)

        viewModel.updateBackButtonBehavior(TerminalBackButtonBehavior.BACKGROUND_SESSION)
        assertEquals(TerminalBackButtonBehavior.BACKGROUND_SESSION, viewModel.uiState.value.backButtonBehavior)
        assertEquals(TerminalBackButtonBehavior.BACKGROUND_SESSION, settingsManager.backButtonBehavior)
    }

    @Test
    fun testUpdateEnforceCharBasedInput() = runTest {
        viewModel.updateEnforceCharBasedInput(true)
        assertTrue(viewModel.uiState.value.enforceCharBasedInput)
        assertTrue(settingsManager.enforceCharBasedInput)

        viewModel.updateEnforceCharBasedInput(false)
        assertFalse(viewModel.uiState.value.enforceCharBasedInput)
        assertFalse(settingsManager.enforceCharBasedInput)
    }

    @Test
    fun testUpdatePocketDetectionEnabled() = runTest {
        viewModel.updatePocketDetectionEnabled(false)
        assertFalse(viewModel.uiState.value.isPocketDetectionEnabled)
        assertFalse(settingsManager.isPocketDetectionEnabled)

        viewModel.updatePocketDetectionEnabled(true)
        assertTrue(viewModel.uiState.value.isPocketDetectionEnabled)
        assertTrue(settingsManager.isPocketDetectionEnabled)
    }

    @Test
    fun testUpdateScreenshotBlockingEnabled() = runTest {
        viewModel.updateScreenshotBlockingEnabled(true)
        assertTrue(viewModel.uiState.value.isScreenshotBlockingEnabled)
        assertTrue(settingsManager.isScreenshotBlockingEnabled)

        viewModel.updateScreenshotBlockingEnabled(false)
        assertFalse(viewModel.uiState.value.isScreenshotBlockingEnabled)
        assertFalse(settingsManager.isScreenshotBlockingEnabled)
    }

    @Test
    fun testUpdateOverlayTouchBlockingEnabled() = runTest {
        viewModel.updateOverlayTouchBlockingEnabled(true)
        assertTrue(viewModel.uiState.value.isOverlayTouchBlockingEnabled)
        assertTrue(settingsManager.isOverlayTouchBlockingEnabled)

        viewModel.updateOverlayTouchBlockingEnabled(false)
        assertFalse(viewModel.uiState.value.isOverlayTouchBlockingEnabled)
        assertFalse(settingsManager.isOverlayTouchBlockingEnabled)
    }

    @Test
    fun testUpdateClipboardAutoClearSeconds() = runTest {
        viewModel.updateClipboardAutoClearSeconds(120)
        assertEquals(120, viewModel.uiState.value.clipboardAutoClearSeconds)
        assertEquals(120, settingsManager.clipboardAutoClearSeconds)

        viewModel.updateClipboardAutoClearSeconds(0)
        assertEquals(0, viewModel.uiState.value.clipboardAutoClearSeconds)
        assertEquals(0, settingsManager.clipboardAutoClearSeconds)
    }

    @Test
    fun testUpdateEnableSecurityAuditLog() = runTest {
        viewModel.updateEnableSecurityAuditLog(false)
        assertFalse(viewModel.uiState.value.enableSecurityAuditLog)
        assertFalse(settingsManager.enableSecurityAuditLog)

        viewModel.updateEnableSecurityAuditLog(true)
        assertTrue(viewModel.uiState.value.enableSecurityAuditLog)
        assertTrue(settingsManager.enableSecurityAuditLog)
    }

    @Test
    fun testUpdateTerminalUrlConfirmationMode() = runTest {
        viewModel.updateTerminalUrlConfirmationMode(TerminalUrlConfirmationMode.ALWAYS)
        assertEquals(TerminalUrlConfirmationMode.ALWAYS, viewModel.uiState.value.terminalUrlConfirmationMode)
        assertEquals(TerminalUrlConfirmationMode.ALWAYS, settingsManager.terminalUrlConfirmationMode)

        viewModel.updateTerminalUrlConfirmationMode(TerminalUrlConfirmationMode.NEVER)
        assertEquals(TerminalUrlConfirmationMode.NEVER, viewModel.uiState.value.terminalUrlConfirmationMode)
        assertEquals(TerminalUrlConfirmationMode.NEVER, settingsManager.terminalUrlConfirmationMode)
    }
}
