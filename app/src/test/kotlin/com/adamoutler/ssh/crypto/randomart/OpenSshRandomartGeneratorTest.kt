package com.adamoutler.ssh.crypto.randomart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class OpenSshRandomartGeneratorTest {

    @Test
    fun testAuthenticOpenSshReferenceVector() {
        // Reference SHA-256 digest from actual OpenSSH ssh-keygen -l -v
        val rawB64 = "laXJsfauX7yG+AMf6xZS2TSpapz6X2FPGmPfV7osLFw="
        val digest = Base64.getDecoder().decode(rawB64)

        val expected = listOf(
            "+--[ED25519 256]--+",
            "|          . .  . |",
            "|         . B  +  |",
            "|          O  = . |",
            "|         o .+ .  |",
            "|        S. +. * o|",
            "|          B.E+ Xo|",
            "|         + B.=* =|",
            "|        . +.O+.o.|",
            "|         .oB=++  |",
            "+----[SHA256]-----+",
        ).joinToString("\n")

        val actual = OpenSshRandomartGenerator.generate(
            rawDigest = digest,
            keyTypeTitle = "ED25519 256",
            hashAlgorithmTitle = "SHA256",
        )

        assertEquals(expected, actual)
    }

    @Test
    fun testAllZerosDigest() {
        // With all zeros:
        // bit 0 = 0 -> dx = -1
        // bit 1 = 0 -> dy = -1
        // The bishop continuously moves North-West toward (0, 0)
        val digest = ByteArray(32) { 0 }
        val result = OpenSshRandomartGenerator.generate(digest, "RSA 2048", "SHA256")

        val lines = result.split("\n")
        assertEquals(11, lines.size)
        assertEquals("+---[RSA 2048]----+", lines.first())
        assertEquals("+----[SHA256]-----+", lines.last())

        // Start 'S' must be at center row index 4 + 1 = line 5, col 8 + 1 = char index 9
        assertEquals('S', lines[5][9])
        // End 'E' must be at (0, 0) -> line 1, char index 1
        assertEquals('E', lines[1][1])
    }

    @Test
    fun testAllOnesDigest() {
        // With all 0xFF:
        // bit 0 = 1 -> dx = +1
        // bit 1 = 1 -> dy = +1
        // The bishop continuously moves South-East toward (16, 8)
        val digest = ByteArray(32) { 0xFF.toByte() }
        val result = OpenSshRandomartGenerator.generate(digest, "ED25519", "SHA256")

        val lines = result.split("\n")
        assertEquals(11, lines.size)
        // End 'E' must be at (16, 8) -> line 9, char index 17
        assertEquals('E', lines[9][17])
    }

    @Test
    fun testEndOverwritesStartWhenFinishingAtCenter() {
        // Byte 0: 0x00 -> moves NW: (8, 4) -> (7, 3)
        // Byte 1: 0x03 -> moves SE: (7, 3) -> (8, 4)
        // Repeat to return to center
        // 0b00 is NW, 0b11 is SE
        val moves = byteArrayOf(0b00000000.toByte(), 0b11111111.toByte())
        val result = OpenSshRandomartGenerator.generate(moves, "TEST", "TEST")
        val lines = result.split("\n")

        // Center position is line 5, col 9. Since end is at (8, 4), 'E' should be present
        assertEquals('E', lines[5][9])
    }

    @Test
    fun testGenerateFromPublicKeyBytes() {
        val dummyPubKey = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGV4YW1wbGVwdWJsaWNrZXlkYXRhZm9ydGVzdA==".toByteArray()
        val randomartSha256 = OpenSshRandomartGenerator.generateFromPublicKeyBytes(dummyPubKey, "ED25519 256", useSha256 = true)
        val linesSha = randomartSha256.split("\n")

        assertEquals(11, linesSha.size)
        assertTrue(linesSha.first().contains("ED25519 256"))
        assertTrue(linesSha.last().contains("SHA256"))

        val randomartMd5 = OpenSshRandomartGenerator.generateFromPublicKeyBytes(dummyPubKey, "ED25519 256", useSha256 = false)
        val linesMd5 = randomartMd5.split("\n")
        assertTrue(linesMd5.last().contains("MD5"))
    }

    @Test
    fun testLongTitleFormattingGracefullyHandled() {
        val longTitle = "VERY_LONG_KEY_ALGORITHM_NAME_EXCEEDING_LIMIT"
        val digest = ByteArray(16)
        val art = OpenSshRandomartGenerator.generate(digest, longTitle, "SHA256")
        val header = art.split("\n").first()

        // Line width must always be exactly 19 characters (+ 17 chars +)
        assertEquals(19, header.length)
        assertTrue(header.startsWith("+"))
        assertTrue(header.endsWith("+"))
    }
}
