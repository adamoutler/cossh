package com.adamoutler.ssh.ui.keys

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeyManagementScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testKeyManagementScreenDisplaysAndGeneratesKey() {
        composeTestRule.setContent {
            CoSSHTheme {
                KeyManagementScreen()
            }
        }

        // Verify initial state
        composeTestRule.onNodeWithText("Key Management").assertExists()

        // Open dialog
        composeTestRule.onNodeWithContentDescription("Generate New Key").assertExists().performClick()

        // Click ED25519 to generate
        composeTestRule.onNodeWithText("ED25519").assertExists().performClick()

        // Verify key is displayed
        composeTestRule.onNodeWithText("Algorithm: Ed25519").assertExists()
    }
}
