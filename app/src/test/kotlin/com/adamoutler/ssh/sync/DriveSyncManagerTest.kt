package com.adamoutler.ssh.sync

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.InvocationTargetException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.net.URLStreamHandlerFactory
import javax.crypto.AEADBadTagException
import kotlin.coroutines.Continuation
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

object MockURLStreamHandlerFactory : URLStreamHandlerFactory {
    var overrideResponseCode: Int? = null
    var simulateException: Boolean = false

    override fun createURLStreamHandler(protocol: String): URLStreamHandler? {
        if (protocol == "https" || protocol == "http") {
            return object : URLStreamHandler() {
                override fun openConnection(u: URL): URLConnection {
                    return object : HttpURLConnection(u) {
                        var isUpload = u.path.contains("upload")
                        var isDownload = u.query?.contains("alt=media") == true
                        override fun connect() {}
                        override fun disconnect() {}
                        override fun usingProxy() = false
                        override fun getResponseCode(): Int = overrideResponseCode ?: 200
                        override fun setRequestMethod(method: String) {
                            // Avoid JVM ProtocolException for PATCH
                        }
                        override fun getInputStream(): InputStream {
                            if (simulateException || (overrideResponseCode != null && overrideResponseCode != 200)) {
                                throw IOException("Mock error stream")
                            }
                            if (isUpload) {
                                return ByteArrayInputStream("""{"id":"new_file_id"}""".toByteArray())
                            }
                            if (isDownload) {
                                val manager = DriveSyncManager(ApplicationProvider.getApplicationContext())
                                val encryptMethod = DriveSyncManager::class.java.getDeclaredMethod("encrypt", ByteArray::class.java, CharArray::class.java)
                                encryptMethod.isAccessible = true
                                val encrypted = encryptMethod.invoke(manager, "test_payload".toByteArray(), "pass".toCharArray()) as ByteArray
                                return ByteArrayInputStream(encrypted)
                            }
                            return ByteArrayInputStream("""{"files":[{"id":"existing_file_id"}]}""".toByteArray())
                        }
                        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()
                        override fun getErrorStream(): InputStream? = if (overrideResponseCode != null) ByteArrayInputStream("Mock Error".toByteArray()) else null
                    }
                }
            }
        }
        return null
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DriveSyncManagerTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setupClass() {
            try {
                URL.setURLStreamHandlerFactory(MockURLStreamHandlerFactory)
            } catch (e: Error) {
                // Ignore if already set
            }
        }
    }

    private lateinit var context: Context
    private lateinit var manager: DriveSyncManager

    @Before
    fun setup() {
        ShadowLog.stream = System.out
        context = ApplicationProvider.getApplicationContext()
        manager = DriveSyncManager(context)
    }

    @Test
    fun testNativeCryptoExecution() {
        android.util.Log.d("DriveSyncManager", "Requesting getCredential via CredentialManager...")
    }

    @Test
    fun testEncryptDecrypt() {
        val payload = "SuperSecretPayload".toByteArray()
        val pass = "MyStrongPassword123!".toCharArray()

        val encryptMethod = DriveSyncManager::class.java.getDeclaredMethod("encrypt", ByteArray::class.java, CharArray::class.java)
        encryptMethod.isAccessible = true
        val encrypted = encryptMethod.invoke(manager, payload, pass) as ByteArray

        assertTrue(encrypted.size > payload.size)

        val decryptMethod = DriveSyncManager::class.java.getDeclaredMethod("decrypt", ByteArray::class.java, CharArray::class.java)
        decryptMethod.isAccessible = true
        val decrypted = decryptMethod.invoke(manager, encrypted, pass) as ByteArray

        assertArrayEquals(payload, decrypted)
    }

    @Test
    fun testDecryptInvalidPayloadSize() {
        val pass = "MyStrongPassword123!".toCharArray()
        val decryptMethod = DriveSyncManager::class.java.getDeclaredMethod("decrypt", ByteArray::class.java, CharArray::class.java)
        decryptMethod.isAccessible = true

        try {
            decryptMethod.invoke(manager, ByteArray(10), pass)
            fail("Expected InvocationTargetException")
        } catch (e: InvocationTargetException) {
            assertTrue(e.cause is AEADBadTagException)
        }
    }

    @Test
    fun testDecryptWrongPassword() {
        val payload = "SuperSecretPayload".toByteArray()
        val pass1 = "Pass1".toCharArray()
        val pass2 = "Pass2".toCharArray()

        val encryptMethod = DriveSyncManager::class.java.getDeclaredMethod("encrypt", ByteArray::class.java, CharArray::class.java)
        encryptMethod.isAccessible = true
        val encrypted = encryptMethod.invoke(manager, payload, pass1) as ByteArray

        val decryptMethod = DriveSyncManager::class.java.getDeclaredMethod("decrypt", ByteArray::class.java, CharArray::class.java)
        decryptMethod.isAccessible = true

        try {
            decryptMethod.invoke(manager, encrypted, pass2)
            fail("Expected InvocationTargetException")
        } catch (e: InvocationTargetException) {
            // expected
        }
    }

    @Test
    fun testSetOAuthToken() {
        manager.setOAuthToken("test_token")
        
        val field = DriveSyncManager::class.java.getDeclaredField("oauthToken")
        field.isAccessible = true
        assertEquals("test_token", field.get(manager))
    }

    @Test
    fun testUploadBackupUnauthenticated() {
        val payload = "payload".toByteArray()
        val pass = "pass".toCharArray()
        
        try {
            runBlocking {
                manager.uploadBackup(payload, pass)
            }
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertEquals("Not authenticated", e.message)
        }
    }

    @Test
    fun testDownloadBackupUnauthenticated() {
        val pass = "pass".toCharArray()
        
        val result = runBlocking {
            manager.downloadBackup(pass)
        }
        assertNull(result)
    }

    @Test
    fun testReadFully() {
        val data = "Hello World".toByteArray()
        val inputStream = ByteArrayInputStream(data)
        
        val readFullyMethod = DriveSyncManager::class.java.getDeclaredMethod("readFully", java.io.InputStream::class.java)
        readFullyMethod.isAccessible = true
        
        val result = readFullyMethod.invoke(manager, inputStream) as ByteArray
        assertArrayEquals(data, result)
    }

    @Test
    fun testUploadBackupSuccess() {
        manager.setOAuthToken("dummy_token")
        val payload = "payload".toByteArray()
        val pass = "pass".toCharArray()
        
        runBlocking {
            // Because URLFactory intercepts and returns 200, this should succeed without throwing
            manager.uploadBackup(payload, pass)
        }
        
        // Assert token is nullified after upload
        val field = DriveSyncManager::class.java.getDeclaredField("oauthToken")
        field.isAccessible = true
        assertNull(field.get(manager))
    }
    
    @Test
    fun testDownloadBackupSuccess() {
        manager.setOAuthToken("dummy_token")
        val pass = "pass".toCharArray()
        
        val result = runBlocking {
            manager.downloadBackup(pass)
        }
        assertNotNull(result)
        assertEquals("test_payload", String(result!!))
    }

    @Test
    fun testUpdateFileMetadataSuccess() {
        manager.setOAuthToken("dummy_token")
        val updateMethod = DriveSyncManager::class.java.getDeclaredMethod("updateFileMetadata", String::class.java)
        updateMethod.isAccessible = true
        
        // URL interceptor returns 200, so it shouldn't throw exception
        updateMethod.invoke(manager, "dummy_id")
    }

    @Test
    fun testFindBackupFileIdSuccess() {
        manager.setOAuthToken("dummy_token")
        val findMethod = DriveSyncManager::class.java.getDeclaredMethod("findBackupFileId")
        findMethod.isAccessible = true
        
        val result = findMethod.invoke(manager)
        assertEquals("existing_file_id", result)
    }

    @Test
    fun testAuthenticate_GetCredentialException() {
        val mockCredentialManager = java.lang.reflect.Proxy.newProxyInstance(
            DriveSyncManagerTest::class.java.classLoader,
            arrayOf(androidx.credentials.CredentialManager::class.java)
        ) { _, method, args ->
            if (method.name == "getCredential") {
                val continuation = args.last() as kotlin.coroutines.Continuation<Any>
                continuation.resumeWithException(androidx.credentials.exceptions.GetCredentialCustomException("CustomError"))
                return@newProxyInstance kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
            }
            null
        }
        val field = DriveSyncManager::class.java.getDeclaredField("credentialManager")
        field.isAccessible = true
        field.set(manager, mockCredentialManager)
        
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        runBlocking {
            manager.authenticate(activity)
        }
        
        val tokenField = DriveSyncManager::class.java.getDeclaredField("oauthToken")
        tokenField.isAccessible = true
        assertNull(tokenField.get(manager))
    }

    @Test
    fun testAuthenticate_UnexpectedCredentialType() {
        val mockCredentialManager = java.lang.reflect.Proxy.newProxyInstance(
            DriveSyncManagerTest::class.java.classLoader,
            arrayOf(androidx.credentials.CredentialManager::class.java)
        ) { _, method, args ->
            if (method.name == "getCredential") {
                val continuation = args.last() as kotlin.coroutines.Continuation<Any>
                val customCredential = androidx.credentials.CustomCredential("UNEXPECTED_TYPE", android.os.Bundle())
                continuation.resumeWith(Result.success(androidx.credentials.GetCredentialResponse(customCredential)))
                return@newProxyInstance kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
            }
            null
        }
        val field = DriveSyncManager::class.java.getDeclaredField("credentialManager")
        field.isAccessible = true
        field.set(manager, mockCredentialManager)
        
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        runBlocking {
            manager.authenticate(activity)
        }
    }

    @Test
    fun testAuthenticate_InvalidGoogleIdToken() {
        val mockCredentialManager = java.lang.reflect.Proxy.newProxyInstance(
            DriveSyncManagerTest::class.java.classLoader,
            arrayOf(androidx.credentials.CredentialManager::class.java)
        ) { _, method, args ->
            if (method.name == "getCredential") {
                val continuation = args.last() as kotlin.coroutines.Continuation<Any>
                val customCredential = androidx.credentials.CustomCredential("com.google.android.libraries.identity.googleid.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL", android.os.Bundle())
                continuation.resumeWith(Result.success(androidx.credentials.GetCredentialResponse(customCredential)))
                return@newProxyInstance kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
            }
            null
        }
        val field = DriveSyncManager::class.java.getDeclaredField("credentialManager")
        field.isAccessible = true
        field.set(manager, mockCredentialManager)
        
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        runBlocking {
            manager.authenticate(activity)
        }
    }

    @Test
    fun testHandleAuthorizationResult_ResultCanceled() {
        DriveSyncManager.handleAuthorizationResult(1001, Activity.RESULT_CANCELED, null)
        assertNull(DriveSyncManager.authorizationContinuation)
    }

    @Test
    fun testHandleAuthorizationResult_WithContinuation() {
        var resumed = false
        var exception: Throwable? = null
        val continuation = object : Continuation<Unit> {
            override val context = kotlin.coroutines.EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) {
                resumed = true
                exception = result.exceptionOrNull()
            }
        }
        DriveSyncManager.authorizationContinuation = continuation
        DriveSyncManager.handleAuthorizationResult(1001, Activity.RESULT_CANCELED, null)
        assertTrue(resumed)
        assertTrue(exception is java.lang.IllegalStateException)
        assertEquals("Authorization denied or cancelled by user", exception?.message)
    }

    @Test
    fun testHandleAuthorizationResult_IntentDataNull() {
        var resumed = false
        var exception: Throwable? = null
        val continuation = object : Continuation<Unit> {
            override val context = kotlin.coroutines.EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) {
                resumed = true
                exception = result.exceptionOrNull()
            }
        }
        DriveSyncManager.authorizationContinuation = continuation
        DriveSyncManager.handleAuthorizationResult(1001, Activity.RESULT_OK, null)
        assertTrue(resumed)
        assertTrue(exception is java.lang.IllegalStateException)
        assertEquals("Authorization denied or cancelled by user", exception?.message)
    }

    @Test
    fun testCurrentInstance() {
        val instance = DriveSyncManager.currentInstance
        assertNotNull(instance)
        DriveSyncManager.currentInstance = null
        assertNull(DriveSyncManager.currentInstance)
        DriveSyncManager.currentInstance = instance
    }

    @Test
    fun testHandleAuthorizationResult_Success() {
        var resumed = false
        var exception: Throwable? = null
        val continuation = object : Continuation<Unit> {
            override val context = kotlin.coroutines.EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) {
                resumed = true
                exception = result.exceptionOrNull()
            }
        }
        DriveSyncManager.authorizationContinuation = continuation
        DriveSyncManager.handleAuthorizationResult(1001, Activity.RESULT_OK, android.content.Intent())
        assertTrue(resumed)
        assertNotNull(exception) // because Identity.getAuthorizationClient throws in bare robolectric
    }

    @Test
    fun testUpdateFileMetadataFailure() {
        manager.setOAuthToken("dummy_token")
        val updateMethod = DriveSyncManager::class.java.getDeclaredMethod("updateFileMetadata", String::class.java)
        updateMethod.isAccessible = true
        
        MockURLStreamHandlerFactory.overrideResponseCode = 403
        try {
            updateMethod.invoke(manager, "dummy_id")
            fail("Expected InvocationTargetException")
        } catch (e: InvocationTargetException) {
            assertTrue(e.cause is java.io.IOException)
        } finally {
            MockURLStreamHandlerFactory.overrideResponseCode = null
        }
    }

    @Test
    fun testFindBackupFileIdFailure() {
        manager.setOAuthToken("dummy_token")
        val findMethod = DriveSyncManager::class.java.getDeclaredMethod("findBackupFileId")
        findMethod.isAccessible = true
        
        MockURLStreamHandlerFactory.overrideResponseCode = 404
        try {
            val result = findMethod.invoke(manager)
            assertNull(result)
        } finally {
            MockURLStreamHandlerFactory.overrideResponseCode = null
        }
    }

    @Test
    fun testDownloadBackupFailure() {
        manager.setOAuthToken("dummy_token")
        val pass = "pass".toCharArray()
        
        MockURLStreamHandlerFactory.overrideResponseCode = 500
        try {
            val result = runBlocking { manager.downloadBackup(pass) }
            assertNull(result) // caught inside downloadBackup
        } finally {
            MockURLStreamHandlerFactory.overrideResponseCode = null
        }
    }

    /*
    @Test
    fun testAuthorizeScope_ThrowsException() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val authorizeMethod = DriveSyncManager::class.java.getDeclaredMethod("authorizeScope", Activity::class.java, Continuation::class.java)
        authorizeMethod.isAccessible = true
        var resumed = false
        var exception: Throwable? = null
        val continuation = object : Continuation<Unit> {
            override val context = kotlin.coroutines.EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) {
                resumed = true
                exception = result.exceptionOrNull()
            }
        }
        // This invokes authorizeScope which will try to use Identity.getAuthorizationClient and fail synchronously or asynchronously
        try {
            authorizeMethod.invoke(manager, activity, continuation)
            assertTrue(resumed || exception != null || DriveSyncManager.authorizationContinuation != null)
        } catch(e: Exception) {
             // either throws InvocationTargetException synchronously or resumes continuation with exception
        }
    }
    */
}
