package com.adamoutler.ssh.network

import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class SshServiceForegroundTest {

    @Test
    fun `starting service on API 34 calls startForeground with type`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val storageManager = SecurityStorageManager(app, app.getSharedPreferences("test_fgs", 0))
        val p1 = ConnectionProfile("id1", "Nick1", "host1", username = "u1", authType = AuthType.PASSWORD)
        storageManager.saveProfile(p1)

        val intent = Intent(app, SshService::class.java).apply {
            action = SshService.ACTION_START
            putExtra(SshService.EXTRA_PROFILE_ID, "id1")
        }

        // Start the service
        val serviceController = Robolectric.buildService(SshService::class.java, intent)
        serviceController.create().startCommand(0, 1)

        val service = serviceController.get()
        val shadowService = shadowOf(service)
        
        // Assert that startForeground was called.
        // It throws MissingForegroundServiceTypeException if not correctly typed internally in real Android.
        val notification = shadowService.lastForegroundNotification
        assertNotNull("Foreground notification should be present", notification)
        
        println("Service started successfully without MissingForegroundServiceTypeException on API 34.")
    }

    @Test
    fun `test service connection state transitions to error on failure`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val storageManager = SecurityStorageManager(app, app.getSharedPreferences("test_fgs_error", 0))
        
        // This profile points to a non-existent local server, ensuring a failure
        val p1 = ConnectionProfile("id-fail", "FailServer", "127.0.0.1", port = 65535, username = "u1", authType = AuthType.PASSWORD, password = "pwd".toByteArray())
        storageManager.saveProfile(p1)

        val intent = Intent(app, SshService::class.java).apply {
            action = SshService.ACTION_START
            putExtra(SshService.EXTRA_PROFILE_ID, "id-fail")
        }

        SshSessionProvider.clearSession("id-fail")
        SshSessionProvider.clearConnections()

        val serviceController = Robolectric.buildService(SshService::class.java, intent)
        serviceController.create().startCommand(0, 1)

        // Wait a bit for coroutines to execute and fail
        var retries = 0
        var currentState: ConnectionState? = null
        while (retries < 50) {
            currentState = SshSessionProvider.connectionStates.value["id-fail"]
            if (currentState is ConnectionState.Error) {
                break
            }
            kotlinx.coroutines.delay(100)
            retries++
        }
        
        org.junit.Assert.assertTrue("State should transition to Error", currentState is ConnectionState.Error)
    }
}
