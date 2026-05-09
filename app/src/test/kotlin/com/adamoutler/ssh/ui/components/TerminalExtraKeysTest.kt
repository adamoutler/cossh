package com.adamoutler.ssh.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TerminalExtraKeysTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testTerminalExtraKeys_rendersKeys() {
        var toggledKey = ""
        var pressedKey = ""
        composeTestRule.setContent {
            TerminalExtraKeys(
                ctrlState = ModifierState.INACTIVE,
                altState = ModifierState.INACTIVE,
                superState = ModifierState.INACTIVE,
                menuState = ModifierState.INACTIVE,
                onKeyToggle = { toggledKey = it },
                onKeyPress = { pressedKey = it },
            )
        }
        
        composeTestRule.onNodeWithText("Esc").assertExists()
        composeTestRule.onNodeWithText("Tab").assertExists()
        composeTestRule.onNodeWithText("Ctrl").assertExists()
        composeTestRule.onNodeWithText("Alt").assertExists()
        
        composeTestRule.onNodeWithText("Esc").performClick()
        assertTrue("Esc should be pressed", pressedKey == "Esc")

        composeTestRule.onNodeWithText("Ctrl").performClick()
        assertTrue("Ctrl should be toggled", toggledKey == "Ctrl")
    }
}

