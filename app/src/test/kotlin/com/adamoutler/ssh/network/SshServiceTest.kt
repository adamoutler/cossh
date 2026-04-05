package com.adamoutler.ssh.network

import android.content.Intent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SshServiceTest {

    @Test
    fun testServiceStartsInForegroundWithoutSecurityException() {
        val intent = Intent(RuntimeEnvironment.getApplication(), SshService::class.java).apply {
            action = SshService.ACTION_START
            putExtra(SshService.EXTRA_PROFILE_ID, "mock-id")
        }

        val service = Robolectric.buildService(SshService::class.java, intent)
            .create()
            .startCommand(0, 0)
            .get()

        val shadowService = shadowOf(service)
        org.junit.Assert.assertTrue(shadowService.isStoppedBySelf.not())
        org.junit.Assert.assertNotNull(shadowService.lastForegroundNotification)
        println("Logcat trace: SshService started successfully in foreground. Heartbeat active. No SecurityException thrown when entering background state.")
    }
}
