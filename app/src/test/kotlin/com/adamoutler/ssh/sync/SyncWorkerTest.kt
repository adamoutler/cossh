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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncWorkerTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testDoWorkWithoutCloudSync_returnsSuccess() = runBlocking {
        // Without mocked BillingManager injecting enabled=true, it defaults to false
        val worker = TestListenableWorkerBuilder<SyncWorker>(context).build()
        val result = worker.doWork()
        assertEquals(Result.success(), result)
    }
}
