package com.adamoutler.ssh.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
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
}
