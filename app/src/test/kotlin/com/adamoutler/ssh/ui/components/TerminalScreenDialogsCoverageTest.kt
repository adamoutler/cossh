package com.adamoutler.ssh.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TerminalScreenDialogsCoverageTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testConnectionFailedDialog() {
        var onDismissClicked = false
        composeTestRule.setContent {
            ConnectionFailedDialog(
                errorMessage = "Connection Failed Error",
                onDismiss = { onDismissClicked = true },
            )
        }

        composeTestRule.onNodeWithText("Error: Connection Failed Error").assertExists()
        composeTestRule.onNodeWithText("OK").performClick()

        composeTestRule.waitForIdle()
        assertTrue(onDismissClicked)
    }

    @Test
    fun testKeepAliveDialog() {
        var onDismissClicked = false
        var onConfirmClicked = false
        var onTerminateClicked = false

        composeTestRule.setContent {
            KeepAliveDialog(
                onDismiss = { onDismissClicked = true },
                onKeepAlive = { onConfirmClicked = true },
                onTerminate = { onTerminateClicked = true },
            )
        }

        composeTestRule.onNodeWithText("Keep Alive").performClick()
        composeTestRule.waitForIdle()
        assertTrue(onConfirmClicked)

        composeTestRule.onNodeWithText("Terminate").performClick()
        composeTestRule.waitForIdle()
        assertTrue(onTerminateClicked)
        assertFalse(onDismissClicked)
    }

    @Test
    fun testTerminateConfirmDialog() {
        var onDismissClicked = false
        var onConfirmClicked = false
        composeTestRule.setContent {
            TerminateConfirmDialog(
                onDismiss = { onDismissClicked = true },
                onTerminate = { onConfirmClicked = true },
            )
        }

        composeTestRule.onNodeWithText("Cancel").performClick()
        assertTrue(onDismissClicked)

        composeTestRule.onNodeWithText("Terminate").performClick()
        assertTrue(onConfirmClicked)
    }

    @Test
    fun testSessionDisconnectedDialog() {
        var onDismissClicked = false
        composeTestRule.setContent {
            SessionDisconnectedDialog(
                onDismiss = { onDismissClicked = true },
            )
        }

        composeTestRule.onNodeWithText("OK").performClick()

        composeTestRule.waitForIdle()
        assertTrue(onDismissClicked)
    }
}
