package com.adamoutler.ssh.network

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.os.Build

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class SshServiceCoverageTest {

    @Test
    fun testOnStartCommand() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val intent = Intent(app, SshService::class.java).apply {
            putExtra("profile_id", "test_id")
            putExtra("hostname", "localhost")
            putExtra("port", 22)
            putExtra("password", "pass")
            putExtra("auth_type", "PASSWORD")
        }

        val service = Robolectric.buildService(SshService::class.java, intent).create().get()
        assertNotNull(service)

        service.onStartCommand(intent, 0, 1)

        Thread.sleep(500)
    }
}
