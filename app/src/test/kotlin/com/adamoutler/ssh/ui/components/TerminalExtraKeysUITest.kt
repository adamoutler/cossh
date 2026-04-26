package com.adamoutler.ssh.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertArrayEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@org.junit.Ignore("Crashes Robolectric in release due to missing UI manifest")
class TerminalExtraKeysUITest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testF1AndF12KeysSendCorrectBytes() {
        org.junit.Assume.assumeTrue(com.adamoutler.ssh.BuildConfig.DEBUG)
        var dispatchedKey = ""

        composeTestRule.setContent {
            TerminalExtraKeys(
                ctrlActive = false,
                altActive = false,
                superActive = false,
                menuActive = false,
                onKeyToggle = {},
                onKeyPress = { key -> dispatchedKey = key },
                initialPage = 2
            )
        }

        // Click F1
        composeTestRule.onNodeWithText("F1").performClick()
        org.junit.Assert.assertEquals("F1", dispatchedKey)

        // Click F12
        composeTestRule.onNodeWithText("F12").performClick()
        org.junit.Assert.assertEquals("F12", dispatchedKey)
    }
}
