package com.adamoutler.ssh.billing

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class BillingManagerCoverageTest {

    @Test
    fun testOnPurchasesUpdated_VariousCodes() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val manager = BillingManager(app)

        val resCanceled = BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.USER_CANCELED).build()
        manager.onPurchasesUpdated(resCanceled, null)

        val resOwned = BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED).build()
        manager.onPurchasesUpdated(resOwned, null)

        val resError = BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.ERROR).build()
        manager.onPurchasesUpdated(resError, null)
    }

    @Test
    fun testAwaitCloudSyncEntitlement_WithForcedTestFlag() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val manager = BillingManager(app)

        BillingManager.forceCloudSyncEnabledForTest = true
        assertTrue(manager.awaitCloudSyncEntitlement())

        BillingManager.forceCloudSyncEnabledForTest = false
        // Under Robolectric without stored pref, returns false
        assertFalse(manager.awaitCloudSyncEntitlement())
    }
}
