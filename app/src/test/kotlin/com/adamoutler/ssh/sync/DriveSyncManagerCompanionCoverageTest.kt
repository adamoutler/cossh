package com.adamoutler.ssh.sync

import android.app.Activity
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.os.Build

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class DriveSyncManagerCompanionCoverageTest {

    @Test
    fun testHandleAuthorizationResult() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val manager = DriveSyncManager(app)
        
        var exceptionThrown = false
        DriveSyncManager.authorizationContinuation = object : kotlin.coroutines.Continuation<Unit> {
            override val context = kotlin.coroutines.EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) {
                if (result.isFailure) exceptionThrown = true
            }
        }
        DriveSyncManager.currentInstance = manager
        DriveSyncManager.handleAuthorizationResult(1001, Activity.RESULT_OK, Intent())
        assertTrue(exceptionThrown)
    }
}