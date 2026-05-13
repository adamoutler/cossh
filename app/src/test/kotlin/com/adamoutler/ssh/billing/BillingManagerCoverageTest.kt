package com.adamoutler.ssh.billing

import androidx.test.core.app.ApplicationProvider
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.os.Build

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
}
