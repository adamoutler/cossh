package com.adamoutler.ssh.crypto.fido2

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.security.MessageDigest

class OpenSshFido2WireParserTest {

    @Test
    fun ed25519PublicKeyBlob_roundtripsCorrectly() {
        val rawPublicBytes = ByteArray(32) { (it * 3).toByte() }
        val original = Fido2PublicKeyBlob.Ed25519(
            publicBytes = rawPublicBytes,
            application = "ssh:bastion.prod",
        )

        val wireBytes = OpenSshFido2WireParser.encodePublicKeyBlob(original)
        val parsed = OpenSshFido2WireParser.parsePublicKeyBlob(wireBytes)

        assertTrue(parsed is Fido2PublicKeyBlob.Ed25519)
        val ed = parsed as Fido2PublicKeyBlob.Ed25519
        assertEquals(OpenSshFido2WireParser.KEY_TYPE_SK_ED25519, ed.keyType)
        assertArrayEquals(rawPublicBytes, ed.publicBytes)
        assertEquals("ssh:bastion.prod", ed.application)
    }

    @Test
    fun ecdsaPublicKeyBlob_roundtripsCorrectly() {
        val uncompressedPoint = ByteArray(65) { 0x04 }
        uncompressedPoint[0] = 0x04 // Uncompressed point marker

        val original = Fido2PublicKeyBlob.EcdsaNistP256(
            curveName = OpenSshFido2WireParser.CURVE_NISTP256,
            publicPoint = uncompressedPoint,
            application = "ssh:gitlab.corp",
        )

        val wireBytes = OpenSshFido2WireParser.encodePublicKeyBlob(original)
        val parsed = OpenSshFido2WireParser.parsePublicKeyBlob(wireBytes)

        assertTrue(parsed is Fido2PublicKeyBlob.EcdsaNistP256)
        val ec = parsed as Fido2PublicKeyBlob.EcdsaNistP256
        assertEquals(OpenSshFido2WireParser.KEY_TYPE_SK_ECDSA, ec.keyType)
        assertEquals(OpenSshFido2WireParser.CURVE_NISTP256, ec.curveName)
        assertArrayEquals(uncompressedPoint, ec.publicPoint)
        assertEquals("ssh:gitlab.corp", ec.application)
    }

    @Test
    fun ed25519WireSignature_roundtripsCorrectly() {
        val rawSig = ByteArray(64) { (it * 7).toByte() }
        val flags = (OpenSshFido2WireParser.FLAG_USER_PRESENCE.toInt() or OpenSshFido2WireParser.FLAG_USER_VERIFIED.toInt()).toByte()
        val counter = 42000L

        val original = Fido2WireSignature.Ed25519(
            signature = rawSig,
            flags = flags,
            counter = counter,
        )

        val wireBytes = OpenSshFido2WireParser.serializeWireSignature(original)
        val parsed = OpenSshFido2WireParser.parseWireSignature(wireBytes)

        assertTrue(parsed is Fido2WireSignature.Ed25519)
        val edSig = parsed as Fido2WireSignature.Ed25519
        assertArrayEquals(rawSig, edSig.signature)
        assertEquals(flags, edSig.flags)
        assertEquals(counter, edSig.counter)
    }

    @Test
    fun ecdsaWireSignature_roundtripsCorrectly() {
        val r = BigInteger("1234567890123456789012345678901234567890")
        val s = BigInteger("9876543210987654321098765432109876543210")
        val flags = OpenSshFido2WireParser.FLAG_USER_PRESENCE
        val counter = 100L

        val original = Fido2WireSignature.EcdsaNistP256(
            r = r,
            s = s,
            flags = flags,
            counter = counter,
        )

        val wireBytes = OpenSshFido2WireParser.serializeWireSignature(original)
        val parsed = OpenSshFido2WireParser.parseWireSignature(wireBytes)

        assertTrue(parsed is Fido2WireSignature.EcdsaNistP256)
        val ecSig = parsed as Fido2WireSignature.EcdsaNistP256
        assertEquals(r, ecSig.r)
        assertEquals(s, ecSig.s)
        assertEquals(flags, ecSig.flags)
        assertEquals(counter, ecSig.counter)
    }

    @Test
    fun buildSignedDataBlob_constructsExact69BytesFormula() {
        val app = "ssh:my-server"
        val flags: Byte = 0x05
        val counter: Long = 1024L
        val message = "challenge-to-sign".toByteArray(Charsets.UTF_8)

        val blob = OpenSshFido2WireParser.buildSignedDataBlob(app, flags, counter, message)
        assertEquals(69, blob.size)

        // Verify sub-components
        val expectedAppHash = MessageDigest.getInstance("SHA-256").digest(app.toByteArray(Charsets.UTF_8))
        val expectedMsgHash = MessageDigest.getInstance("SHA-256").digest(message)

        val appHashInBlob = blob.copyOfRange(0, 32)
        val flagInBlob = blob[32]
        val counterInBlob = ((blob[33].toLong() and 0xFF) shl 24) or
            ((blob[34].toLong() and 0xFF) shl 16) or
            ((blob[35].toLong() and 0xFF) shl 8) or
            (blob[36].toLong() and 0xFF)
        val msgHashInBlob = blob.copyOfRange(37, 69)

        assertArrayEquals(expectedAppHash, appHashInBlob)
        assertEquals(flags, flagInBlob)
        assertEquals(counter, counterInBlob)
        assertArrayEquals(expectedMsgHash, msgHashInBlob)
    }

    @Test
    fun ecdsaDerAndRsConversion_roundtripsAccurately() {
        val r = BigInteger("112233445566778899aabbccddeeff0011223344", 16)
        val s = BigInteger("99887766554433221100ffeeddccbbaa99887766", 16)

        val derBytes = OpenSshFido2WireParser.encodeEcdsaRsToDer(r, s)
        assertTrue(derBytes.size > 0)

        val (extractedR, extractedS) = OpenSshFido2WireParser.extractEcdsaRs(derBytes)
        assertEquals(r, extractedR)
        assertEquals(s, extractedS)
    }

    @Test
    fun validateFlags_checksPresenceAndVerification() {
        val upOnly: Byte = 0x01
        val uvOnly: Byte = 0x04
        val upAndUv: Byte = 0x05
        val none: Byte = 0x00

        // Test UP required (default)
        assertTrue(OpenSshFido2WireParser.validateFlags(upOnly, requireUserPresence = true, requireUserVerification = false))
        assertTrue(OpenSshFido2WireParser.validateFlags(upAndUv, requireUserPresence = true, requireUserVerification = false))
        assertFalse(OpenSshFido2WireParser.validateFlags(none, requireUserPresence = true, requireUserVerification = false))
        assertFalse(OpenSshFido2WireParser.validateFlags(uvOnly, requireUserPresence = true, requireUserVerification = false))

        // Test UV required
        assertTrue(OpenSshFido2WireParser.validateFlags(upAndUv, requireUserPresence = true, requireUserVerification = true))
        assertFalse(OpenSshFido2WireParser.validateFlags(upOnly, requireUserPresence = true, requireUserVerification = true))
        assertTrue(OpenSshFido2WireParser.validateFlags(uvOnly, requireUserPresence = false, requireUserVerification = true))
    }
}
