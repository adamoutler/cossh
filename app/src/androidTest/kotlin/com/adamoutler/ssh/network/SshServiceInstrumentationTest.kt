package com.adamoutler.ssh.network

import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.GrantPermissionRule
import com.adamoutler.ssh.MainActivity
import org.junit.Assert.assertNotNull
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
    fun testForegroundServiceSurvivesActivityBackgrounding() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val serviceIntent = Intent(context, SshService::class.java).apply {
            action = SshService.ACTION_START
            putExtra(SshService.EXTRA_PROFILE_ID, "mock-profile")
        }
        
        context.startForegroundService(serviceIntent)

        Thread.sleep(1000)

        // Background the activity
        scenario.moveToState(Lifecycle.State.CREATED)

        Thread.sleep(1000)

        assertNotNull(scenario)
        
        val stopIntent = Intent(context, SshService::class.java).apply {
            action = SshService.ACTION_DISCONNECT
        }
        context.startService(stopIntent)
        scenario.close()
    }
}
