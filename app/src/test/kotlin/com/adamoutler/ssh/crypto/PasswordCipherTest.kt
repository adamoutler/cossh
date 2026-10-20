package com.adamoutler.ssh.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class PasswordCipherTest {

    @Test
    fun `test encrypt and decrypt roundtrip`() {
        val originalText = "TopSecretPassword!123".toByteArray(Charsets.UTF_8)

        val encrypted = PasswordCipher.encrypt(originalText)

        // Ensure it's actually encrypted
        assertFalse(encrypted.contentEquals(originalText))

        val decrypted = PasswordCipher.decrypt(encrypted)

        assertArrayEquals(originalText, decrypted)
    }

    @Test(expected = Exception::class)
    fun `test decrypt with invalid data throws exception`() {
        // Should throw exception
        PasswordCipher.decrypt(ByteArray(10) { it.toByte() })
    }

    @Test
    fun `test initEncryptCipher and initDecryptCipher directly`() {
        val encryptCipher = PasswordCipher.initEncryptCipher()
        assertNotNull(encryptCipher)
        val iv = encryptCipher.iv
        assertNotNull(iv)

        val decryptCipher = PasswordCipher.initDecryptCipher(iv)
        assertNotNull(decryptCipher)

        val plaintext = "DirectCipherTestPayload".toByteArray(Charsets.UTF_8)
        val ciphertext = encryptCipher.doFinal(plaintext)
        val recovered = decryptCipher.doFinal(ciphertext)
        assertArrayEquals(plaintext, recovered)
    }

    @Test
    fun `test wrapCryptoObject on API 28+`() {
        val encryptCipher = PasswordCipher.initEncryptCipher()
        val cryptoObject = PasswordCipher.wrapCryptoObject(encryptCipher)
        assertNotNull(cryptoObject)
        assertEquals(encryptCipher, cryptoObject.cipher)
    }

    @Test
    fun `test resetFallbackKeyForTesting clears state`() {
        PasswordCipher.resetFallbackKeyForTesting()
        val c1 = PasswordCipher.initEncryptCipher()
        assertNotNull(c1)
        PasswordCipher.resetFallbackKeyForTesting()
        val c2 = PasswordCipher.initEncryptCipher()
        assertNotNull(c2)
    }
}
