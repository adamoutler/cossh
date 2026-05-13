package com.adamoutler.ssh.sync

import android.app.Activity
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.os.Build

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class DriveSyncManagerEdgeCasesCoverageTest {

    @Test
    fun testUploadBackup_NotAuthenticated() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val manager = DriveSyncManager(app)
        var thrown = false
        try {
            manager.uploadBackup(ByteArray(0), CharArray(0))
        } catch (e: Exception) {
            thrown = true
        }
        assertTrue(thrown)
    }

    @Test
    fun testDownloadBackup_NotAuthenticated() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val manager = DriveSyncManager(app)
        val result = manager.downloadBackup(CharArray(0))
        assertNull(result)
    }
}
