package com.adamoutler.ssh.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HoldToConfirmButtonTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testHoldToConfirmButton_DisplaysText() {
        composeTestRule.setContent {
            HoldToConfirmButton(
                onConfirm = {},
                text = "Delete",
            )
        }

        composeTestRule.onNodeWithText("Delete").assertExists()
    }

    @Test
    fun testHoldToConfirmButton_LongClickConfirms() {
        var confirmed = false
        composeTestRule.setContent {
            HoldToConfirmButton(
                onConfirm = { confirmed = true },
                text = "Delete",
                durationMillis = 400, // Shorten for test
            )
        }

        composeTestRule.onNodeWithText("Delete").performTouchInput {
            longClick(durationMillis = 500)
        }

        // Wait for coroutines to complete
        composeTestRule.waitForIdle()

        assertTrue("Button should confirm after long hold", confirmed)
    }
}
