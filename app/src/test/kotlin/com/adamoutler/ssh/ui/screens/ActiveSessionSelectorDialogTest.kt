package com.adamoutler.ssh.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.Protocol
import com.adamoutler.ssh.ui.screens.connectionlist.dialogs.ActiveSessionSelectorDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ActiveSessionSelectorDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testSelectSession_ResumesSession() {
        var selectedSession: String? = null
        var startNewClicked = false
        var dismissClicked = false

        val profile1 = ConnectionProfile("id1", "Host 1", "host1.com", 22, Protocol.SSH, "user1", AuthType.PASSWORD, 0)
        val activeConnections = setOf("session1_id1", "session2_id1")

        composeTestRule.setContent {
            ActiveSessionSelectorDialog(
                activeConnections = activeConnections,
                profiles = listOf(profile1),
                onSelectSession = { selectedSession = it },
                onStartNew = { startNewClicked = true },
                onDismiss = { dismissClicked = true }
            )
        }

        // The dialog shows the profile ID / nickname.
        // Wait, the dialog displays `profile?.nickname ?: profileId`
        // But activeConnections are just strings. Are they sessionId or profileId?
        // In the codebase it looks like activeConnections are the session IDs.
        // Let's click on the first session button.
        composeTestRule.onNodeWithText("session1_id1").performClick()

        assertEquals("session1_id1", selectedSession)
        assertFalse(startNewClicked)
    }

    @Test
    fun testStartNew_StartsNewSession() {
        var selectedSession: String? = null
        var startNewClicked = false
        var dismissClicked = false

        val profile1 = ConnectionProfile("id1", "Host 1", "host1.com", 22, Protocol.SSH, "user1", AuthType.PASSWORD, 0)
        val activeConnections = setOf("session1_id1", "session2_id1")

        composeTestRule.setContent {
            ActiveSessionSelectorDialog(
                activeConnections = activeConnections,
                profiles = listOf(profile1),
                onSelectSession = { selectedSession = it },
                onStartNew = { startNewClicked = true },
                onDismiss = { dismissClicked = true }
            )
        }

        composeTestRule.onNodeWithText("Start New").performClick()

        assertTrue(startNewClicked)
        assertEquals(null, selectedSession)
    }

    private fun assertFalse(b: Boolean) {
        org.junit.Assert.assertFalse(b)
    }
}
