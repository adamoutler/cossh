package com.adamoutler.ssh.crypto

import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.SecureRandom

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class PasswordCipherSecurityTest {

    @Test
    fun verifyTruncatedCiphertextBoundaries_ThrowsCryptoException() {
        val truncatedLengths = listOf(0, 1, 5, 11, 12, 13, 20, 27)

        for (len in truncatedLengths) {
            val invalidPayload = ByteArray(len) { 0xAA.toByte() }
            try {
                PasswordCipher.decrypt(invalidPayload)
                fail("Decryption MUST fail for truncated payload of length: $len")
            } catch (e: Exception) {
                assertTrue(
                    "Expected CryptoException for length $len but got ${e.javaClass.name}",
                    e is CryptoException || e is ArrayIndexOutOfBoundsException || e is IllegalArgumentException,
                )
            }
        }
    }

    @Test
    fun verifyCiphertextBitFlip_FailsAuthentication() {
        val plaintext = "StrictSecurePassword#2026".toByteArray(Charsets.UTF_8)
        val encrypted = PasswordCipher.encrypt(plaintext)

        // Flip bit in IV (bytes 0..11)
        val tamperedIv = encrypted.clone()
        tamperedIv[0] = (tamperedIv[0].toInt() xor 0x01).toByte()
        assertDecryptionFails(tamperedIv, "Tampered IV must fail GCM auth")

        // Flip bit in Ciphertext Body (bytes 12..size-17)
        val tamperedBody = encrypted.clone()
        tamperedBody[12] = (tamperedBody[12].toInt() xor 0x01).toByte()
        assertDecryptionFails(tamperedBody, "Tampered ciphertext payload must fail GCM auth")

        // Flip bit in GCM Auth Tag (last 16 bytes)
        val tamperedTag = encrypted.clone()
        tamperedTag[tamperedTag.size - 1] = (tamperedTag[tamperedTag.size - 1].toInt() xor 0x01).toByte()
        assertDecryptionFails(tamperedTag, "Tampered GCM tag must fail GCM auth")
    }

    private fun assertDecryptionFails(tampered: ByteArray, message: String) {
        try {
            PasswordCipher.decrypt(tampered)
            fail(message)
        } catch (e: Exception) {
            assertTrue("Expected CryptoException but got ${e.javaClass.name}", e is CryptoException)
        }
    }

    @Test
    fun verifyNonceFreshness_NoIVCollisions() {
        val plaintext = "ConstantSensitivePasswordPayload".toByteArray(Charsets.UTF_8)
        val sampleSize = 100
        val seenIvs = HashSet<String>()
        val seenCiphertexts = HashSet<String>()

        repeat(sampleSize) {
            val encrypted = PasswordCipher.encrypt(plaintext)
            val iv = encrypted.copyOfRange(0, 12).joinToString("") { "%02x".format(it) }
            val fullHex = encrypted.joinToString("") { "%02x".format(it) }

            assertFalse("CRITICAL: IV collision detected! Nonce reused: $iv", seenIvs.contains(iv))
            assertFalse("CRITICAL: Ciphertext collision detected!", seenCiphertexts.contains(fullHex))

            seenIvs.add(iv)
            seenCiphertexts.add(fullHex)
        }
        assertEquals(sampleSize, seenIvs.size)
    }

    @Test
    fun verifyEmptyPlaintext_RoundtripsSuccessfully() {
        val emptyPlaintext = ByteArray(0)
        val encrypted = PasswordCipher.encrypt(emptyPlaintext)

        // 12 bytes IV + 16 bytes GCM Tag = 28 bytes
        assertEquals(28, encrypted.size)

        val decrypted = PasswordCipher.decrypt(encrypted)
        assertEquals(0, decrypted.size)
        assertArrayEquals(emptyPlaintext, decrypted)
    }

    @Test
    fun verifyArbitraryPayloadSizes_RoundtripsSuccessfully() {
        val testSizes = listOf(1, 15, 16, 17, 31, 32, 33, 255, 1024)
        val rng = SecureRandom()

        for (size in testSizes) {
            val payload = ByteArray(size)
            rng.nextBytes(payload)

            val encrypted = PasswordCipher.encrypt(payload)
            assertEquals(12 + size + 16, encrypted.size)

            val decrypted = PasswordCipher.decrypt(encrypted)
            assertArrayEquals("Failed roundtrip for size $size", payload, decrypted)
        }
    }

    @Test
    fun verifyKeystoreExceptionHandling_KeyPermanentlyInvalidated() {
        val cause = KeyPermanentlyInvalidatedException()
        try {
            cause.handleKeystoreExceptions("Default test message")
        } catch (e: KeyInvalidatedException) {
            assertEquals("Keystore key permanently invalidated. Storage must be wiped.", e.message)
            assertTrue(e.cause === cause)
        }
    }

    @Test
    fun verifyKeystoreExceptionHandling_UserNotAuthenticated() {
        val cause = UserNotAuthenticatedException()
        try {
            cause.handleKeystoreExceptions("Default test message")
        } catch (e: AuthenticationRequiredException) {
            assertEquals("User authentication required to access Keystore.", e.message)
            assertTrue(e.cause === cause)
        }
    }

    @Test
    fun verifyKeystoreExceptionHandling_GenericSecureStorageUnavailable() {
        val cause = IllegalStateException("Generic hardware error")
        try {
            cause.handleKeystoreExceptions("Storage unavailable message")
        } catch (e: SecureStorageUnavailableException) {
            assertEquals("Storage unavailable message", e.message)
            assertTrue(e.cause === cause)
        }
    }

    @Test
    fun verifyIsCausedByHelper() {
        val root = IllegalArgumentException("Root cause")
        val mid = RuntimeException("Mid cause", root)
        val top = Exception("Top cause", mid)

        assertTrue(top.isCausedBy(IllegalArgumentException::class.java))
        assertTrue(top.isCausedBy(RuntimeException::class.java))
        assertTrue(top.isCausedBy(Exception::class.java))
        assertFalse(top.isCausedBy(KeyPermanentlyInvalidatedException::class.java))
    }
}
