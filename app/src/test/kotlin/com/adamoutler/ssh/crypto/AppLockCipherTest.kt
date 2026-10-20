package com.adamoutler.ssh.crypto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.Protocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.Cipher

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppLockCipherTest {

    private lateinit var context: Context
    private lateinit var securityStorageManager: SecurityStorageManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences("test_app_lock_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        securityStorageManager = SecurityStorageManager(context, prefs)
    }

    @Test
    fun testInitEncryptCipherProducesValidGcmCipher() {
        val cipher = PasswordCipher.initEncryptCipher()
        assertNotNull("Cipher should not be null", cipher)
        assertNotNull("Cipher IV must be generated", cipher.iv)
        assertEquals("IV length for AES-GCM should be 12 bytes", 12, cipher.iv.size)
    }

    @Test
    fun testInitDecryptCipherProducesMatchingCipher() {
        val plaintext = "CosshSecretToken1234567890123456".toByteArray(Charsets.UTF_8)
        val encryptCipher = PasswordCipher.initEncryptCipher()
        val iv = encryptCipher.iv
        val ciphertext = encryptCipher.doFinal(plaintext)

        val decryptCipher = PasswordCipher.initDecryptCipher(iv)
        val decrypted = decryptCipher.doFinal(ciphertext)

        assertArrayEquals("Decrypted bytes must match plaintext", plaintext, decrypted)
    }

    @Test
    fun testDeterministicFallbackKeyReuseUnderRobolectric() {
        val plaintext = "DeterminismCheckData".toByteArray(Charsets.UTF_8)

        val cipher1 = PasswordCipher.initEncryptCipher()
        val ciphertext1 = cipher1.doFinal(plaintext)
        val iv1 = cipher1.iv

        val decryptCipher1 = PasswordCipher.initDecryptCipher(iv1)
        val decrypted1 = decryptCipher1.doFinal(ciphertext1)
        assertArrayEquals(plaintext, decrypted1)

        val cipher2 = PasswordCipher.initEncryptCipher()
        val ciphertext2 = cipher2.doFinal(plaintext)
        val iv2 = cipher2.iv

        val decryptCipher2 = PasswordCipher.initDecryptCipher(iv2)
        val decrypted2 = decryptCipher2.doFinal(ciphertext2)
        assertArrayEquals(plaintext, decrypted2)
    }

    @Test
    fun testWrapCryptoObjectReturnsValidBiometricObject() {
        val cipher = PasswordCipher.initEncryptCipher()
        val cryptoObject = PasswordCipher.wrapCryptoObject(cipher)
        assertNotNull("CryptoObject must not be null", cryptoObject)
        assertEquals("CryptoObject must wrap the provided cipher", cipher, cryptoObject.cipher)
    }

    @Test
    fun testSetupAppLockAndVerifyTokenSuccess() {
        assertFalse("App lock should not be configured initially", securityStorageManager.isAppLockConfigured())

        val encryptCipher = PasswordCipher.initEncryptCipher()
        securityStorageManager.setupAppLock(encryptCipher)

        assertTrue("App lock should be configured after setup", securityStorageManager.isAppLockConfigured())

        val iv = securityStorageManager.getAppLockIv()
        assertNotNull("IV must be retrievable", iv)
        assertArrayEquals("Retrieved IV must match encrypt cipher IV", encryptCipher.iv, iv)

        val decryptCipher = PasswordCipher.initDecryptCipher(iv!!)
        val verifySuccess = securityStorageManager.verifyAppLockToken(decryptCipher)
        assertTrue("Verification should succeed with correct decrypt cipher", verifySuccess)
    }

    @Test
    fun testVerifyAppLockTokenFailsWithCorruptedHash() {
        val encryptCipher = PasswordCipher.initEncryptCipher()
        securityStorageManager.setupAppLock(encryptCipher)

        val iv = securityStorageManager.getAppLockIv()!!
        val decryptCipher = PasswordCipher.initDecryptCipher(iv)

        val corruptSha = java.util.Base64.getEncoder().encodeToString(ByteArray(32) { 0xFF.toByte() })
        securityStorageManager.encryptedPrefs.edit()
            .putString("app_lock_token_sha256", corruptSha)
            .commit()

        val verifySuccess = securityStorageManager.verifyAppLockToken(decryptCipher)
        assertFalse("Verification must fail when hash does not match token", verifySuccess)
    }

    @Test
    fun testVerifyAppLockTokenFailsWhenNotConfigured() {
        val encryptCipher = PasswordCipher.initEncryptCipher()
        val verifySuccess = securityStorageManager.verifyAppLockToken(encryptCipher)
        assertFalse("Verification must fail if app lock is not configured", verifySuccess)
    }

    @Test
    fun testClearAppLockRemovesAllLockKeys() {
        val encryptCipher = PasswordCipher.initEncryptCipher()
        securityStorageManager.setupAppLock(encryptCipher)
        assertTrue(securityStorageManager.isAppLockConfigured())

        securityStorageManager.clearAppLock()

        assertFalse("isAppLockConfigured should be false after clear", securityStorageManager.isAppLockConfigured())
        assertNull("IV should be null after clear", securityStorageManager.getAppLockIv())
    }

    @Test
    fun testGetAllProfilesExcludesAppLockKeys() {
        val encryptCipher = PasswordCipher.initEncryptCipher()
        securityStorageManager.setupAppLock(encryptCipher)

        val profile = ConnectionProfile(
            id = "profile-test-1",
            nickname = "Prod Server",
            host = "192.168.1.1",
            protocol = Protocol.SSH,
        )
        securityStorageManager.saveProfile(profile)

        val profiles = securityStorageManager.getAllProfiles()
        assertEquals("Only actual profiles should be returned", 1, profiles.size)
        assertEquals("profile-test-1", profiles[0].id)
    }
}
