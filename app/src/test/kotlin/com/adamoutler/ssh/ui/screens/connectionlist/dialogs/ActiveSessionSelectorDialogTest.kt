package com.adamoutler.ssh.ui.screens.connectionlist.dialogs

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActiveSessionSelectorDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testActiveSessionSelectorDialog() {
        var dismissed = false
        var selectedProfileId = ""
        var startNewClicked = false

        val activeConnections = setOf("id1", "id2")
        val profiles = listOf(
            ConnectionProfile("id1", "Nick1", "host", 22, Protocol.SSH, "user", AuthType.PASSWORD),
            ConnectionProfile("id2", "Nick2", "host", 22, Protocol.SSH, "user", AuthType.PASSWORD),
        )

        composeTestRule.setContent {
            ActiveSessionSelectorDialog(
                activeConnections = activeConnections,
                profiles = profiles,
                onDismiss = { dismissed = true },
                onSelectSession = { profId ->
                    selectedProfileId = profId
                },
                onStartNew = { startNewClicked = true },
            )
        }

        composeTestRule.onAllNodesWithText("Active Sessions").onFirst().assertExists()

        // Select the first session
        composeTestRule.onAllNodesWithText("Nick1").onFirst().performClick()

        assertEquals("id1", selectedProfileId)

        // Test Start New
        composeTestRule.onAllNodesWithText("Start New").onFirst().performClick()
        assertTrue(startNewClicked)
        org.junit.Assert.assertFalse(dismissed)
    }
}
