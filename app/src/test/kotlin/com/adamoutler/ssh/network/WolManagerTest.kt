package com.adamoutler.ssh.network

import com.adamoutler.ssh.data.WolConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket

@OptIn(ExperimentalCoroutinesApi::class)
class WolManagerTest {

    @Test
    fun `isValidMacAddress accepts valid colon and hyphen separated MACs`() {
        assertTrue(WolManager.isValidMacAddress("00:11:22:33:44:55"))
        assertTrue(WolManager.isValidMacAddress("AA:BB:CC:DD:EE:FF"))
        assertTrue(WolManager.isValidMacAddress("aa:bb:cc:dd:ee:ff"))
        assertTrue(WolManager.isValidMacAddress("00-11-22-33-44-55"))
        assertTrue(WolManager.isValidMacAddress("AA-BB-CC-DD-EE-FF"))
        assertTrue(WolManager.isValidMacAddress("  00:11:22:33:44:55  "))
    }

    @Test
    fun `isValidMacAddress rejects invalid MAC formats`() {
        assertFalse(WolManager.isValidMacAddress(""))
        assertFalse(WolManager.isValidMacAddress("   "))
        assertFalse(WolManager.isValidMacAddress("00:11:22:33:44")) // only 5 octets
        assertFalse(WolManager.isValidMacAddress("00:11:22:33:44:55:66")) // 7 octets
        assertFalse(WolManager.isValidMacAddress("00:11:22:33:44:ZZ")) // non-hex
        assertFalse(WolManager.isValidMacAddress("0011.2233.4455")) // cisco format not supported
        assertFalse(WolManager.isValidMacAddress("00:11:22:33:44:5")) // 1-char octet
        assertFalse(WolManager.isValidMacAddress("00:11:22:33:44:555")) // 3-char octet
    }

    @Test
    fun `parseMacBytes correctly extracts 6 raw bytes`() {
        val mac = "00:1A:2B:3C:4D:5E"
        val bytes = WolManager.parseMacBytes(mac)
        assertEquals(6, bytes.size)
        assertEquals(0x00.toByte(), bytes[0])
        assertEquals(0x1A.toByte(), bytes[1])
        assertEquals(0x2B.toByte(), bytes[2])
        assertEquals(0x3C.toByte(), bytes[3])
        assertEquals(0x4D.toByte(), bytes[4])
        assertEquals(0x5E.toByte(), bytes[5])
    }

    @Test
    fun `parseMacBytes throws IllegalArgumentException for invalid MAC`() {
        try {
            WolManager.parseMacBytes("invalid-mac")
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected
        }
    }

    @Test
    fun `buildMagicPacket creates RFC conforming 102 byte payload`() {
        val macBytes = byteArrayOf(0x01, 0x23, 0x45, 0x67, 0x89.toByte(), 0xAB.toByte())
        val packet = WolManager.buildMagicPacket(macBytes)

        assertEquals(102, packet.size)

        // Verify 6 leading 0xFF bytes
        for (i in 0 until 6) {
            assertEquals(0xFF.toByte(), packet[i])
        }

        // Verify 16 repetitions of 6-byte target MAC
        for (rep in 0 until 16) {
            val offset = 6 + (rep * 6)
            val subArray = packet.copyOfRange(offset, offset + 6)
            assertArrayEquals("Repetition $rep mismatch", macBytes, subArray)
        }
    }

    @Test
    fun `sendMagicPacket transmits datagram successfully via injected socket`() = runTest {
        val config = WolConfig(
            macAddress = "AA:BB:CC:DD:EE:FF",
            broadcastIp = "127.0.0.1",
            port = 9,
            postWakeDelayMs = 0L,
        )

        var sentPacket: DatagramPacket? = null
        val fakeSocket = object : DatagramSocket() {
            override fun send(p: DatagramPacket?) {
                sentPacket = p
            }
        }

        val result = WolManager.sendMagicPacket(config, socketFactory = { fakeSocket })
        assertTrue(result)
        sentPacket?.let { p ->
            assertEquals(102, p.length)
            assertEquals(9, p.port)
            // Verify payload header
            assertEquals(0xFF.toByte(), p.data[0])
            assertEquals(0xFF.toByte(), p.data[5])
            // Verify payload MAC
            assertEquals(0xAA.toByte(), p.data[6])
            assertEquals(0xFF.toByte(), p.data[11])
        } ?: fail("Datagram was not sent")
    }

    @Test
    fun `sendMagicPacket propagates IO error cleanly`() = runTest {
        val config = WolConfig(
            macAddress = "00:11:22:33:44:55",
            broadcastIp = "127.0.0.1",
            port = 9,
            postWakeDelayMs = 0L,
        )

        val failingSocket = object : DatagramSocket() {
            override fun send(p: DatagramPacket?): Unit = throw IOException("Network unreachable")
        }

        try {
            WolManager.sendMagicPacket(config, socketFactory = { failingSocket })
            fail("Expected IOException")
        } catch (e: IOException) {
            assertEquals("Network unreachable", e.message)
        }
    }
}
