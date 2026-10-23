package com.adamoutler.ssh.network

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelnetNawsSecurityTest {

    @Test
    fun testDimensionSanitizationClamping() {
        // Valid bounds
        val (cols80, rows24) = TelnetNawsProtocol.sanitizeDimensions(80, 24)
        assertEquals(80, cols80)
        assertEquals(24, rows24)

        // Zero / negative falls back to default 80x24
        val (colsZero, rowsNeg) = TelnetNawsProtocol.sanitizeDimensions(0, -10)
        assertEquals(TelnetNawsProtocol.DEFAULT_COLS, colsZero)
        assertEquals(TelnetNawsProtocol.DEFAULT_ROWS, rowsNeg)

        // Upper limit clamped to 16-bit max (65535)
        val (colsOverflow, rowsOverflow) = TelnetNawsProtocol.sanitizeDimensions(100_000, 70_000)
        assertEquals(TelnetNawsProtocol.MAX_DIMENSION, colsOverflow)
        assertEquals(TelnetNawsProtocol.MAX_DIMENSION, rowsOverflow)

        // Lower limit clamped to 1
        val (colsMin, rowsMin) = TelnetNawsProtocol.sanitizeDimensions(1, 1)
        assertEquals(1, colsMin)
        assertEquals(1, rowsMin)
    }

    @Test
    fun testSubnegotiationPayloadStructure() {
        val payload = TelnetNawsProtocol.buildSubnegotiationPayload(80, 24)
        assertEquals(5, payload.size)
        assertEquals(TelnetNawsProtocol.OPTION_NAWS, payload[0])
        assertEquals(0, payload[1]) // 80 ushr 8
        assertEquals(80, payload[2]) // 80 and 0xFF
        assertEquals(0, payload[3]) // 24 ushr 8
        assertEquals(24, payload[4]) // 24 and 0xFF
    }

    @Test
    fun testRfc854MandatoryByteDoublingOnWire() {
        // Test standard 80x24 (no 0xFF bytes)
        // Expected wire: [IAC, SB, NAWS, 0, 80, 0, 24, IAC, SE]
        val standardFrame = TelnetNawsProtocol.buildWireFrame(80, 24)
        val expectedStandard = byteArrayOf(
            255.toByte(), // IAC
            250.toByte(), // SB
            31.toByte(),  // NAWS
            0.toByte(),
            80.toByte(),
            0.toByte(),
            24.toByte(),
            255.toByte(), // IAC
            240.toByte(), // SE
        )
        assertArrayEquals(expectedStandard, standardFrame)

        // Test width = 255 (0x00FF), height = 24 (0x0018)
        // Byte 255 MUST be doubled on the wire per RFC 854: [IAC, SB, NAWS, 0, 255, 255, 0, 24, IAC, SE]
        val singleDoubledFrame = TelnetNawsProtocol.buildWireFrame(255, 24)
        val expectedSingleDoubled = byteArrayOf(
            255.toByte(), // IAC
            250.toByte(), // SB
            31.toByte(),  // NAWS
            0.toByte(),
            255.toByte(), // Escaped byte
            255.toByte(), // Escaped byte duplicate
            0.toByte(),
            24.toByte(),
            255.toByte(), // IAC
            240.toByte(), // SE
        )
        assertArrayEquals(expectedSingleDoubled, singleDoubledFrame)

        // Test width = 65535 (0xFFFF), height = 65535 (0xFFFF)
        // All four dimension bytes are 255, so all four MUST be doubled: 4 bytes -> 8 bytes
        // Total frame size: 3 header + 8 escaped data + 2 trailer = 13 bytes
        val maxDoubledFrame = TelnetNawsProtocol.buildWireFrame(65535, 65535)
        assertEquals(13, maxDoubledFrame.size)
        val expectedMaxDoubled = byteArrayOf(
            255.toByte(), // IAC
            250.toByte(), // SB
            31.toByte(),  // NAWS
            255.toByte(), 255.toByte(), // Width high (255 doubled)
            255.toByte(), 255.toByte(), // Width low (255 doubled)
            255.toByte(), 255.toByte(), // Height high (255 doubled)
            255.toByte(), 255.toByte(), // Height low (255 doubled)
            255.toByte(), // IAC
            240.toByte(), // SE
        )
        assertArrayEquals(expectedMaxDoubled, maxDoubledFrame)
    }

    @Test
    fun testRfc1143QMethodLoopSuppression() {
        val sm = TelnetNawsProtocol.NawsNegotiationStateMachine()
        assertFalse(sm.isNegotiated)
        assertEquals(TelnetNawsProtocol.NawsNegotiationStateMachine.State.NO, sm.state)

        // First DO NAWS from server: accepts and transitions to YES
        val firstDo = sm.onReceiveDo()
        assertTrue("First DO must return true to send WILL", firstDo)
        assertTrue(sm.isNegotiated)
        assertEquals(TelnetNawsProtocol.NawsNegotiationStateMachine.State.YES, sm.state)

        // Duplicate DO NAWS while in YES: suppressed! MUST NOT send redundant WILL (prevents loop)
        val duplicateDo = sm.onReceiveDo()
        assertFalse("Duplicate DO must be suppressed to prevent loop", duplicateDo)
        assertTrue(sm.isNegotiated)

        // Triplicate DO NAWS: still suppressed
        val triplicateDo = sm.onReceiveDo()
        assertFalse("Triplicate DO must be suppressed", triplicateDo)

        // Remote peer sends DONT NAWS: transitions back to NO and emits WONT
        val firstDont = sm.onReceiveDont()
        assertTrue("First DONT must return true to send WONT", firstDont)
        assertFalse(sm.isNegotiated)
        assertEquals(TelnetNawsProtocol.NawsNegotiationStateMachine.State.NO, sm.state)

        // Duplicate DONT NAWS while in NO: suppressed
        val duplicateDont = sm.onReceiveDont()
        assertFalse("Duplicate DONT must be suppressed", duplicateDont)

        // Reset resets to initial State.NO
        sm.onReceiveDo()
        assertTrue(sm.isNegotiated)
        sm.reset()
        assertFalse(sm.isNegotiated)
        assertEquals(TelnetNawsProtocol.NawsNegotiationStateMachine.State.NO, sm.state)
    }
}
