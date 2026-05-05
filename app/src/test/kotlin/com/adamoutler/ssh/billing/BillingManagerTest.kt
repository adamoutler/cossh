package com.adamoutler.ssh.billing

import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.lang.reflect.Field

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

        assertTrue(billingManager.isCloudSyncEnabled.value)
    }

    @Test
    fun testProcessPurchases_UnacknowledgedPurchase() {
        val context = RuntimeEnvironment.getApplication()
        val billingManager = BillingManager(context)

        assertFalse(billingManager.isCloudSyncEnabled.value)

        val purchaseJson = """
            {
              "orderId": "GPA.1234-5678-9012-34567",
              "packageName": "com.adamoutler.ssh",
              "productId": "lifetimecloudsync",
              "purchaseTime": 1620000000000,
              "purchaseState": 0,
              "purchaseToken": "token",
              "acknowledged": false
            }
        """.trimIndent()

        val purchase = Purchase(purchaseJson, "signature")

        val billingResult = BillingResult.newBuilder()
            .setResponseCode(BillingClient.BillingResponseCode.OK)
            .build()

        billingManager.onPurchasesUpdated(billingResult, mutableListOf(purchase))
        
        // Should trigger acknowledgePurchase. Without a mock billing client, 
        // it may just log or fail to acknowledge. We test if it survives.
    }

    @Test
    fun testOnPurchasesUpdated_Error() {
        val context = RuntimeEnvironment.getApplication()
        val billingManager = BillingManager(context)

        val billingResult = BillingResult.newBuilder()
            .setResponseCode(BillingClient.BillingResponseCode.ERROR)
            .build()

        billingManager.onPurchasesUpdated(billingResult, null)
        assertFalse(billingManager.isCloudSyncEnabled.value)
    }

    @Test
    fun testPurchaseCloudSync() {
        val context = RuntimeEnvironment.getApplication()
        val billingManager = BillingManager(context)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        // Just calling it to hit the code path and ensure it doesn't crash 
        // before interacting with BillingClient.
        billingManager.purchaseCloudSync(activity)
    }
}
