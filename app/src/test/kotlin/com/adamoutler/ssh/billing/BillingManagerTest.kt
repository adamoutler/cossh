package com.adamoutler.ssh.billing

import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BillingManagerTest {

    @Test
    fun testProcessPurchases_UnlockCloudSync() {
        val context = RuntimeEnvironment.getApplication()
        val billingManager = BillingManager(context)
        
        assertFalse(billingManager.isCloudSyncEnabled.value)
        
        // Construct a Purchase using JSON
        val purchaseJson = """
            {
              "orderId": "GPA.1234-5678-9012-34567",
              "packageName": "com.adamoutler.ssh",
              "productId": "lifetimecloudsync",
              "purchaseTime": 1620000000000,
              "purchaseState": 0,
              "purchaseToken": "token",
              "acknowledged": true
            }
        """.trimIndent()
        
        val purchase = Purchase(purchaseJson, "signature")
        
        val billingResult = BillingResult.newBuilder()
            .setResponseCode(BillingClient.BillingResponseCode.OK)
            .build()
        
        billingManager.onPurchasesUpdated(billingResult, mutableListOf(purchase))
        
        println("TEST-METRIC: BillingManager processed PURCHASED event. isCloudSyncEnabled = ${billingManager.isCloudSyncEnabled.value}")
        assertTrue(billingManager.isCloudSyncEnabled.value)
    }
}
