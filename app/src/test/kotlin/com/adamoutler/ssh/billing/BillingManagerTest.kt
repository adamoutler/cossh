package com.adamoutler.ssh.billing

import android.app.Activity
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BillingManagerTest {

    private lateinit var context: Context
    private lateinit var billingManager: BillingManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        billingManager = BillingManager(context)
    }

    @Test
    fun testInitialState() = runBlocking {
        // By default it should be false, unless forceCloudSyncEnabledForTest is true
        BillingManager.forceCloudSyncEnabledForTest = false
        val newBillingManager = BillingManager(context)
        val isEnabled = newBillingManager.isCloudSyncEnabled.first()
        assertEquals(false, isEnabled)
    }

    @Test
    fun testForceCloudSyncEnabled() = runBlocking {
        BillingManager.forceCloudSyncEnabledForTest = true
        val newBillingManager = BillingManager(context)
        val isEnabled = newBillingManager.isCloudSyncEnabled.first()
        assertEquals(true, isEnabled)
        BillingManager.forceCloudSyncEnabledForTest = false // Reset
    }

    @Test
    fun testOnPurchasesUpdated() {
        // We test the callback manually
        val billingResult = BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.OK).build()
        // Also test with null purchases
        billingManager.onPurchasesUpdated(billingResult, null)
    }

    @Test
    fun testPurchaseCloudSync() {
        val activity = Activity()
        billingManager.purchaseCloudSync(activity)
        // Should query product details but since billing client isn't connected/mocked, it will just not crash
    }

    @Test
    fun testOnPurchasesUpdated_withValidPurchase() = runBlocking {
        val billingResult = BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.OK).build()
        
        val purchaseJson = """
            {
                "productId": "lifetimecloudsync",
                "productIds": ["lifetimecloudsync"],
                "purchaseState": 0,
                "acknowledged": true,
                "purchaseToken": "mock_token"
            }
        """.trimIndent()
        val purchase = com.android.billingclient.api.Purchase(purchaseJson, "mock_signature")
        
        val newBillingManager = BillingManager(context)
        newBillingManager.onPurchasesUpdated(billingResult, mutableListOf(purchase))
        
        val isEnabled = newBillingManager.isCloudSyncEnabled.first()
        assertEquals(true, isEnabled)
    }

    @Test
    fun testOnPurchasesUpdated_withUnacknowledgedPurchase() = runBlocking {
        val billingResult = BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.OK).build()
        
        val purchaseJson = """
            {
                "productId": "lifetimecloudsync",
                "productIds": ["lifetimecloudsync"],
                "purchaseState": 0,
                "acknowledged": false,
                "purchaseToken": "mock_token"
            }
        """.trimIndent()
        val purchase = com.android.billingclient.api.Purchase(purchaseJson, "mock_signature")
        
        val newBillingManager = BillingManager(context)
        newBillingManager.onPurchasesUpdated(billingResult, mutableListOf(purchase))
    }
}
