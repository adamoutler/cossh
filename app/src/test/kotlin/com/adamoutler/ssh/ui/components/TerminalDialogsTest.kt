package com.adamoutler.ssh.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TerminalDialogsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testConnectionFailedDialog() {
        var dismissed = false
        composeTestRule.setContent {
            ConnectionFailedDialog(
                errorMessage = "Failed to connect",
                onDismiss = { dismissed = true },
            )
        }

        composeTestRule.onAllNodesWithText("Connection Failed").onFirst().assertExists()
        composeTestRule.onAllNodesWithText("OK").onFirst().performClick()
        assertTrue(dismissed)
    }

    @Test
    fun testKeepAliveDialog() {
        var onKeepAliveClicked = false
        var onTerminateClicked = false

        @Suppress("UNUSED_VARIABLE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
        var onDismissClicked = false
        composeTestRule.setContent {
            KeepAliveDialog(
                onDismiss = { onDismissClicked = true },
                onKeepAlive = { onKeepAliveClicked = true },
                onTerminate = { onTerminateClicked = true },
            )
        }

        composeTestRule.onAllNodesWithText("Keep Session Alive?").onFirst().assertExists()

        composeTestRule.onAllNodesWithText("Keep Alive").onFirst().performClick()
        assertTrue(onKeepAliveClicked)

        composeTestRule.onAllNodesWithText("Terminate").onFirst().performClick()
        assertTrue(onTerminateClicked)
    }

    @Test
    fun testTerminateConfirmDialog() {
        var onDismissClicked = false
        var onTerminateClicked = false
        composeTestRule.setContent {
            TerminateConfirmDialog(
                onDismiss = { onDismissClicked = true },
                onTerminate = { onTerminateClicked = true },
            )
        }

        composeTestRule.onAllNodesWithText("Terminate Connection?").onFirst().assertExists()

        composeTestRule.onAllNodesWithText("Cancel").onFirst().performClick()
        assertTrue(onDismissClicked)

        composeTestRule.onAllNodesWithText("Terminate").onFirst().performClick()
        assertTrue(onTerminateClicked)
    }

    @Test
    fun testSessionDisconnectedDialog() {
        var onDismissClicked = false
        composeTestRule.setContent {
            SessionDisconnectedDialog(
                onDismiss = { onDismissClicked = true },
            )
        }

        composeTestRule.onAllNodesWithText("Session Disconnected").onFirst().assertExists()
        composeTestRule.onAllNodesWithText("OK").onFirst().performClick()
        assertTrue(onDismissClicked)
    }
}
