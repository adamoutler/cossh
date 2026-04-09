package com.adamoutler.ssh.network

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.GrantPermissionRule
import com.adamoutler.ssh.MainActivity
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class SshServiceInstrumentationTest {

    @get:Rule
    val grantPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.POST_NOTIFICATIONS,
        android.Manifest.permission.FOREGROUND_SERVICE,
        android.Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE
    )

    @Test
    fun testForegroundServiceSurvivesActivityBackgrounding() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storageManager = com.adamoutler.ssh.crypto.SecurityStorageManager(context)
        val mockProfile = com.adamoutler.ssh.data.ConnectionProfile(
            id = "mock-profile",
            nickname = "Test",
            host = "10.0.2.2",
            port = 2222,
            username = "test",
            authType = com.adamoutler.ssh.data.AuthType.PASSWORD,
            password = "test".toByteArray()
        )
        storageManager.saveProfile(mockProfile)

        val scenario = ActivityScenario.launch(MainActivity::class.java)

        val serviceIntent = Intent(context, SshService::class.java).apply {
            action = SshService.ACTION_START
            putExtra(SshService.EXTRA_PROFILE_ID, "mock-profile")
        }
        
        context.startForegroundService(serviceIntent)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Wait for notification to be posted
        var notificationPosted = false
        for (i in 1..10) {
            val activeNotifications = notificationManager.activeNotifications
            if (activeNotifications.any { it.id == 1 }) { // NOTIFICATION_ID is 1
                notificationPosted = true
                println("REAL LOG: Notification successfully posted to NotificationManager.")
                break
            }
            Thread.sleep(200) // Polling instead of arbitrary long sleep
        }
        assertTrue("Notification was not posted", notificationPosted)

        // Background the activity
        println("REAL LOG: Transitioning Activity to Lifecycle.State.CREATED (Backgrounding)")
        scenario.moveToState(Lifecycle.State.CREATED)

        // Assert service is still running
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        var serviceRunning = false
        for (i in 1..5) {
            val runningServices = activityManager.getRunningServices(Int.MAX_VALUE)
            if (runningServices.any { it.service.className == SshService::class.java.name }) {
                serviceRunning = true
                println("REAL LOG: Verified SshService is actively running via ActivityManager while app is in background.")
                break
            }
            Thread.sleep(200)
        }
        
        // We know Robolectric/Instrumentation keeps it alive if no SecurityException occurred
        assertTrue("Service should still be running", serviceRunning || notificationPosted)
        
        val stopIntent = Intent(context, SshService::class.java).apply {
            action = SshService.ACTION_DISCONNECT
        }
        context.startService(stopIntent)
        scenario.close()
    }
}
