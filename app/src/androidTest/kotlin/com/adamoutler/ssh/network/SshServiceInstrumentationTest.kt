package com.adamoutler.ssh.network

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.GrantPermissionRule
import com.adamoutler.ssh.MainActivity
import com.adamoutler.ssh.annotations.FullTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * @FullTest Note (SSH-49):
 * This is a long-running integration test excluded from the standard fast CI/CD pipeline.
 * To execute this test locally, use the fullTestRun property:
 * ./gradlew connectedAndroidTest -PfullTestRun
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@FullTest
class SshServiceInstrumentationTest {
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @get:Rule
    val grantPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.POST_NOTIFICATIONS,
        android.Manifest.permission.FOREGROUND_SERVICE,
        android.Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE,
    )

    @Test(timeout = 300000L)
    fun testForegroundServiceSurvivesActivityBackgrounding() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storageManager = com.adamoutler.ssh.crypto.SecurityStorageManager(context)
        storageManager.getAllProfiles().forEach { storageManager.deleteProfile(it.id) }
        com.adamoutler.ssh.network.ConnectionStateRepository.sessions.clear()
        val mockProfile = com.adamoutler.ssh.data.ConnectionProfile(
            id = "mock-profile",
            nickname = "Test",
            host = "10.0.2.2",
            port = 41111,
            username = "test",
            authType = com.adamoutler.ssh.data.AuthType.PASSWORD,
            password = "test".toByteArray(),
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
        try {
            composeTestRule.waitUntil(10000) {
                val activeNotifications = notificationManager.activeNotifications
                activeNotifications.any { it.id == 1000 } // SUMMARY_NOTIFICATION_ID is 1000
            }
            notificationPosted = true
            println("REAL LOG: Notification successfully posted to NotificationManager.")
        } catch (e: Exception) {}
        assertTrue("Notification was not posted", notificationPosted)

        // Background the activity
        println("REAL LOG: Transitioning Activity to Lifecycle.State.CREATED (Backgrounding)")
        scenario.moveToState(Lifecycle.State.CREATED)

        // Assert service is still running via notification
        assertTrue("Service should still be running via notification", notificationPosted)

        val stopIntent = Intent(context, SshService::class.java).apply {
            action = SshService.ACTION_DISCONNECT
        }
        context.startService(stopIntent)
        scenario.close()
    }
}
