package com.adamoutler.ssh.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DriveSyncManagerTest {

    private lateinit var context: Context
    private lateinit var driveSyncManager: DriveSyncManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        driveSyncManager = DriveSyncManager(context)
    }

    @Test
    fun `test DriveSyncManager initialization`() {
        // Just covering the initialization and basic properties
        assertNotNull(driveSyncManager)
    }
}