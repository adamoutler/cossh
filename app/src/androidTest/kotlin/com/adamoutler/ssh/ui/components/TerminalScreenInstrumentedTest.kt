package com.adamoutler.ssh.ui.components

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class TerminalScreenInstrumentedTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun testBackPressWhenKeyboardHiddenNavigatesBack() {
        val latch = CountDownLatch(1)
        var backNavigationCalled = false

        composeTestRule.activity.runOnUiThread {
            composeTestRule.activity.setContent {
                TerminalScreen(
                    onNavigateBack = {
                        backNavigationCalled = true
                        latch.countDown()
                    }
                )
            }
        }
        
        composeTestRule.waitForIdle()

        // By default, terminalInputState is 0 (keyboard hidden).
        // Pressing back should trigger the onNavigateBack callback.
        
        // Use espresso back press to trigger the Activity's OnBackPressedDispatcher
        pressBackUnconditionally()
        
        // Wait for the callback to be invoked
        val navigated = latch.await(2, TimeUnit.SECONDS)
        
        assertTrue("Back navigation was not called when keyboard was hidden", navigated || backNavigationCalled)
    }
}
