package com.adamoutler.ssh.ui.components

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TerminalScreenNewlineBleedThroughTest {

    @Test
    fun testBleedThroughKeyEventIsIgnored() {
        val connectionStartTime = 10000L
        
        // A key event that happened BEFORE the connection started
        val pastEvent = KeyEvent(5000L, 5000L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0)
        val isBleedThrough = isBleedThroughEvent(pastEvent, connectionStartTime)
        assertTrue("Event from the past should be identified as a bleed-through", isBleedThrough)
        
        // A key event that happened AFTER the connection started
        val futureEvent = KeyEvent(15000L, 15000L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0)
        val isNotBleedThrough = isBleedThroughEvent(futureEvent, connectionStartTime)
        assertFalse("Event from the future should not be identified as a bleed-through", isNotBleedThrough)
        
        // Null event
        assertFalse("Null event should not crash or be identified as bleed-through", isBleedThroughEvent(null, connectionStartTime))
    }
}
