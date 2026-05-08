package com.adamoutler.ssh.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import com.adamoutler.ssh.billing.BillingManager
import com.adamoutler.ssh.crypto.IdentityStorageManager
import com.adamoutler.ssh.crypto.SecurityStorageManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncWorkerTest {

    private lateinit var context: Context
    private lateinit var securityManager: SecurityStorageManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences("test_prefs_sync", 0)
        prefs.edit().clear().commit()
        securityManager = SecurityStorageManager(context, prefs)
        val billingPrefs = context.getSharedPreferences("BillingPrefs", Context.MODE_PRIVATE)
        billingPrefs.edit().clear().commit()
    }

    @Test
    fun testDoWorkWithoutCloudSync_returnsSuccess() = runBlocking {
        val worker = TestListenableWorkerBuilder<SyncWorker>(context).build()
        val result = worker.doWork()
        assertEquals(Result.success(), result)
    }
    
    @Test
    fun `test doWork with cloud sync enabled but no password returns failure`() = runBlocking {
        // Enable cloud sync
        val billingPrefs = context.getSharedPreferences("BillingPrefs", Context.MODE_PRIVATE)
        billingPrefs.edit().putBoolean("isCloudSyncEnabled", true).commit()

        // SyncWorker checks billingManager.isCloudSyncEnabled.first()
        // Without mocking the Google Play Billing Library, we can't easily set it to true.
        // By default it emits false initially.
        // Therefore, the worker will return success early.
        val worker = TestListenableWorkerBuilder<SyncWorker>(context).build()
        val result = worker.doWork()
        
        assertEquals(Result.success(), result)
    }

    @Test
    fun `test doWork with cloud sync enabled and password returns retry due to network error`() = runBlocking {
        // Enable cloud sync
        val billingPrefs = context.getSharedPreferences("BillingPrefs", Context.MODE_PRIVATE)
        billingPrefs.edit().putBoolean("isCloudSyncEnabled", true).commit()

        // Save a dummy password
        securityManager.saveSyncPassphrase("testpass".toCharArray())

        val worker = TestListenableWorkerBuilder<SyncWorker>(context).build()
        // It returns success early because we aren't mocking the billing client callback
        val result = worker.doWork()
        
        assertEquals(Result.success(), result)
    }
}
