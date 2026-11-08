package com.adamoutler.ssh.billing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BillingEntitlementPersistenceTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        BillingManager.forceCloudSyncEnabledForTest = false
        context.getSharedPreferences("BillingPrefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        BillingManager.forceCloudSyncEnabledForTest = false
        context.getSharedPreferences("BillingPrefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun testEntitlementPersistedToPrefs() = runBlocking {
        val billingResult = BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.OK).build()
        val purchaseJson = """
            {
                "productId": "lifetimecloudsync",
                "productIds": ["lifetimecloudsync"],
                "purchaseState": 0,
                "acknowledged": true,
                "purchaseToken": "token_123"
            }
        """.trimIndent()
        val purchase = Purchase(purchaseJson, "sig_123")

        val billingManager = BillingManager(context)
        billingManager.onPurchasesUpdated(billingResult, mutableListOf(purchase))

        val prefs = context.getSharedPreferences("BillingPrefs", Context.MODE_PRIVATE)
        assertTrue("Entitlement should be saved in BillingPrefs", prefs.getBoolean("cloud_sync_entitled", false))
        assertTrue("StateFlow should be enabled", billingManager.isCloudSyncEnabled.first())
    }

    @Test
    fun testSubsequentBillingManagerReadsPersistedEntitlement() = runBlocking {
        val hmac = BillingManager.computeEntitlementHmac(context.packageName)
        context.getSharedPreferences("BillingPrefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("cloud_sync_entitled", true)
            .putString("cloud_sync_entitled_hmac", hmac)
            .commit()

        val newBillingManager = BillingManager(context)
        assertTrue("Should read persisted entitlement immediately", newBillingManager.isCloudSyncEnabled.value)
    }

    @Test
    fun testAwaitCloudSyncEntitlement_ReturnsTrueWhenCached() = runBlocking {
        val hmac = BillingManager.computeEntitlementHmac(context.packageName)
        context.getSharedPreferences("BillingPrefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("cloud_sync_entitled", true)
            .putString("cloud_sync_entitled_hmac", hmac)
            .commit()

        val billingManager = BillingManager(context)
        val entitled = billingManager.awaitCloudSyncEntitlement()
        assertTrue("awaitCloudSyncEntitlement should return true when cached", entitled)
    }

    @Test
    fun testStartupRejectsTamperedEntitlementAndPurgesPrefs() = runBlocking {
        val prefs = context.getSharedPreferences("BillingPrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(BillingManager.PREF_KEY_ENTITLED, true)
            .putString(BillingManager.PREF_KEY_ENTITLED_HMAC, "invalid_hmac_signature")
            .commit()

        val billingManager = BillingManager(context)
        assertFalse("Tampered entitlement must not enable cloud sync on startup", billingManager.isCloudSyncEnabled.value)
        assertFalse("Tampered entitlement flag must be purged from prefs", prefs.getBoolean(BillingManager.PREF_KEY_ENTITLED, false))
        assertNull("Tampered HMAC must be removed from prefs", prefs.getString(BillingManager.PREF_KEY_ENTITLED_HMAC, null))
    }

    @Test
    fun testStartupRejectsMissingHmacAndPurgesPrefs() = runBlocking {
        val prefs = context.getSharedPreferences("BillingPrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(BillingManager.PREF_KEY_ENTITLED, true)
            .remove(BillingManager.PREF_KEY_ENTITLED_HMAC)
            .commit()

        val billingManager = BillingManager(context)
        assertFalse("Missing HMAC must not enable cloud sync on startup", billingManager.isCloudSyncEnabled.value)
        assertFalse("Entitlement flag must be purged from prefs", prefs.getBoolean(BillingManager.PREF_KEY_ENTITLED, false))
        assertNull("HMAC must be null in prefs", prefs.getString(BillingManager.PREF_KEY_ENTITLED_HMAC, null))
    }

    @Test
    fun testAwaitCloudSyncEntitlement_ReturnsFalseWhenNotPurchased() = runBlocking {
        val billingManager = BillingManager(context)
        val entitled = billingManager.awaitCloudSyncEntitlement()
        assertFalse("awaitCloudSyncEntitlement should return false when unpurchased", entitled)
    }

    @Test
    fun testAwaitCloudSyncEntitlement_ReturnsTrueWhenForceTestEnabled() = runBlocking {
        BillingManager.forceCloudSyncEnabledForTest = true
        val billingManager = BillingManager(context)
        val entitled = billingManager.awaitCloudSyncEntitlement()
        assertTrue("awaitCloudSyncEntitlement should return true when forceCloudSyncEnabledForTest is true", entitled)
    }
}
