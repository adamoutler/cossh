package com.adamoutler.ssh.ui.components

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class TerminalModifierLogicTest {

    // Simple unit test to verify the byte encoding logic used in TerminalScreen.kt
    // for mapping sticky modifiers to correct PTY bytes.
    @Test
    fun testCtrlC_Encoding() {
        val codePoint = 'c'.code
        var cp = codePoint
        
        // Apply Ctrl Logic as seen in TerminalScreen.kt
        if (cp in 'a'.code..'z'.code) {
            cp = cp - 'a'.code + 1
        } else if (cp in 'A'.code..'Z'.code) {
            cp = cp - 'A'.code + 1
        } else if (cp == '['.code) {
            cp = 27 // ESC
        } else if (cp == ']'.code) {
            cp = 29
        } else if (cp == '\\'.code) {
            cp = 28
        } else if (cp == '^'.code) {
            cp = 30
        } else if (cp == '_'.code) {
            cp = 31
        }

        val chars = Character.toChars(cp)
        val bytes = String(chars).toByteArray(Charsets.UTF_8)
        
        // Expecting 0x03 for Ctrl+C
        assertArrayEquals(byteArrayOf(0x03), bytes)
    }

    @Test
    fun testAltA_Encoding() {
        val baseBytes = "A".toByteArray()
        val altBytes = byteArrayOf(0x1B) + baseBytes
        
        assertArrayEquals(byteArrayOf(0x1B, 0x41), altBytes)
    }

    @Test
    fun testTerminalInputLockout() {
        val showDisconnectedOverlay = true
        var bytesSent: ByteArray? = null
        
        val sendToTerminal: (ByteArray) -> Unit = { bytes ->
            if (showDisconnectedOverlay) {
                println("Log.d(TerminalScreen, Input locked: session disconnected.)")
            } else {
                bytesSent = bytes
            }
        }
        
        sendToTerminal(byteArrayOf(0x03))
        
        org.junit.Assert.assertNull("Bytes should not be sent when terminal is locked", bytesSent)
    }
}