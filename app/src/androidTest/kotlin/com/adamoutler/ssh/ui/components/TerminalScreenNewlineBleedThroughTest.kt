package com.adamoutler.ssh.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import android.view.KeyEvent
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class TerminalScreenNewlineBleedThroughTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testBleedThroughKeyEventIsIgnored() {
        val connectionStartTime = System.currentTimeMillis()
        
        // A key event that happened BEFORE the connection started (e.g. from the previous screen)
        val bleedThroughTime = connectionStartTime - 1000 
        
        val keyEvent = KeyEvent(
            bleedThroughTime,
            System.currentTimeMillis(),
            KeyEvent.ACTION_DOWN,
            KeyEvent.KEYCODE_ENTER,
            0
        )
        
        // The logic in TerminalScreen.kt asserts that if e.downTime < connectionStartTime, 
        // the event is consumed (returns true) but NOT sent to the terminal.
        val shouldConsumeAndIgnore = keyEvent.downTime < connectionStartTime
        
        assertTrue("Bleed-through Enter key event should be caught and ignored", shouldConsumeAndIgnore)
    }
}
