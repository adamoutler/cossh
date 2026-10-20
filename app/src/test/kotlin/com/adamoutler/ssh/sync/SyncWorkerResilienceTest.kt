package com.adamoutler.ssh.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import com.adamoutler.ssh.billing.BillingManager
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.crypto.SettingsManager
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URL
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncWorkerResilienceTest {

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
    private lateinit var securityManager: SecurityStorageManager
    private lateinit var settingsManager: SettingsManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        securityManager = SecurityStorageManager(context)
        settingsManager = SettingsManager(context)

        securityManager.clearSyncPassphrase()
        settingsManager.isCloudSyncActive = true
        BillingManager.forceCloudSyncEnabledForTest = true
        DriveSyncManager.cachedOAuthToken = "valid_token"
    }

    @After
    fun tearDown() {
        MockURLStreamHandlerFactory.connectionProvider = null
        MockURLStreamHandlerFactory.mockConnection = null
        BillingManager.forceCloudSyncEnabledForTest = false
        DriveSyncManager.cachedOAuthToken = null
    }

    @Test
    fun testDoWork_SyncDisabledInSettings_ReturnsSuccess() = runBlocking {
        settingsManager.isCloudSyncActive = false
        securityManager.saveSyncPassphrase("testPassword".toCharArray())

        val worker = TestListenableWorkerBuilder<SyncWorker>(context).build()
        val result = worker.doWork()

        assertEquals(Result.success(), result)
    }

    @Test
    fun testDoWork_NoBillingEntitlement_ReturnsSuccess() = runBlocking {
        BillingManager.forceCloudSyncEnabledForTest = false
        context.getSharedPreferences("BillingPrefs", Context.MODE_PRIVATE).edit().clear().commit()
        securityManager.saveSyncPassphrase("testPassword".toCharArray())

        val worker = TestListenableWorkerBuilder<SyncWorker>(context).build()
        val result = worker.doWork()

        assertEquals(Result.success(), result)
    }

    @Test
    fun testDoWork_NullPassphrase_ReturnsFailureImmediately() = runBlocking {
        securityManager.clearSyncPassphrase()

        val worker = TestListenableWorkerBuilder<SyncWorker>(context).build()
        val result = worker.doWork()

        assertEquals(Result.failure(), result)
    }

    @Test
    fun testDoWork_Unauthenticated_ReturnsFailureImmediately() = runBlocking {
        DriveSyncManager.cachedOAuthToken = null
        securityManager.saveSyncPassphrase("testPassword".toCharArray())

        val worker = TestListenableWorkerBuilder<SyncWorker>(context).build()
        val result = worker.doWork()

        assertEquals(Result.failure(), result)
    }

    @Test
    fun testDoWork_Http401or403_ReturnsFailureImmediately() = runBlocking {
        securityManager.saveSyncPassphrase("testPassword".toCharArray())

        MockURLStreamHandlerFactory.connectionProvider = { url ->
            val conn = DriveSyncMultipartTest.DetailedMockConnection(url)
            conn.mockResponseCode = 401
            conn.responseBody = "{\"error\": \"Unauthorized 401\"}".toByteArray(StandardCharsets.UTF_8)
            conn
        }

        val worker = TestListenableWorkerBuilder<SyncWorker>(context).build()
        val result = worker.doWork()

        assertEquals(Result.failure(), result)
    }

    @Test
    fun testDoWork_TransientNetworkError_Under3Attempts_ReturnsRetry() = runBlocking {
        securityManager.saveSyncPassphrase("testPassword".toCharArray())

        MockURLStreamHandlerFactory.connectionProvider = { url ->
            val conn = DriveSyncMultipartTest.DetailedMockConnection(url)
            val urlStr = url.toString()
            if (urlStr.contains("spaces=appDataFolder")) {
                conn.mockResponseCode = 200
                conn.responseBody = JSONObject().put("files", org.json.JSONArray()).toString().toByteArray(StandardCharsets.UTF_8)
            } else {
                conn.mockResponseCode = 503
                conn.responseBody = "Service Unavailable".toByteArray(StandardCharsets.UTF_8)
            }
            conn
        }

        val worker = TestListenableWorkerBuilder<SyncWorker>(context)
            .setRunAttemptCount(1)
            .build()
        val result = worker.doWork()

        assertEquals(Result.retry(), result)
    }

    @Test
    fun testDoWork_TransientNetworkError_At3Attempts_ReturnsFailure() = runBlocking {
        securityManager.saveSyncPassphrase("testPassword".toCharArray())

        MockURLStreamHandlerFactory.connectionProvider = { url ->
            val conn = DriveSyncMultipartTest.DetailedMockConnection(url)
            conn.mockResponseCode = 500
            conn.responseBody = "Internal Server Error".toByteArray(StandardCharsets.UTF_8)
            conn
        }

        val worker = TestListenableWorkerBuilder<SyncWorker>(context)
            .setRunAttemptCount(3)
            .build()
        val result = worker.doWork()

        assertEquals(Result.failure(), result)
    }
}
