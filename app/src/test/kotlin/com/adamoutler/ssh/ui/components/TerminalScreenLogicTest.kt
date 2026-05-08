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
class TerminalScreenLogicTest {

    @Test
    fun testIsBleedThroughEvent() {
        val connectionStartTime = 1000L

        // Null event should be false
        assertFalse(isBleedThroughEvent(null, connectionStartTime))

        // Event before connection start should be true
        val beforeEvent = KeyEvent(500L, 500L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 0)
        assertTrue(isBleedThroughEvent(beforeEvent, connectionStartTime))

        // Event right after connection start should be true (within 500ms)
        val bleedEvent = KeyEvent(1200L, 1200L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 0)
        assertTrue(isBleedThroughEvent(bleedEvent, connectionStartTime))

        // Event well after connection start should be false (> 500ms)
        val validEvent = KeyEvent(1600L, 1600L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 0)
        assertFalse(isBleedThroughEvent(validEvent, connectionStartTime))
    }
}
