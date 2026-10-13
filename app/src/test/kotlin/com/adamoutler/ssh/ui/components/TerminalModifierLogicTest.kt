package com.adamoutler.ssh.ui.components

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TerminalModifierLogicTest {

    @Test
    fun testCtrlC_Encoding() {
        val mappedCp = TerminalKeyMapper.applyCtrlModifier('c'.code)
        assertEquals(0x03, mappedCp)

        val mappedUppercaseCp = TerminalKeyMapper.applyCtrlModifier('C'.code)
        assertEquals(0x03, mappedUppercaseCp)

        val (bytes, applied) = TerminalKeyMapper.mapCodePoint('c'.code, ctrlActive = true)
        assertEquals(true, applied)
        assertArrayEquals(byteArrayOf(0x03), bytes)
    }

    @Test
    fun testCtrl_AllAlphabetLetters() {
        for (i in 0 until 26) {
            val lower = 'a'.code + i
            val upper = 'A'.code + i
            val expected = i + 1

            assertEquals(expected, TerminalKeyMapper.applyCtrlModifier(lower))
            assertEquals(expected, TerminalKeyMapper.applyCtrlModifier(upper))
        }
    }

    @Test
    fun testCtrl_SpecialCharacters() {
        assertEquals(27, TerminalKeyMapper.applyCtrlModifier('['.code))
        assertEquals(29, TerminalKeyMapper.applyCtrlModifier(']'.code))
        assertEquals(28, TerminalKeyMapper.applyCtrlModifier('\\'.code))
        assertEquals(30, TerminalKeyMapper.applyCtrlModifier('^'.code))
        assertEquals(31, TerminalKeyMapper.applyCtrlModifier('_'.code))
    }

    @Test
    fun testCtrl_UnmodifiedForOtherCharacters() {
        assertEquals('1'.code, TerminalKeyMapper.applyCtrlModifier('1'.code))
        assertEquals('@'.code, TerminalKeyMapper.applyCtrlModifier('@'.code))
        assertEquals('!'.code, TerminalKeyMapper.applyCtrlModifier('!'.code))
    }

    @Test
    fun testAltA_Encoding() {
        val baseBytes = "A".toByteArray()
        val altBytes = TerminalKeyMapper.applyAltModifier(baseBytes, altActive = true)

        assertArrayEquals(byteArrayOf(0x1B, 0x41), altBytes)
    }

    @Test
    fun testAlt_InactiveDoesNotPrependEscape() {
        val baseBytes = "A".toByteArray()
        val resultBytes = TerminalKeyMapper.applyAltModifier(baseBytes, altActive = false)

        assertArrayEquals(baseBytes, resultBytes)
    }

    @Test
    fun testAlt_MultiByteDoesNotPrependEscape() {
        val multiByte = "AB".toByteArray()
        val resultBytes = TerminalKeyMapper.applyAltModifier(multiByte, altActive = true)

        assertArrayEquals(multiByte, resultBytes)
    }

    @Test
    fun testMapCodePoint_NewlineTranslation() {
        // \n (10) should be converted to \r (13)
        val (bytes, appliedCtrl) = TerminalKeyMapper.mapCodePoint(10, ctrlActive = false)
        assertEquals(false, appliedCtrl)
        assertArrayEquals(byteArrayOf(13), bytes)
    }

    @Test
    fun testMapKeyCode_StandardKeys() {
        assertArrayEquals("\r".toByteArray(), TerminalKeyMapper.mapKeyCode(android.view.KeyEvent.KEYCODE_ENTER, null, false))
        assertArrayEquals(byteArrayOf(0x7F), TerminalKeyMapper.mapKeyCode(android.view.KeyEvent.KEYCODE_DEL, null, false))
        assertArrayEquals("\t".toByteArray(), TerminalKeyMapper.mapKeyCode(android.view.KeyEvent.KEYCODE_TAB, null, false))
        assertArrayEquals(byteArrayOf(0x1B, '['.code.toByte(), 'A'.code.toByte()), TerminalKeyMapper.mapKeyCode(android.view.KeyEvent.KEYCODE_DPAD_UP, null, false))
        assertArrayEquals(byteArrayOf(0x1B, '['.code.toByte(), 'B'.code.toByte()), TerminalKeyMapper.mapKeyCode(android.view.KeyEvent.KEYCODE_DPAD_DOWN, null, false))
        assertArrayEquals(byteArrayOf(0x1B, '['.code.toByte(), 'C'.code.toByte()), TerminalKeyMapper.mapKeyCode(android.view.KeyEvent.KEYCODE_DPAD_RIGHT, null, false))
        assertArrayEquals(byteArrayOf(0x1B, '['.code.toByte(), 'D'.code.toByte()), TerminalKeyMapper.mapKeyCode(android.view.KeyEvent.KEYCODE_DPAD_LEFT, null, false))
    }

    @Test
    fun testMapExtraKey_AnsiSequences() {
        assertArrayEquals(byteArrayOf(0x1B), TerminalKeyMapper.mapExtraKey("Esc"))
        assertArrayEquals(byteArrayOf(0x03), TerminalKeyMapper.mapExtraKey("Ctrl-C"))
        assertArrayEquals(byteArrayOf(0x09), TerminalKeyMapper.mapExtraKey("Tab"))
        assertArrayEquals(byteArrayOf(0x1B, 'O'.code.toByte(), 'P'.code.toByte()), TerminalKeyMapper.mapExtraKey("F1"))
        assertArrayEquals(byteArrayOf(0x1B, 'O'.code.toByte(), 'Q'.code.toByte()), TerminalKeyMapper.mapExtraKey("F2"))
        assertArrayEquals(byteArrayOf(0x1B, 'O'.code.toByte(), 'R'.code.toByte()), TerminalKeyMapper.mapExtraKey("F3"))
        assertArrayEquals(byteArrayOf(0x1B, 'O'.code.toByte(), 'S'.code.toByte()), TerminalKeyMapper.mapExtraKey("F4"))
        assertArrayEquals(byteArrayOf(0x1B, '['.code.toByte(), '1'.code.toByte(), '5'.code.toByte(), '~'.code.toByte()), TerminalKeyMapper.mapExtraKey("F5"))
        assertArrayEquals(byteArrayOf(0x1B, '['.code.toByte(), '1'.code.toByte(), '7'.code.toByte(), '~'.code.toByte()), TerminalKeyMapper.mapExtraKey("F6"))
        assertArrayEquals(byteArrayOf(0x1B, '['.code.toByte(), '1'.code.toByte(), '8'.code.toByte(), '~'.code.toByte()), TerminalKeyMapper.mapExtraKey("F7"))
        assertArrayEquals(byteArrayOf(0x1B, '['.code.toByte(), '1'.code.toByte(), '9'.code.toByte(), '~'.code.toByte()), TerminalKeyMapper.mapExtraKey("F8"))
        assertArrayEquals(byteArrayOf(0x1B, '['.code.toByte(), '2'.code.toByte(), '0'.code.toByte(), '~'.code.toByte()), TerminalKeyMapper.mapExtraKey("F9"))
        assertArrayEquals(byteArrayOf(0x1B, '['.code.toByte(), '2'.code.toByte(), '1'.code.toByte(), '~'.code.toByte()), TerminalKeyMapper.mapExtraKey("F10"))
        assertArrayEquals(byteArrayOf(0x1B, '['.code.toByte(), '2'.code.toByte(), '3'.code.toByte(), '~'.code.toByte()), TerminalKeyMapper.mapExtraKey("F11"))
        assertArrayEquals(byteArrayOf(0x1B, '['.code.toByte(), '2'.code.toByte(), '4'.code.toByte(), '~'.code.toByte()), TerminalKeyMapper.mapExtraKey("F12"))
        assertArrayEquals(byteArrayOf(0x1B, '['.code.toByte(), 'H'.code.toByte()), TerminalKeyMapper.mapExtraKey("Home"))
        assertArrayEquals(byteArrayOf(0x1B, '['.code.toByte(), 'F'.code.toByte()), TerminalKeyMapper.mapExtraKey("End"))
        assertArrayEquals(byteArrayOf(0x1B, '['.code.toByte(), '5'.code.toByte(), '~'.code.toByte()), TerminalKeyMapper.mapExtraKey("PgUp"))
        assertArrayEquals(byteArrayOf(0x1B, '['.code.toByte(), '6'.code.toByte(), '~'.code.toByte()), TerminalKeyMapper.mapExtraKey("PgDn"))
        assertArrayEquals(byteArrayOf(0x1B, '['.code.toByte(), '2'.code.toByte(), '~'.code.toByte()), TerminalKeyMapper.mapExtraKey("Ins"))
        assertArrayEquals(byteArrayOf(0x1B, '['.code.toByte(), '3'.code.toByte(), '~'.code.toByte()), TerminalKeyMapper.mapExtraKey("Del"))
        assertArrayEquals(byteArrayOf(0x1B, '['.code.toByte(), '3'.code.toByte(), '2'.code.toByte(), '~'.code.toByte()), TerminalKeyMapper.mapExtraKey("PrtSc"))
        assertArrayEquals(byteArrayOf(0x1A), TerminalKeyMapper.mapExtraKey("Pause"))
        assertNull(TerminalKeyMapper.mapExtraKey("UnknownKey"))
    }

    @Test
    fun testTerminalInputLockout() {
        var bytesSent: ByteArray? = null
        val mapper = TerminalKeyMapper(
            isInputLocked = { true },
            sendRawBytes = { bytesSent = it },
        )

        mapper.sendToTerminal(byteArrayOf(0x03))
        assertNull("Bytes should not be sent when terminal is locked", bytesSent)
    }

    @Test
    fun testTerminalInputNotLocked() {
        var bytesSent: ByteArray? = null
        val mapper = TerminalKeyMapper(
            isInputLocked = { false },
            sendRawBytes = { bytesSent = it },
        )

        mapper.sendToTerminal(byteArrayOf(0x03))
        assertArrayEquals(byteArrayOf(0x03), bytesSent)
    }
}
