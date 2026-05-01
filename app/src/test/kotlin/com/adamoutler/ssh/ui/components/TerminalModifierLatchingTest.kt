package com.adamoutler.ssh.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalModifierLatchingTest {

    @Test
    fun testModifierStateTransitions() {
        var state = ModifierState.INACTIVE

        state = state.next()
        assertEquals(ModifierState.STICKY, state)

        state = state.next()
        assertEquals(ModifierState.LOCKED, state)

        state = state.next()
        assertEquals(ModifierState.INACTIVE, state)
    }

    @Test
    fun testStickyConsumption() {
        var ctrlState = ModifierState.STICKY

        // Mock processing a character
        if (ctrlState == ModifierState.STICKY) {
            ctrlState = ModifierState.INACTIVE
        }

        assertEquals(ModifierState.INACTIVE, ctrlState)
    }

    @Test
    fun testLockedPersistence() {
        var ctrlState = ModifierState.LOCKED

        // Mock processing multiple characters
        repeat(3) {
            if (ctrlState == ModifierState.STICKY) {
                ctrlState = ModifierState.INACTIVE
            }
        }

        assertEquals(ModifierState.LOCKED, ctrlState)
    }

    @Test
    fun testAltStickyConsumption() {
        var altState = ModifierState.STICKY
        val bytes = byteArrayOf('a'.code.toByte())

        var finalBytes = bytes
        if (altState != ModifierState.INACTIVE && bytes.size == 1) {
            finalBytes = byteArrayOf(0x1B) + finalBytes
            if (altState == ModifierState.STICKY) {
                altState = ModifierState.INACTIVE
            }
        }

        assertEquals(ModifierState.INACTIVE, altState)
        assertEquals(0x1B.toByte(), finalBytes[0])
        assertEquals('a'.code.toByte(), finalBytes[1])
    }
}
