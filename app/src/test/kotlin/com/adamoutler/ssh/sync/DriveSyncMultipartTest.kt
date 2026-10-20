package com.adamoutler.ssh.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.lang.reflect.Method
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DriveSyncMultipartTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setupFactory() {
            try {
                URL.setURLStreamHandlerFactory(MockURLStreamHandlerFactory)
            } catch (_: Error) {
                // Ignore if already registered
            }
        }
    }

    private lateinit var context: Context
    private lateinit var driveSyncManager: DriveSyncManager

    class DetailedMockConnection(url: URL) : MockHttpURLConnection(url) {
        val requestHeaders = mutableMapOf<String, String>()
        val outputBytes = ByteArrayOutputStream()
        var responseBody: ByteArray = ByteArray(0)

        override fun setRequestMethod(method: String) {
            super.setRequestMethod(method)
            requestMethodMock = method
        }

        override fun setRequestProperty(key: String, value: String) {
            super.setRequestProperty(key, value)
            requestHeaders[key] = value
        }

        override fun getOutputStream(): java.io.OutputStream = outputBytes

        override fun getInputStream(): InputStream {
            if (mockResponseCode >= 400) throw java.io.IOException("HTTP $mockResponseCode")
            return ByteArrayInputStream(responseBody)
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        driveSyncManager = DriveSyncManager(context)
        driveSyncManager.setOAuthToken("test_bearer_token")
    }

    @After
    fun tearDown() {
        MockURLStreamHandlerFactory.connectionProvider = null
        MockURLStreamHandlerFactory.mockConnection = null
        DriveSyncManager.cachedOAuthToken = null
    }

    @Test
    fun testUploadBackup_CreatesFileViaMultipartPost() = runBlocking {
        val requestedConnections = mutableListOf<DetailedMockConnection>()

        MockURLStreamHandlerFactory.connectionProvider = { url ->
            val conn = DetailedMockConnection(url)
            requestedConnections.add(conn)
            val urlStr = url.toString()
            if (urlStr.contains("spaces=appDataFolder")) {
                conn.mockResponseCode = 200
                conn.responseBody = JSONObject().put("files", org.json.JSONArray()).toString().toByteArray(StandardCharsets.UTF_8)
            } else if (urlStr.contains("uploadType=multipart")) {
                conn.mockResponseCode = 200
                conn.responseBody = JSONObject().put("id", "newMultipartFileId").toString().toByteArray(StandardCharsets.UTF_8)
            }
            conn
        }

        val testPayload = "FakeEncryptedZipPayload".toByteArray(StandardCharsets.UTF_8)
        val pass = "mySyncPassword".toCharArray()

        driveSyncManager.uploadBackup(testPayload, pass)

        assertEquals("Expected exactly 2 HTTP requests (GET find, POST multipart)", 2, requestedConnections.size)

        val getReq = requestedConnections[0]
        assertEquals("GET", getReq.requestMethodMock)
        assertTrue(getReq.url.toString().contains("spaces=appDataFolder"))

        val postReq = requestedConnections[1]
        assertEquals("POST", postReq.requestMethodMock)
        assertTrue("URL must contain uploadType=multipart", postReq.url.toString().contains("uploadType=multipart"))
        assertEquals("Bearer test_bearer_token", postReq.requestHeaders["Authorization"])

        val contentType = postReq.requestHeaders["Content-Type"] ?: ""
        assertTrue("Content-Type must be multipart/related", contentType.startsWith("multipart/related; boundary="))

        val requestBody = postReq.outputBytes.toString(StandardCharsets.UTF_8.name())
        assertTrue("Body must include metadata JSON with cossh_backup.enc", requestBody.contains("cossh_backup.enc"))
        assertTrue("Body must target appDataFolder", requestBody.contains("appDataFolder"))
        assertTrue("Body must include application/octet-stream", requestBody.contains("application/octet-stream"))
        assertTrue("Body must contain payload", requestBody.contains("FakeEncryptedZipPayload"))
    }

    @Test
    fun testUploadBackup_UpdatesExistingFileViaMediaPatch() = runBlocking {
        val requestedConnections = mutableListOf<DetailedMockConnection>()

        MockURLStreamHandlerFactory.connectionProvider = { url ->
            val conn = DetailedMockConnection(url)
            requestedConnections.add(conn)
            val urlStr = url.toString()
            if (urlStr.contains("spaces=appDataFolder")) {
                val files = org.json.JSONArray().put(JSONObject().put("id", "existingFileId123"))
                conn.mockResponseCode = 200
                conn.responseBody = JSONObject().put("files", files).toString().toByteArray(StandardCharsets.UTF_8)
            } else if (urlStr.contains("uploadType=media")) {
                conn.mockResponseCode = 200
                conn.responseBody = JSONObject().put("id", "existingFileId123").toString().toByteArray(StandardCharsets.UTF_8)
            }
            conn
        }

        val testPayload = "UpdatedPayloadBytes".toByteArray(StandardCharsets.UTF_8)
        val pass = "mySyncPassword".toCharArray()

        driveSyncManager.uploadBackup(testPayload, pass)

        assertEquals(2, requestedConnections.size)
        val patchReq = requestedConnections[1]
        assertEquals("PATCH", patchReq.requestMethodMock)
        assertTrue("URL must target existing file ID", patchReq.url.toString().contains("/files/existingFileId123?uploadType=media"))
        assertEquals("application/octet-stream", patchReq.requestHeaders["Content-Type"])
        assertEquals("Bearer test_bearer_token", patchReq.requestHeaders["Authorization"])
        assertArrayEquals(testPayload, patchReq.outputBytes.toByteArray())
    }

    @Test
    fun testDeleteBackup_ExistingFile_ReturnsTrue() = runBlocking {
        val requestedConnections = mutableListOf<DetailedMockConnection>()

        MockURLStreamHandlerFactory.connectionProvider = { url ->
            val conn = DetailedMockConnection(url)
            requestedConnections.add(conn)
            val urlStr = url.toString()
            if (urlStr.contains("spaces=appDataFolder")) {
                val files = org.json.JSONArray().put(JSONObject().put("id", "fileToDelete999"))
                conn.mockResponseCode = 200
                conn.responseBody = JSONObject().put("files", files).toString().toByteArray(StandardCharsets.UTF_8)
            } else if (urlStr.contains("/files/fileToDelete999")) {
                conn.mockResponseCode = 204
            }
            conn
        }

        val result = driveSyncManager.deleteBackup()
        assertTrue("deleteBackup should succeed", result)

        assertEquals(2, requestedConnections.size)
        val deleteReq = requestedConnections[1]
        assertEquals("DELETE", deleteReq.requestMethodMock)
        assertEquals("Bearer test_bearer_token", deleteReq.requestHeaders["Authorization"])
    }

    @Test
    fun testDeleteBackup_MissingFile_ReturnsTrue() = runBlocking {
        val requestedConnections = mutableListOf<DetailedMockConnection>()

        MockURLStreamHandlerFactory.connectionProvider = { url ->
            val conn = DetailedMockConnection(url)
            requestedConnections.add(conn)
            conn.mockResponseCode = 200
            conn.responseBody = JSONObject().put("files", org.json.JSONArray()).toString().toByteArray(StandardCharsets.UTF_8)
            conn
        }

        val result = driveSyncManager.deleteBackup()
        assertTrue("deleteBackup should return true when no backup exists", result)
        assertEquals("Only GET findBackupFileId should be called", 1, requestedConnections.size)
    }

    @Test
    fun testDownloadBackup_ZipPayload_ReturnsRawWithoutDoubleDecrypt() = runBlocking {
        // Raw ZIP header: "PK\x03\x04" followed by sample bytes
        val zipHeader = byteArrayOf(0x50.toByte(), 0x4B.toByte(), 0x03.toByte(), 0x04.toByte(), 0x14.toByte(), 0x00.toByte())

        MockURLStreamHandlerFactory.connectionProvider = { url ->
            val conn = DetailedMockConnection(url)
            val urlStr = url.toString()
            if (urlStr.contains("spaces=appDataFolder")) {
                val files = org.json.JSONArray().put(JSONObject().put("id", "zipBackupId"))
                conn.mockResponseCode = 200
                conn.responseBody = JSONObject().put("files", files).toString().toByteArray(StandardCharsets.UTF_8)
            } else {
                conn.mockResponseCode = 200
                conn.responseBody = zipHeader
            }
            conn
        }

        val downloaded = driveSyncManager.downloadBackup("anyPass".toCharArray())
        assertNotNull(downloaded)
        assertArrayEquals(zipHeader, downloaded)
    }

    @Test
    fun testDownloadBackup_LegacyEncryptedPayload_InvokesDecryptFallback() = runBlocking {
        val encryptMethod: Method = DriveSyncManager::class.java.getDeclaredMethod("encrypt", ByteArray::class.java, CharArray::class.java)
        encryptMethod.isAccessible = true

        val plaintextZip = byteArrayOf(0x50.toByte(), 0x4B.toByte(), 0x03.toByte(), 0x04.toByte(), 0x99.toByte())
        val pass = "legacySecret123".toCharArray()
        val legacyEncrypted = encryptMethod.invoke(driveSyncManager, plaintextZip, pass) as ByteArray

        MockURLStreamHandlerFactory.connectionProvider = { url ->
            val conn = DetailedMockConnection(url)
            val urlStr = url.toString()
            if (urlStr.contains("spaces=appDataFolder")) {
                val files = org.json.JSONArray().put(JSONObject().put("id", "legacyBackupId"))
                conn.mockResponseCode = 200
                conn.responseBody = JSONObject().put("files", files).toString().toByteArray(StandardCharsets.UTF_8)
            } else {
                conn.mockResponseCode = 200
                conn.responseBody = legacyEncrypted
            }
            conn
        }

        val decrypted = driveSyncManager.downloadBackup("legacySecret123".toCharArray())
        assertNotNull(decrypted)
        assertArrayEquals(plaintextZip, decrypted)
    }

    @Test
    fun testOAuthTokenPreservedAcrossCalls() = runBlocking {
        MockURLStreamHandlerFactory.connectionProvider = { url ->
            val conn = DetailedMockConnection(url)
            val urlStr = url.toString()
            if (urlStr.contains("spaces=appDataFolder")) {
                conn.mockResponseCode = 200
                conn.responseBody = JSONObject().put("files", org.json.JSONArray()).toString().toByteArray(StandardCharsets.UTF_8)
            } else if (urlStr.contains("uploadType=multipart")) {
                conn.mockResponseCode = 200
                conn.responseBody = JSONObject().put("id", "newId").toString().toByteArray(StandardCharsets.UTF_8)
            }
            conn
        }

        driveSyncManager.uploadBackup("Data".toByteArray(), "pass".toCharArray())
        assertEquals("test_bearer_token", driveSyncManager.oauthToken)

        // Download call
        MockURLStreamHandlerFactory.connectionProvider = { url ->
            val conn = DetailedMockConnection(url)
            val urlStr = url.toString()
            if (urlStr.contains("spaces=appDataFolder")) {
                val files = org.json.JSONArray().put(JSONObject().put("id", "newId"))
                conn.mockResponseCode = 200
                conn.responseBody = JSONObject().put("files", files).toString().toByteArray(StandardCharsets.UTF_8)
            } else {
                conn.mockResponseCode = 200
                conn.responseBody = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
            }
            conn
        }

        driveSyncManager.downloadBackup("pass".toCharArray())
        assertEquals("test_bearer_token", driveSyncManager.oauthToken)
    }
}
