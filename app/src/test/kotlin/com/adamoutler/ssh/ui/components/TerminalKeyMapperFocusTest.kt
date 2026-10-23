package com.adamoutler.ssh.ui.components

import android.view.KeyEvent
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TerminalKeyMapperFocusTest {

    @Test
    fun testHardwareAltModifierPrefixesEsc() {
        var dispatchedBytes: ByteArray? = null
        val mapper = TerminalKeyMapper(
            sendRawBytes = { dispatchedBytes = it }
        )

        // Simulate Alt+B key event
        val altEvent = KeyEvent(
            0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_B, 0,
            KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        )

        mapper.onKeyDown(KeyEvent.KEYCODE_B, altEvent)

        // Alt+B sends ESC + 'b'
        val expected = byteArrayOf(0x1B, 'b'.code.toByte())
        assertArrayEquals(expected, dispatchedBytes)
    }

    @Test
    fun testFocusLossResetModifiers() {
        var ctrlState = ModifierState.STICKY
        var altState = ModifierState.STICKY
        val mapper = TerminalKeyMapper(
            getCtrlState = { ctrlState },
            setCtrlState = { ctrlState = it },
            getAltState = { altState },
            setAltState = { altState = it },
        )

        // Simulate physical Shift and Alt pressed
        mapper.onKeyDown(KeyEvent.KEYCODE_SHIFT_LEFT)
        mapper.onKeyDown(KeyEvent.KEYCODE_ALT_LEFT)
        assertTrue(mapper.readShiftKey())
        assertTrue(mapper.readAltKey())

        // Focus loss trigger
        mapper.resetModifiers()

        assertFalse(mapper.readShiftKey())
        assertFalse(mapper.readAltKey())
        assertFalse(mapper.readControlKey())
        assertEquals(ModifierState.INACTIVE, ctrlState)
        assertEquals(ModifierState.INACTIVE, altState)
    }

    @Test
    fun testEscapeAndBacktabThroughKeyMapper() {
        var dispatchedBytes: ByteArray? = null
        val mapper = TerminalKeyMapper(
            sendRawBytes = { dispatchedBytes = it }
        )

        // Escape
        mapper.onKeyDown(KeyEvent.KEYCODE_ESCAPE)
        assertArrayEquals(byteArrayOf(0x1B), dispatchedBytes)

        // Shift+Tab -> Backtab \e[Z
        val shiftTabEvent = KeyEvent(
            0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB, 0,
            KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        )
        mapper.onKeyDown(KeyEvent.KEYCODE_TAB, shiftTabEvent)
        assertArrayEquals(byteArrayOf(0x1B, '['.code.toByte(), 'Z'.code.toByte()), dispatchedBytes)
    }
}
