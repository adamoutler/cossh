package com.adamoutler.ssh.ui.components

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
class TerminalScreenInstrumentedTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test(timeout = 300000L)
    fun testBackPressWhenKeyboardHiddenNavigatesBack() {
        // Clear any previous state
        com.adamoutler.ssh.network.SshSessionProvider.activeConnections.value.forEach {
            com.adamoutler.ssh.network.SshSessionProvider.removeConnection(it)
        }
        
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

        // Wait a few seconds to allow external screenshot capture
        Thread.sleep(3000)

        // By default, terminalInputState is 0 (keyboard hidden).
        // Pressing back should trigger the onNavigateBack callback.
        
        // Use espresso back press to trigger the Activity's OnBackPressedDispatcher
        pressBackUnconditionally()
        
        // Wait for the callback to be invoked
        val navigated = latch.await(2, TimeUnit.SECONDS)
        
        assertTrue("Back navigation was not called when keyboard was hidden", navigated || backNavigationCalled)
    }

    @Test(timeout = 300000L)
    fun testKeepAliveDialogFlow() {
        var backNavigationCalled = false

        // Simulate active connection
        com.adamoutler.ssh.network.SshSessionProvider.addConnection("test-id")

        composeTestRule.setContent {
            TerminalScreen(
                onNavigateBack = {
                    backNavigationCalled = true
                }
            )
        }
        
        composeTestRule.waitForIdle()

        // 1. Back button triggers the dialogue
        pressBackUnconditionally()
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        composeTestRule.onNodeWithText("Keep Session Alive?").assertExists()
        
        // 2. Second back press dismisses the dialogue
        pressBackUnconditionally()
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        composeTestRule.onNodeWithText("Keep Session Alive?").assertDoesNotExist()

        // Re-open dialogue
        pressBackUnconditionally()
        composeTestRule.waitForIdle()
        Thread.sleep(500)

        // 3. Selecting 'Terminate' kills session and navigates back
        composeTestRule.onNodeWithText("Terminate").performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        assertTrue("Navigate back should be called when terminating", backNavigationCalled)

        // Clean up
        com.adamoutler.ssh.network.SshSessionProvider.removeConnection("test-id")
    }

    @Test(timeout = 300000L)
    fun testBackPressNavigatesBackWhenConnectionDrops() {
        var backNavigationCalled = false

        // Simulate active connection
        com.adamoutler.ssh.network.SshSessionProvider.addConnection("drop-test-id")

        composeTestRule.setContent {
            TerminalScreen(
                onNavigateBack = {
                    backNavigationCalled = true
                }
            )
        }
        
        composeTestRule.waitForIdle()

        // Connection drops
        com.adamoutler.ssh.network.SshSessionProvider.removeConnection("drop-test-id")
        composeTestRule.waitForIdle()
        Thread.sleep(500)

        // Back button should immediately navigate back without KeepAlive dialog
        pressBackUnconditionally()
        composeTestRule.waitForIdle()
        Thread.sleep(500)

        composeTestRule.onNodeWithText("Keep Session Alive?").assertDoesNotExist()
        assertTrue("Navigate back should be called since connection dropped", backNavigationCalled)
    }

    @Test(timeout = 300000L)
    fun testFloatingOverlayButtons() {
        var backNavigationCalled = false

        composeTestRule.setContent {
            TerminalScreen(
                onNavigateBack = {
                    backNavigationCalled = true
                }
            )
        }
        composeTestRule.waitForIdle()

        // 1. Initial state: overlay buttons should not exist
        composeTestRule.onNodeWithText("Background Session", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Terminate Session", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNode(androidx.compose.ui.test.hasContentDescription("Background Session"), useUnmergedTree = true).assertDoesNotExist()

        // 2. Tap the terminal screen
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(com.termux.view.TerminalView::class.java))
            .perform(object : androidx.test.espresso.ViewAction {
                override fun getConstraints() = androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(com.termux.view.TerminalView::class.java)
                override fun getDescription() = "Invoke onSingleTapUp"
                override fun perform(uiController: androidx.test.espresso.UiController?, view: android.view.View?) {
                    (view as? com.termux.view.TerminalView)?.mClient?.onSingleTapUp(null)
                }
            })
        composeTestRule.waitForIdle()

        // Overlay buttons should appear
        composeTestRule.onNode(androidx.compose.ui.test.hasContentDescription("Background Session")).assertExists()
        composeTestRule.onNode(androidx.compose.ui.test.hasContentDescription("Terminate Session")).assertExists()

        // 3. Test tapping 'Left Arrow' (Background Session)
        composeTestRule.onNode(androidx.compose.ui.test.hasContentDescription("Background Session")).performClick()
        composeTestRule.waitForIdle()

        assertTrue("Navigate back should be called when backgrounding", backNavigationCalled)
        backNavigationCalled = false // Reset

        // State machine might transition or buttons might stay. Let's just click 'X' now.
        composeTestRule.onNode(androidx.compose.ui.test.hasContentDescription("Terminate Session")).performClick()
        composeTestRule.waitForIdle()

        assertTrue("Navigate back should be called when terminating", backNavigationCalled)
    }
}
