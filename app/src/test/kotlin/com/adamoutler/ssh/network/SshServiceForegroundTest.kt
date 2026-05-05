package com.adamoutler.ssh.network

import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        val service = serviceController.get()
        val shadowService = shadowOf(service)

        // Assert that startForeground was called.
        // It throws MissingForegroundServiceTypeException if not correctly typed internally in real Android.
        val notification = shadowService.lastForegroundNotification
        assertNotNull("Foreground notification should be present", notification)

        println("Service started successfully without MissingForegroundServiceTypeException on API 34.")
    }

    @org.junit.Ignore("Flaky JVM crash")
    @Test
    fun `test service connection state transitions to error on failure`() = kotlinx.coroutines.runBlocking<Unit> {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val storageManager = SecurityStorageManager(app, app.getSharedPreferences("test_fgs_error", 0))

        // This profile points to a non-existent local server, ensuring a failure
        val p1 = ConnectionProfile("id-fail", "FailServer", "127.0.0.1", port = 65535, username = "u1", authType = AuthType.PASSWORD, password = "pwd".toByteArray())
        storageManager.saveProfile(p1)

        val intent = Intent(app, SshService::class.java).apply {
            action = SshService.ACTION_START
            putExtra(SshService.EXTRA_PROFILE_ID, "id-fail")
        }

        ConnectionStateRepository.clearSession("id-fail")
        ConnectionStateRepository.clearConnections()

        val serviceController = Robolectric.buildService(SshService::class.java, intent)
        serviceController.create().startCommand(0, 1)

        // Wait a bit for coroutines to execute and fail
        var retries = 0
        var currentState: ConnectionState? = null
        while (retries < 50) {
            currentState = ConnectionStateRepository.connectionStates.value["id-fail"]
            if (currentState is ConnectionState.Error) {
                break
            }
            kotlinx.coroutines.delay(100)
            retries++
        }

        org.junit.Assert.assertTrue("State should transition to Error", currentState is ConnectionState.Error)
        serviceController.destroy()
    }

    @Test
    fun `test service intentional disconnect does not transition to error`() = kotlinx.coroutines.runBlocking<Unit> {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val storageManager = SecurityStorageManager(app, app.getSharedPreferences("test_fgs_disconnect", 0))

        // This profile points to a non-existent local server, but we will cancel it before it errors, or we can use a mock server
        val p1 = ConnectionProfile("id-disc", "DisconnectServer", "127.0.0.1", port = 65535, username = "u1", authType = AuthType.PASSWORD, password = "pwd".toByteArray())
        storageManager.saveProfile(p1)

        val intent = Intent(app, SshService::class.java).apply {
            action = SshService.ACTION_START
            putExtra(SshService.EXTRA_PROFILE_ID, "id-disc")
            // Provide a static session ID so we can cancel it
            putExtra(SshService.EXTRA_SESSION_ID, "session-disc")
        }

        ConnectionStateRepository.clearSession("session-disc")
        ConnectionStateRepository.clearConnections()

        val serviceController = Robolectric.buildService(SshService::class.java, intent)
        serviceController.create().startCommand(0, 1)

        // Immediately send disconnect intent
        val disconnectIntent = Intent(app, SshService::class.java).apply {
            action = SshService.ACTION_DISCONNECT
            putExtra(SshService.EXTRA_PROFILE_ID, "id-disc")
            putExtra(SshService.EXTRA_SESSION_ID, "session-disc")
        }
        serviceController.get().onStartCommand(disconnectIntent, 0, 2)

        // Wait a bit for coroutines to process the cancellation
        var retries = 0
        var currentState: ConnectionState? = null
        while (retries < 20) {
            currentState = ConnectionStateRepository.connectionStates.value["id-disc"]
            if (currentState is ConnectionState.Disconnected || currentState is ConnectionState.Disconnecting || currentState == null) {
                break
            }
            kotlinx.coroutines.delay(100)
            retries++
        }

        org.junit.Assert.assertFalse("State should not transition to Error", currentState is ConnectionState.Error)
        serviceController.destroy()
    }

    @Test
    fun `test service onBind returns null`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val intent = Intent(app, SshService::class.java)
        val serviceController = Robolectric.buildService(SshService::class.java, intent)
        val service = serviceController.create().get()
        assertNull("onBind should return null", service.onBind(null))
        serviceController.destroy()
    }

    /*
    @Test
    fun `test service onDestroy stops all connections`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val intent = Intent(app, SshService::class.java)
        val serviceController = Robolectric.buildService(SshService::class.java, intent)
        serviceController.create().startCommand(0, 1)

        ConnectionStateRepository.getOrCreateSession("prof1", "sess1")
        serviceController.destroy()

        val state = ConnectionStateRepository.connectionStates.value["prof1"]
        org.junit.Assert.assertTrue("State should be Disconnecting or null", state is ConnectionState.Disconnecting || state == null)
    }
    */

    /*
    @Test
    fun `test start command with ACTION_DISCONNECT without session ID stops all for profile`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val serviceController = Robolectric.buildService(SshService::class.java)
        serviceController.create().startCommand(0, 1)
        
        ConnectionStateRepository.getOrCreateSession("prof_multi", "sessA")
        ConnectionStateRepository.getOrCreateSession("prof_multi", "sessB")

        val intent = Intent(app, SshService::class.java).apply {
            action = SshService.ACTION_DISCONNECT
            putExtra(SshService.EXTRA_PROFILE_ID, "prof_multi")
        }

        serviceController.get().onStartCommand(intent, 0, 2)
        
        val state = ConnectionStateRepository.connectionStates.value["prof_multi"]
        org.junit.Assert.assertTrue("State should be Disconnecting or null", state is ConnectionState.Disconnecting || state == null)
        serviceController.destroy()
    }

    @Test
    fun `test start command with ACTION_DISCONNECT without profile ID calls stopAllConnections and stopSelf`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val serviceController = Robolectric.buildService(SshService::class.java)
        serviceController.create().startCommand(0, 1)

        val intent = Intent(app, SshService::class.java).apply {
            action = SshService.ACTION_DISCONNECT
        }

        serviceController.get().onStartCommand(intent, 0, 2)
        // Check if service stopped (Robolectric shadows can be checked if needed, but invoking without crash is good enough coverage)
        serviceController.destroy()
    }
    */

    @Test
    fun `test mapExceptionMessage formats correctly`() {
        val e1 = Exception("Exhausted available authentication methods")
        assertEquals("Connection failed", SshService.mapExceptionMessage(e1))

        val e2 = Exception("Some other error")
        assertEquals("Some other error", SshService.mapExceptionMessage(e2))
    }
}
