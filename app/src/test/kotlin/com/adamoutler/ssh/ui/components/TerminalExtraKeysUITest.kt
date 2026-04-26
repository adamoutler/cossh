package com.adamoutler.ssh.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.activity.compose.setContent

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class TerminalExtraKeysUITest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @Test
    fun testF1AndF12KeysSendCorrectBytes() {
        org.junit.Assume.assumeTrue(com.adamoutler.ssh.BuildConfig.DEBUG)
        var dispatchedKey = ""

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
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
            }

            // Click F1
            composeTestRule.onNodeWithText("F1").performClick()
            assertEquals("F1", dispatchedKey)

            // Click F12
            composeTestRule.onNodeWithText("F12").performClick()
            assertEquals("F12", dispatchedKey)
        }
    }
}
