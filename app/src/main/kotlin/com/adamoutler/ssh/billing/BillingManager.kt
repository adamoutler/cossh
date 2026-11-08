package com.adamoutler.ssh.billing

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.charset.StandardCharsets
import java.security.Key
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

class BillingManager(private val context: Context) : PurchasesUpdatedListener {

    companion object {
        var forceCloudSyncEnabledForTest = false
        const val PREF_KEY_ENTITLED = "cloud_sync_entitled"
        const val PREF_KEY_ENTITLED_HMAC = "cloud_sync_entitled_hmac"
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private const val HMAC_KEY_ALIAS = "cossh_billing_entitlement_hmac_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"

        private val FALLBACK_TEST_KEY: SecretKey = SecretKeySpec(
            MessageDigest.getInstance("SHA-256")
                .digest("cossh_billing_deterministic_test_seed_2026".toByteArray(StandardCharsets.UTF_8)),
            HMAC_ALGORITHM,
        )

        val isRobolectric: Boolean by lazy {
            try {
                Class.forName("org.robolectric.Robolectric")
                true
            } catch (_: Throwable) {
                false
            }
        }

        fun isRobolectricEnvironment(): Boolean = isRobolectric

        @JvmOverloads
        fun isTestOverrideAllowed(
            isDebugBuild: Boolean = com.adamoutler.ssh.BuildConfig.DEBUG,
            isRobolectricEnv: Boolean = isRobolectricEnvironment(),
        ): Boolean = (isDebugBuild || isRobolectricEnv) && forceCloudSyncEnabledForTest

        fun isTestEnvironment(): Boolean = com.adamoutler.ssh.BuildConfig.DEBUG || isRobolectric

        fun isForceCloudSyncActive(): Boolean = isTestOverrideAllowed()

        @Synchronized
        fun getEntitlementKey(): Key {
            if (isRobolectric) {
                return FALLBACK_TEST_KEY
            }
            return try {
                val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                if (!keyStore.containsAlias(HMAC_KEY_ALIAS)) {
                    val keyGenerator = KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_HMAC_SHA256,
                        ANDROID_KEYSTORE,
                    )
                    val spec = KeyGenParameterSpec.Builder(
                        HMAC_KEY_ALIAS,
                        KeyProperties.PURPOSE_SIGN,
                    ).build()
                    keyGenerator.init(spec)
                    keyGenerator.generateKey()
                }
                keyStore.getKey(HMAC_KEY_ALIAS, null) ?: FALLBACK_TEST_KEY
            } catch (_: Throwable) {
                FALLBACK_TEST_KEY
            }
        }

        fun computeEntitlementHmac(packageName: String): String {
            val message = "cloud_sync_entitled:true:$packageName"
            val mac = Mac.getInstance(HMAC_ALGORITHM)
            mac.init(getEntitlementKey())
            val hmacBytes = mac.doFinal(message.toByteArray(StandardCharsets.UTF_8))
            return hmacBytes.joinToString("") { "%02x".format(it) }
        }
    }

    private val prefs = context.getSharedPreferences("BillingPrefs", Context.MODE_PRIVATE)
    private val _isCloudSyncEnabled = MutableStateFlow(isTestOverrideAllowed() || verifyEntitlementIntegrity())
    val isCloudSyncEnabled: StateFlow<Boolean> = _isCloudSyncEnabled.asStateFlow()

    fun persistEntitlement(targetPrefs: SharedPreferences = prefs) {
        val hmac = computeEntitlementHmac(context.packageName)
        targetPrefs.edit()
            .putBoolean(PREF_KEY_ENTITLED, true)
            .putString(PREF_KEY_ENTITLED_HMAC, hmac)
            .apply()
    }

    fun verifyEntitlementIntegrity(): Boolean {
        if (isTestOverrideAllowed()) return true
        val isEntitled = prefs.getBoolean(PREF_KEY_ENTITLED, false)
        if (!isEntitled) return false
        val storedHmac = prefs.getString(PREF_KEY_ENTITLED_HMAC, null)
        val expectedHmac = computeEntitlementHmac(context.packageName)
        val isValid = storedHmac != null && MessageDigest.isEqual(
            storedHmac.toByteArray(StandardCharsets.UTF_8),
            expectedHmac.toByteArray(StandardCharsets.UTF_8),
        )
        if (!isValid) {
            prefs.edit()
                .putBoolean(PREF_KEY_ENTITLED, false)
                .remove(PREF_KEY_ENTITLED_HMAC)
                .apply()
            return false
        }
        return true
    }

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build(),
        )
        .build()

    init {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryPurchases()
                }
            }
            override fun onBillingServiceDisconnected() {
                // Try to restart the connection on the next request to
                // Google Play by calling the startConnection() method.
            }
        })
    }

    suspend fun awaitCloudSyncEntitlement(timeoutMs: Long = 3000L): Boolean {
        if (isForceCloudSyncActive() || _isCloudSyncEnabled.value) return true
        if (isRobolectric) return _isCloudSyncEnabled.value
        return try {
            withTimeoutOrNull(timeoutMs) {
                _isCloudSyncEnabled.first { it }
            } ?: _isCloudSyncEnabled.value
        } catch (_: Exception) {
            _isCloudSyncEnabled.value
        }
    }

    private fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchases(purchases)
            }
        }
    }

    private fun processPurchases(purchases: List<Purchase>?) {
        purchases?.forEach { purchase ->
            if (purchase.products.contains("lifetimecloudsync")) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    if (!purchase.isAcknowledged) {
                        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
                        billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                                _isCloudSyncEnabled.value = true
                                persistEntitlement(prefs)
                            }
                        }
                    } else {
                        _isCloudSyncEnabled.value = true
                        persistEntitlement(prefs)
                    }
                }
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            processPurchases(purchases)
        }
    }

    fun purchaseCloudSync(activity: Activity) {
        val queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId("lifetimecloudsync")
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()

        billingClient.queryProductDetailsAsync(queryProductDetailsParams) { billingResult, queryProductDetailsResult ->
            val productDetailsList = queryProductDetailsResult.productDetailsList
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                val productDetails = productDetailsList[0]
                val productDetailsParamsList = listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .build(),
                )

                val billingFlowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(productDetailsParamsList)
                    .build()

                billingClient.launchBillingFlow(activity, billingFlowParams)
            }
        }
    }
}
