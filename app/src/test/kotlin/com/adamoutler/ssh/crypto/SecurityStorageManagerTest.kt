package com.adamoutler.ssh.crypto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class SecurityStorageManagerTest {

    private lateinit var context: Context
    private lateinit var storageManager: SecurityStorageManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val testPrefs = context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)
        storageManager = SecurityStorageManager(context, testPrefs)
    }

    @Test
    fun testSaveAndRetrieveProfile() {
        val profile = ConnectionProfile(
            id = "test-id-1",
            nickname = "Test Server",
            host = "192.168.1.100",
            port = 22,
            username = "root",
            authType = AuthType.PASSWORD,
            password = "supersecretpassword"
        )

        storageManager.saveProfile(profile)
        
        val retrieved = storageManager.getProfile("test-id-1")
        assertNotNull(retrieved)
        assertEquals(profile.nickname, retrieved?.nickname)
        assertEquals(profile.host, retrieved?.host)
        assertEquals(profile.password, retrieved?.password)
    }

    @Test
    fun testDeleteProfile() {
        val profile = ConnectionProfile(
            id = "test-id-2",
            nickname = "Test Server 2",
            host = "10.0.0.5",
            port = 2222,
            username = "admin",
            authType = AuthType.KEY
        )

        storageManager.saveProfile(profile)
        assertNotNull(storageManager.getProfile("test-id-2"))
        
        storageManager.deleteProfile("test-id-2")
        assertNull(storageManager.getProfile("test-id-2"))
    }
}
