package com.adamoutler.ssh.sync

import android.app.Activity
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.*
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.lang.reflect.Method
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.net.URLStreamHandlerFactory
import javax.crypto.AEADBadTagException
import org.json.JSONObject

open class MockHttpURLConnection(url: URL) : HttpURLConnection(url) {
    var mockResponseCode = 200
    var mockResponseString = ""
    var requestMethodMock = ""
    var outputStreamMock = ByteArrayOutputStream()

    override fun setRequestMethod(method: String) {
        requestMethodMock = method
    }
    
    override fun connect() {}
    override fun disconnect() {}
    override fun usingProxy(): Boolean = false
    override fun getResponseCode(): Int = mockResponseCode
    override fun getInputStream(): InputStream {
        if (mockResponseCode >= 400) throw java.io.IOException("Error")
        return ByteArrayInputStream(mockResponseString.toByteArray())
    }
    override fun getErrorStream(): InputStream? {
        return ByteArrayInputStream("Error".toByteArray())
    }
    override fun getOutputStream(): java.io.OutputStream = outputStreamMock
}

object MockURLStreamHandlerFactory : URLStreamHandlerFactory {
    var mockConnection: MockHttpURLConnection? = null
    override fun createURLStreamHandler(protocol: String?): URLStreamHandler? {
        if (protocol == "https") {
            return object : URLStreamHandler() {
                override fun openConnection(u: URL?): URLConnection {
                    return mockConnection ?: MockHttpURLConnection(u!!)
                }
            }
        }
        return null
    }
}

@RunWith(RobolectricTestRunner::class)
class DriveSyncManagerTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setupFactory() {
            try {
                URL.setURLStreamHandlerFactory(MockURLStreamHandlerFactory)
            } catch (e: Error) {
                // Ignore if already set
            }
        }
    }

    private lateinit var context: Context
    private lateinit var driveSyncManager: DriveSyncManager
    private lateinit var mockConnection: MockHttpURLConnection

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        driveSyncManager = DriveSyncManager(context)
        DriveSyncManager.authorizationContinuation = null
        
        mockConnection = MockHttpURLConnection(URL("https://dummy"))
        MockURLStreamHandlerFactory.mockConnection = mockConnection
    }

    @Test
    fun testEncryptDecrypt() {
        val encryptMethod: Method = DriveSyncManager::class.java.getDeclaredMethod("encrypt", ByteArray::class.java, CharArray::class.java)
        encryptMethod.isAccessible = true

        val decryptMethod: Method = DriveSyncManager::class.java.getDeclaredMethod("decrypt", ByteArray::class.java, CharArray::class.java)
        decryptMethod.isAccessible = true

        val payload = "SuperSecretData".toByteArray()
        val pass = "myPassword123".toCharArray()

        val encrypted = encryptMethod.invoke(driveSyncManager, payload, pass) as ByteArray
        assertNotNull(encrypted)
        assertTrue(encrypted.size > payload.size)

        val decrypted = decryptMethod.invoke(driveSyncManager, encrypted, pass) as ByteArray
        assertArrayEquals(payload, decrypted)
    }

    @Test
    fun testSetOAuthToken() {
        val token = "test_token"
        driveSyncManager.setOAuthToken(token)

        val oauthTokenField = DriveSyncManager::class.java.getDeclaredField("oauthToken")
        oauthTokenField.isAccessible = true
        assertEquals(token, oauthTokenField.get(driveSyncManager))
    }

    @Test
    fun testHandleAuthorizationResult_invalidCode() {
        DriveSyncManager.handleAuthorizationResult(999, 0, null)
    }

    @Test
    fun testHandleAuthorizationResult_validCode_cancelled() {
        var exceptionThrown = false
        DriveSyncManager.authorizationContinuation = object : kotlin.coroutines.Continuation<Unit> {
            override val context = kotlin.coroutines.EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) {
                if (result.isFailure) exceptionThrown = true
            }
        }
        DriveSyncManager.handleAuthorizationResult(1001, Activity.RESULT_CANCELED, null)
        assertTrue(exceptionThrown)
    }

    @Test
    fun testHandleAuthorizationResult_validCode_ok() {
        var exceptionThrown = false
        DriveSyncManager.authorizationContinuation = object : kotlin.coroutines.Continuation<Unit> {
            override val context = kotlin.coroutines.EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) {
                if (result.isFailure) exceptionThrown = true
            }
        }
        DriveSyncManager.handleAuthorizationResult(1001, Activity.RESULT_OK, android.content.Intent())
        assertTrue(exceptionThrown)
    }

    @Test
    fun testUploadBackupSuccess() = runBlocking {
        driveSyncManager.setOAuthToken("valid_token")
        
        // Mock finding file ID (returns empty list -> null fileId -> will create new file)
        mockConnection.mockResponseCode = 200
        mockConnection.mockResponseString = JSONObject().put("id", "newFileId").put("files", org.json.JSONArray()).toString()
        
        // Will create new file and then update metadata
        driveSyncManager.uploadBackup("testData".toByteArray(), "password".toCharArray())
        
        // Ensure token was cleared
        val oauthTokenField = DriveSyncManager::class.java.getDeclaredField("oauthToken")
        oauthTokenField.isAccessible = true
        assertNull(oauthTokenField.get(driveSyncManager))
    }
    
    @Test
    fun testUploadBackupSuccessWithExistingFile() = runBlocking {
        driveSyncManager.setOAuthToken("valid_token")
        
        // Mock finding file ID (returns a file)
        val filesArray = org.json.JSONArray().put(JSONObject().put("id", "existingFileId"))
        mockConnection.mockResponseString = JSONObject().put("files", filesArray).toString()
        
        driveSyncManager.uploadBackup("testData".toByteArray(), "password".toCharArray())
    }

    @Test
    fun testUploadBackupFailure() = runBlocking {
        driveSyncManager.setOAuthToken("valid_token")
        mockConnection.mockResponseCode = 401 // Unauthorized
        
        var exceptionThrown = false
        try {
            driveSyncManager.uploadBackup("testData".toByteArray(), "password".toCharArray())
        } catch (e: Exception) {
            exceptionThrown = true
        }
        assertTrue("Expected exception on HTTP error", exceptionThrown)
    }

    @Test
    fun testDownloadBackupSuccess() = runBlocking {
        driveSyncManager.setOAuthToken("valid_token")
        
        // Pre-encrypt some payload so download parsing passes
        val encryptMethod = DriveSyncManager::class.java.getDeclaredMethod("encrypt", ByteArray::class.java, CharArray::class.java)
        encryptMethod.isAccessible = true
        val encryptedData = encryptMethod.invoke(driveSyncManager, "payload".toByteArray(), "password".toCharArray()) as ByteArray
        
        // Mock finding file ID and downloading
        val filesArray = org.json.JSONArray().put(JSONObject().put("id", "existingFileId"))
        mockConnection.mockResponseString = JSONObject().put("files", filesArray).toString()
        
        // For the second request (the download itself), we need to return the encrypted bytes
        // But our mock connection just returns mockResponseString. We can convert bytes to string.
        mockConnection.mockResponseString = String(encryptedData, Charsets.ISO_8859_1)
        
        MockURLStreamHandlerFactory.mockConnection = object : MockHttpURLConnection(URL("https://dummy")) {
            var callCount = 0
            override fun getInputStream(): InputStream {
                callCount++
                if (callCount == 1) {
                    return ByteArrayInputStream(JSONObject().put("files", filesArray).toString().toByteArray())
                }
                return ByteArrayInputStream(encryptedData)
            }
        }
        
        val result = driveSyncManager.downloadBackup("password".toCharArray())
        assertNotNull(result)
        assertArrayEquals("payload".toByteArray(), result)
    }

    @Test
    fun testDownloadBackupFileNotFound() = runBlocking {
        driveSyncManager.setOAuthToken("valid_token")
        
        // Return empty files list
        mockConnection.mockResponseString = JSONObject().put("files", org.json.JSONArray()).toString()
        
        val result = driveSyncManager.downloadBackup("password".toCharArray())
        assertNull(result)
    }

    @Test
    fun testDownloadBackupHttpError() = runBlocking {
        driveSyncManager.setOAuthToken("valid_token")
        
        // finding file ID works
        val filesArray = org.json.JSONArray().put(JSONObject().put("id", "existingFileId"))
        
        MockURLStreamHandlerFactory.mockConnection = object : MockHttpURLConnection(URL("https://dummy")) {
            var callCount = 0
            override fun getResponseCode(): Int {
                return if (callCount == 0) 200 else 500
            }
            override fun getInputStream(): InputStream {
                callCount++
                if (callCount == 1) {
                    return ByteArrayInputStream(JSONObject().put("files", filesArray).toString().toByteArray())
                }
                throw java.io.IOException("Error")
            }
        }
        
        val result = driveSyncManager.downloadBackup("password".toCharArray())
        assertNull(result)
    }
}
