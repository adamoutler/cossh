package com.adamoutler.ssh.ui.components

import android.view.KeyEvent
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TerminalAnsiKeyMapperTest {

    @Test
    fun testModifierCalculation() {
        assertEquals(1, TerminalAnsiKeyMapper.calculateModifierParam(shift = false, alt = false, ctrl = false))
        assertEquals(2, TerminalAnsiKeyMapper.calculateModifierParam(shift = true, alt = false, ctrl = false))
        assertEquals(3, TerminalAnsiKeyMapper.calculateModifierParam(shift = false, alt = true, ctrl = false))
        assertEquals(4, TerminalAnsiKeyMapper.calculateModifierParam(shift = true, alt = true, ctrl = false))
        assertEquals(5, TerminalAnsiKeyMapper.calculateModifierParam(shift = false, alt = false, ctrl = true))
        assertEquals(6, TerminalAnsiKeyMapper.calculateModifierParam(shift = true, alt = false, ctrl = true))
        assertEquals(7, TerminalAnsiKeyMapper.calculateModifierParam(shift = false, alt = true, ctrl = true))
        assertEquals(8, TerminalAnsiKeyMapper.calculateModifierParam(shift = true, alt = true, ctrl = true))
    }

    @Test
    fun testEscapeKey() {
        val bytes = TerminalAnsiKeyMapper.mapKeyCode(KeyEvent.KEYCODE_ESCAPE, false, false, false)
        assertArrayEquals(byteArrayOf(0x1B), bytes)
    }

    @Test
    fun testBacktab() {
        // Shift+Tab -> Backtab \e[Z
        val bytes = TerminalAnsiKeyMapper.mapKeyCode(KeyEvent.KEYCODE_TAB, shift = true, alt = false, ctrl = false)
        assertArrayEquals(byteArrayOf(0x1B, '['.code.toByte(), 'Z'.code.toByte()), bytes)
    }

    @Test
    fun testCursorKeysUnmodified() {
        val up = TerminalAnsiKeyMapper.mapKeyCode(KeyEvent.KEYCODE_DPAD_UP, false, false, false)
        assertArrayEquals(byteArrayOf(0x1B, '['.code.toByte(), 'A'.code.toByte()), up)

        val down = TerminalAnsiKeyMapper.mapKeyCode(KeyEvent.KEYCODE_DPAD_DOWN, false, false, false)
        assertArrayEquals(byteArrayOf(0x1B, '['.code.toByte(), 'B'.code.toByte()), down)

        val right = TerminalAnsiKeyMapper.mapKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT, false, false, false)
        assertArrayEquals(byteArrayOf(0x1B, '['.code.toByte(), 'C'.code.toByte()), right)

        val left = TerminalAnsiKeyMapper.mapKeyCode(KeyEvent.KEYCODE_DPAD_LEFT, false, false, false)
        assertArrayEquals(byteArrayOf(0x1B, '['.code.toByte(), 'D'.code.toByte()), left)
    }

    @Test
    fun testCursorKeysModified() {
        // Ctrl+Up -> \e[1;5A
        val ctrlUp = TerminalAnsiKeyMapper.mapKeyCode(KeyEvent.KEYCODE_DPAD_UP, shift = false, alt = false, ctrl = true)
        val expected = "\u001B[1;5A".toByteArray(Charsets.US_ASCII)
        assertArrayEquals(expected, ctrlUp)

        // Shift+Alt+Down -> mod = 1 + 1 + 2 = 4 -> \e[1;4B
        val shiftAltDown = TerminalAnsiKeyMapper.mapKeyCode(KeyEvent.KEYCODE_DPAD_DOWN, shift = true, alt = true, ctrl = false)
        assertArrayEquals("\u001B[1;4B".toByteArray(Charsets.US_ASCII), shiftAltDown)
    }

    @Test
    fun testFunctionKeysF1ThroughF4() {
        // F1 unmodified uses SS3 \eOP
        val f1 = TerminalAnsiKeyMapper.mapKeyCode(KeyEvent.KEYCODE_F1, false, false, false)
        assertArrayEquals(byteArrayOf(0x1B, 'O'.code.toByte(), 'P'.code.toByte()), f1)

        // F1 with Ctrl uses CSI \e[1;5P
        val f1Ctrl = TerminalAnsiKeyMapper.mapKeyCode(KeyEvent.KEYCODE_F1, false, false, true)
        assertArrayEquals("\u001B[1;5P".toByteArray(Charsets.US_ASCII), f1Ctrl)

        // F4 unmodified uses SS3 \eOS
        val f4 = TerminalAnsiKeyMapper.mapKeyCode(KeyEvent.KEYCODE_F4, false, false, false)
        assertArrayEquals(byteArrayOf(0x1B, 'O'.code.toByte(), 'S'.code.toByte()), f4)
    }

    @Test
    fun testFunctionKeysF5ThroughF12() {
        // F5 unmodified: \e[15~
        val f5 = TerminalAnsiKeyMapper.mapKeyCode(KeyEvent.KEYCODE_F5, false, false, false)
        assertArrayEquals("\u001B[15~".toByteArray(Charsets.US_ASCII), f5)

        // F12 with Ctrl: mod = 5 -> \e[24;5~
        val f12Ctrl = TerminalAnsiKeyMapper.mapKeyCode(KeyEvent.KEYCODE_F12, false, false, true)
        assertArrayEquals("\u001B[24;5~".toByteArray(Charsets.US_ASCII), f12Ctrl)
    }

    @Test
    fun testNavigationKeys() {
        val pgUp = TerminalAnsiKeyMapper.mapKeyCode(KeyEvent.KEYCODE_PAGE_UP, false, false, false)
        assertArrayEquals("\u001B[5~".toByteArray(Charsets.US_ASCII), pgUp)

        val pgDn = TerminalAnsiKeyMapper.mapKeyCode(KeyEvent.KEYCODE_PAGE_DOWN, false, false, false)
        assertArrayEquals("\u001B[6~".toByteArray(Charsets.US_ASCII), pgDn)

        val home = TerminalAnsiKeyMapper.mapKeyCode(KeyEvent.KEYCODE_MOVE_HOME, false, false, false)
        assertArrayEquals(byteArrayOf(0x1B, '['.code.toByte(), 'H'.code.toByte()), home)

        val end = TerminalAnsiKeyMapper.mapKeyCode(KeyEvent.KEYCODE_MOVE_END, false, false, false)
        assertArrayEquals(byteArrayOf(0x1B, '['.code.toByte(), 'F'.code.toByte()), end)
    }

    @Test
    fun testEscapeSequenceUpperBounds() {
        // Test all key codes across all modifier combinations (1..8) to ensure <= 16 bytes
        val keyCodes = listOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN,
            KeyEvent.KEYCODE_INSERT, KeyEvent.KEYCODE_FORWARD_DEL,
            KeyEvent.KEYCODE_F1, KeyEvent.KEYCODE_F2, KeyEvent.KEYCODE_F3, KeyEvent.KEYCODE_F4,
            KeyEvent.KEYCODE_F5, KeyEvent.KEYCODE_F6, KeyEvent.KEYCODE_F7, KeyEvent.KEYCODE_F8,
            KeyEvent.KEYCODE_F9, KeyEvent.KEYCODE_F10, KeyEvent.KEYCODE_F11, KeyEvent.KEYCODE_F12,
        )

        for (keyCode in keyCodes) {
            for (shift in listOf(false, true)) {
                for (alt in listOf(false, true)) {
                    for (ctrl in listOf(false, true)) {
                        val bytes = TerminalAnsiKeyMapper.mapKeyCode(keyCode, shift, alt, ctrl)
                        assertNotNull("Keycode $keyCode should map to bytes", bytes)
                        assertTrue("Length must be <= 16 bytes", (bytes?.size ?: 0) <= 16)
                    }
                }
            }
        }
    }
}
