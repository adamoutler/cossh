package com.adamoutler.ssh.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.backup.BackupManager
import com.adamoutler.ssh.crypto.IdentityStorageManager
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.Protocol
import com.adamoutler.ssh.network.ConnectionStateRepository
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConnectionListDialogIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testConnectionList_ShowsSessionSelector_AndNavigates() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val storageManager = SecurityStorageManager(app)
        val identityStorage = IdentityStorageManager(app)
        val backupManager = BackupManager(app, storageManager, identityStorage)

        val profile = ConnectionProfile("test_id", "Test Server", "10.0.0.1", 22, Protocol.SSH, "user", AuthType.PASSWORD, 0)
        storageManager.saveProfile(profile)

        val viewModel = ConnectionListViewModel(app, storageManager, backupManager)

        // Add two active sessions for the same profile
        ConnectionStateRepository.addConnection("test_id")
        ConnectionStateRepository.addConnection("test_id")
        ConnectionStateRepository.getOrCreateSession("test_id", "session1")
        ConnectionStateRepository.getOrCreateSession("test_id", "session2")

        var selectedProfileId: String? = null
        var selectedSessionId: String? = null

        composeTestRule.setContent {
            ConnectionListScreen(
                viewModel = viewModel,
                onAddConnection = {},
                onEditConnection = {},
                onConnect = { p, s ->
                    selectedProfileId = p
                    selectedSessionId = s
                },
                onSettingsRequested = {}
            )
        }

        // Wait for profile to load
        composeTestRule.waitForIdle()

        // Click the server in the list
        composeTestRule.onNodeWithText("Test Server", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()

        // Dialog should appear
        composeTestRule.onNodeWithText("Active Sessions: Test Server").assertExists()
        composeTestRule.onNodeWithText("Start New").assertExists()

        // Click "Start New"
        composeTestRule.onNodeWithText("Start New").performClick()
        composeTestRule.waitForIdle()

        // It should have called onConnect with a new session ID (non-null)
        org.junit.Assert.assertEquals("test_id", selectedProfileId)
        org.junit.Assert.assertNotNull(selectedSessionId)

        // Reset variables for next click
        selectedProfileId = null
        selectedSessionId = null

        // Click the server in the list again to test Resume
        composeTestRule.onNodeWithText("Test Server", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()

        // Click the resume button
        composeTestRule.onNodeWithText("Resume Session 1", substring = true).performClick()
        composeTestRule.waitForIdle()

        // It should have called onConnect with an existing session ID
        org.junit.Assert.assertEquals("test_id", selectedProfileId)
        org.junit.Assert.assertNotNull(selectedSessionId)

        // Clean up
        ConnectionStateRepository.clearConnectionState("test_id")
    }
}
