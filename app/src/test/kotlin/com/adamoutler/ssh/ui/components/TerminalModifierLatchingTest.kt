package com.adamoutler.ssh.ui.components

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalModifierLatchingTest {

    @Test
    fun testModifierStateTransitions() {
        var state = ModifierState.INACTIVE
        assertFalse(state.isActive)
        assertEquals(ModifierState.UNLATCHED, state)

        state = state.next()
        assertEquals(ModifierState.STICKY, state)
        assertTrue(state.isActive)
        assertEquals(ModifierState.LATCHED, state)

        state = state.next()
        assertEquals(ModifierState.LOCKED, state)
        assertTrue(state.isActive)

        state = state.next()
        assertEquals(ModifierState.INACTIVE, state)
        assertFalse(state.isActive)
    }

    @Test
    fun testStickyConsumptionDirect() {
        assertEquals(ModifierState.INACTIVE, TerminalKeyMapper.consumeSticky(ModifierState.STICKY))
        assertEquals(ModifierState.LOCKED, TerminalKeyMapper.consumeSticky(ModifierState.LOCKED))
        assertEquals(ModifierState.INACTIVE, TerminalKeyMapper.consumeSticky(ModifierState.INACTIVE))
    }

    @Test
    fun testTerminalKeyMapper_stickyConsumptionOnExtraKeyPress() {
        var ctrl = ModifierState.STICKY
        var sup = ModifierState.STICKY
        var menu = ModifierState.STICKY
        var sentBytes: ByteArray? = null

        val mapper = TerminalKeyMapper(
            getCtrlState = { ctrl },
            setCtrlState = { ctrl = it },
            getSuperState = { sup },
            setSuperState = { sup = it },
            getMenuState = { menu },
            setMenuState = { menu = it },
            sendRawBytes = { sentBytes = it },
        )

        mapper.onExtraKeyPress("Tab")

        assertEquals(ModifierState.INACTIVE, ctrl)
        assertEquals(ModifierState.INACTIVE, sup)
        assertEquals(ModifierState.INACTIVE, menu)
        assertArrayEquals(byteArrayOf(0x09), sentBytes)
    }

    @Test
    fun testTerminalKeyMapper_lockedPersistenceAcrossMultipleKeyPresses() {
        var ctrl = ModifierState.LOCKED
        var sup = ModifierState.LOCKED
        var menu = ModifierState.LOCKED
        var sendCount = 0

        val mapper = TerminalKeyMapper(
            getCtrlState = { ctrl },
            setCtrlState = { ctrl = it },
            getSuperState = { sup },
            setSuperState = { sup = it },
            getMenuState = { menu },
            setMenuState = { menu = it },
            sendRawBytes = { sendCount++ },
        )

        repeat(3) {
            mapper.onExtraKeyPress("Tab")
        }

        assertEquals(3, sendCount)
        assertEquals(ModifierState.LOCKED, ctrl)
        assertEquals(ModifierState.LOCKED, sup)
        assertEquals(ModifierState.LOCKED, menu)
    }

    @Test
    fun testTerminalKeyMapper_altStickyConsumptionOnSingleByte() {
        var alt = ModifierState.STICKY
        var sentBytes: ByteArray? = null

        val mapper = TerminalKeyMapper(
            getAltState = { alt },
            setAltState = { alt = it },
            sendRawBytes = { sentBytes = it },
        )

        mapper.sendToTerminal(byteArrayOf('a'.code.toByte()))

        assertEquals(ModifierState.INACTIVE, alt)
        assertArrayEquals(byteArrayOf(0x1B, 'a'.code.toByte()), sentBytes)
    }

    @Test
    fun testTerminalKeyMapper_altLockedPersistence() {
        var alt = ModifierState.LOCKED
        val sentList = mutableListOf<ByteArray>()

        val mapper = TerminalKeyMapper(
            getAltState = { alt },
            setAltState = { alt = it },
            sendRawBytes = { sentList.add(it) },
        )

        repeat(3) {
            mapper.sendToTerminal(byteArrayOf('z'.code.toByte()))
        }

        assertEquals(3, sentList.size)
        assertEquals(ModifierState.LOCKED, alt)
        for (bytes in sentList) {
            assertArrayEquals(byteArrayOf(0x1B, 'z'.code.toByte()), bytes)
        }
    }

    @Test
    fun testTerminalKeyMapper_altMultiByteNotEscaped() {
        var alt = ModifierState.STICKY
        var sentBytes: ByteArray? = null

        val mapper = TerminalKeyMapper(
            getAltState = { alt },
            setAltState = { alt = it },
            sendRawBytes = { sentBytes = it },
        )

        val input = byteArrayOf('a'.code.toByte(), 'b'.code.toByte())
        mapper.sendToTerminal(input)

        // Alt is NOT consumed and NOT prepended on multi-byte payloads
        assertEquals(ModifierState.STICKY, alt)
        assertArrayEquals(input, sentBytes)
    }

    @Test
    fun testTerminalKeyMapper_onExtraKeyToggleCycling() {
        var ctrl = ModifierState.INACTIVE
        var alt = ModifierState.INACTIVE
        var sup = ModifierState.INACTIVE
        var menu = ModifierState.INACTIVE

        val mapper = TerminalKeyMapper(
            getCtrlState = { ctrl },
            setCtrlState = { ctrl = it },
            getAltState = { alt },
            setAltState = { alt = it },
            getSuperState = { sup },
            setSuperState = { sup = it },
            getMenuState = { menu },
            setMenuState = { menu = it },
        )

        // Test cycle for each modifier: INACTIVE -> STICKY -> LOCKED -> INACTIVE
        val keys = listOf("Ctrl" to { ctrl }, "Alt" to { alt }, "Super" to { sup }, "Menu" to { menu })
        for ((key, getter) in keys) {
            assertEquals(ModifierState.INACTIVE, getter())
            mapper.onExtraKeyToggle(key)
            assertEquals(ModifierState.STICKY, getter())
            mapper.onExtraKeyToggle(key)
            assertEquals(ModifierState.LOCKED, getter())
            mapper.onExtraKeyToggle(key)
            assertEquals(ModifierState.INACTIVE, getter())
        }
    }

    @Test
    fun testTerminalKeyMapper_onCodePointStickyConsumption() {
        var ctrl = ModifierState.STICKY
        var sentBytes: ByteArray? = null

        val mapper = TerminalKeyMapper(
            getCtrlState = { ctrl },
            setCtrlState = { ctrl = it },
            sendRawBytes = { sentBytes = it },
        )

        mapper.onCodePoint('c'.code)

        assertEquals(ModifierState.INACTIVE, ctrl)
        assertArrayEquals(byteArrayOf(0x03), sentBytes)
    }

    @Test
    fun testTerminalKeyMapper_onCodePointLockedPersistence() {
        var ctrl = ModifierState.LOCKED
        var sentBytes: ByteArray? = null

        val mapper = TerminalKeyMapper(
            getCtrlState = { ctrl },
            setCtrlState = { ctrl = it },
            sendRawBytes = { sentBytes = it },
        )

        mapper.onCodePoint('c'.code)

        assertEquals(ModifierState.LOCKED, ctrl)
        assertArrayEquals(byteArrayOf(0x03), sentBytes)
    }
}
