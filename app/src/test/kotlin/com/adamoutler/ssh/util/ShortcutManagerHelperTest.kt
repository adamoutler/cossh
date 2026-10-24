package com.adamoutler.ssh.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.data.ConnectionProfile
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShortcutManagerHelperTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testPushProfileShortcut_success() {
        val profile = ConnectionProfile(
            id = "test-profile-1",
            nickname = "Production Server",
            host = "10.0.0.1",
            port = 22,
            username = "root",
        )
        val result = ShortcutManagerHelper.pushProfileShortcut(context, profile)
        assertTrue(result)
    }

    @Test
    fun testPushProfileShortcut_blankNicknameFallsBackToHost() {
        val profile = ConnectionProfile(
            id = "test-profile-2",
            nickname = "",
            host = "192.168.1.100",
            port = 2222,
            username = "admin",
        )
        val result = ShortcutManagerHelper.pushProfileShortcut(context, profile)
        assertTrue(result)
    }

    @Test
    fun testRemoveProfileShortcut_executesWithoutError() {
        ShortcutManagerHelper.removeProfileShortcut(context, "test-profile-1")
    }
}
