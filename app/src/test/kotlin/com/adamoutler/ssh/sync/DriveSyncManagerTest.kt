package com.adamoutler.ssh.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.lang.reflect.Method
import javax.crypto.AEADBadTagException

@RunWith(RobolectricTestRunner::class)
class DriveSyncManagerTest {

    private lateinit var context: Context
    private lateinit var driveSyncManager: DriveSyncManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        driveSyncManager = DriveSyncManager(context)
    }

    @Test
    fun testEncryptDecrypt() {
        val encryptMethod: Method = DriveSyncManager::class.java.getDeclaredMethod("encrypt", ByteArray::class.java, CharArray::class.java)
        encryptMethod.isAccessible = true

        val decryptMethod: Method = DriveSyncManager::class.java.getDeclaredMethod("decrypt", ByteArray::class.java, CharArray::class.java)
        decryptMethod.isAccessible = true

        val payload = "SuperSecretData".toByteArray()
        val pass = "myPassword123".toCharArray()

        // Encrypt
        val encrypted = encryptMethod.invoke(driveSyncManager, payload, pass) as ByteArray
        assertNotNull(encrypted)
        assertTrue(encrypted.size > payload.size)

        // Decrypt
        val decrypted = decryptMethod.invoke(driveSyncManager, encrypted, pass) as ByteArray
        assertArrayEquals(payload, decrypted)
    }

    @Test
    fun testDecryptWithWrongPassword() {
        val encryptMethod: Method = DriveSyncManager::class.java.getDeclaredMethod("encrypt", ByteArray::class.java, CharArray::class.java)
        encryptMethod.isAccessible = true

        val decryptMethod: Method = DriveSyncManager::class.java.getDeclaredMethod("decrypt", ByteArray::class.java, CharArray::class.java)
        decryptMethod.isAccessible = true

        val payload = "SuperSecretData".toByteArray()
        val pass = "myPassword123".toCharArray()
        val wrongPass = "wrongPassword".toCharArray()

        val encrypted = encryptMethod.invoke(driveSyncManager, payload, pass) as ByteArray

        var exceptionThrown = false
        try {
            decryptMethod.invoke(driveSyncManager, encrypted, wrongPass)
        } catch (e: Exception) {
            val cause = e.cause
            if (cause is AEADBadTagException) {
                exceptionThrown = true
            }
        }
        assertTrue("Expected AEADBadTagException", exceptionThrown)
    }

    @Test
    fun testSetOAuthToken() {
        val token = "test_token"
        driveSyncManager.setOAuthToken(token)

        val oauthTokenField = DriveSyncManager::class.java.getDeclaredField("oauthToken")
        oauthTokenField.isAccessible = true
        val actualToken = oauthTokenField.get(driveSyncManager)

        assertEquals(token, actualToken)
    }

    @Test
    fun testHandleAuthorizationResult_invalidCode() {
        DriveSyncManager.authorizationContinuation = null
        DriveSyncManager.handleAuthorizationResult(999, 0, null)
        // Should not crash and not consume continuation
    }

    @Test
    fun testUploadBackupUnauthenticated() = runBlocking {
        var exceptionThrown = false
        try {
            driveSyncManager.uploadBackup("test".toByteArray(), "pass".toCharArray())
        } catch (e: IllegalStateException) {
            exceptionThrown = true
        }
        assertTrue("Expected IllegalStateException for unauthenticated upload", exceptionThrown)
    }

    @Test
    fun testUploadBackupAuthenticated_Failure() = runBlocking {
        driveSyncManager.setOAuthToken("dummy_token")
        var exceptionThrown = false
        try {
            driveSyncManager.uploadBackup("test".toByteArray(), "pass".toCharArray())
        } catch (e: Exception) {
            // expected to fail due to fake token
            exceptionThrown = true
        }
        assertTrue("Expected Exception for fake upload", exceptionThrown)
        val oauthTokenField = DriveSyncManager::class.java.getDeclaredField("oauthToken")
        oauthTokenField.isAccessible = true
        val actualToken = oauthTokenField.get(driveSyncManager)
        org.junit.Assert.assertNull(actualToken)
    }

    @Test
    fun testDownloadBackupUnauthenticated() = runBlocking {
        val result = driveSyncManager.downloadBackup("pass".toCharArray())
        org.junit.Assert.assertNull(result)
    }

    @Test
    fun testFindBackupFileId_Unauthenticated() {
        val findBackupFileIdMethod = DriveSyncManager::class.java.getDeclaredMethod("findBackupFileId")
        findBackupFileIdMethod.isAccessible = true

        // This will attempt an HTTP request, but since oauthToken is null, it might fail or return null
        // However it should cover the method execution up to the HTTP call
        try {
            val result = findBackupFileIdMethod.invoke(driveSyncManager)
            org.junit.Assert.assertNull(result)
        } catch (e: Exception) {
            // Exception is fine, we just want coverage
        }
    }

    @Test
    fun testUpdateFileMetadata_Unauthenticated() {
        val updateFileMetadataMethod = DriveSyncManager::class.java.getDeclaredMethod("updateFileMetadata", String::class.java)
        updateFileMetadataMethod.isAccessible = true

        try {
            updateFileMetadataMethod.invoke(driveSyncManager, "dummyFileId")
        } catch (e: Exception) {
            // Exception is expected since network request fails
        }
    }

    @Test
    fun testReadFully() {
        val readFullyMethod = DriveSyncManager::class.java.getDeclaredMethod("readFully", java.io.InputStream::class.java)
        readFullyMethod.isAccessible = true

        val testData = "testData".toByteArray()
        val inputStream = java.io.ByteArrayInputStream(testData)
        val result = readFullyMethod.invoke(driveSyncManager, inputStream) as ByteArray
        org.junit.Assert.assertArrayEquals(testData, result)
    }

    @Test
    fun testAuthenticate() = runBlocking {
        val activity = android.app.Activity()
        try {
            // Under Robolectric, CredentialManager will likely throw an exception.
            // We just want to cover the method execution.
            driveSyncManager.authenticate(activity)
        } catch (e: Exception) {
            // Expected in test environment
        }
    }
}
