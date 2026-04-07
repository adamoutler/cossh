package com.adamoutler.ssh

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.GrantPermissionRule
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.crypto.SecurityStorageManager
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking
import androidx.test.core.app.ActivityScenario

@RunWith(AndroidJUnit4::class)
@LargeTest
class ConnectionCrashTest {

    @get:Rule
    val grantPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.POST_NOTIFICATIONS,
        android.Manifest.permission.FOREGROUND_SERVICE,
        android.Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE
    )

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @Test
    fun testClickingConnectionProfileDoesNotCrash() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val storageManager = SecurityStorageManager(context)
            
            // Add a mock profile
            val profile = ConnectionProfile(
                id = "mock-id-ui-crash-test",
                nickname = "UI Crash Test Profile",
                host = "localhost",
                username = "user",
                authType = AuthType.PASSWORD,
                port = 22
            )
            profile.password = "password".toByteArray()
            storageManager.saveProfile(profile)
            
            // Start the activity NOW, after profile is in storage
            val scenario = ActivityScenario.launch(MainActivity::class.java)

            // Wait for UI to load
            Thread.sleep(3000)

            // Click the profile in the list
            composeTestRule.onNodeWithText("UI Crash Test Profile").performClick()

            // Wait to see if it crashes
            Thread.sleep(5000)

            // Clean up
            storageManager.deleteProfile(profile.id)
            
            val stopIntent = android.content.Intent(context, com.adamoutler.ssh.network.SshService::class.java).apply {
                action = com.adamoutler.ssh.network.SshService.ACTION_DISCONNECT
            }
            context.startService(stopIntent)
            scenario.close()
        }
    }
}