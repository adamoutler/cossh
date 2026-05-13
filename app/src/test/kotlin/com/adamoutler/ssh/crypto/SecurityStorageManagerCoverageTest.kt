package com.adamoutler.ssh.crypto

import androidx.test.core.app.ApplicationProvider
import android.os.Build
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class SecurityStorageManagerCoverageTest {

    @Test
    fun testSaveAndGetProfile() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val manager = SecurityStorageManager(app, app.getSharedPreferences("test_sec", 0))
        
        val profile = ConnectionProfile(id = "1", nickname = "test", host = "localhost", authType = AuthType.PASSWORD)
        profile.password = "password".toByteArray()
        
        manager.saveProfile(profile)
        
        val loaded = manager.getProfile("1")
        assertNotNull(loaded)
        assertEquals("test", loaded?.nickname)
        assertEquals("password", String(loaded?.password!!))
        
        // Test invalid data
        app.getSharedPreferences("test_sec", 0).edit().putString("2", "invalid_json").commit()
        assertNull(manager.getProfile("2"))
        
        // Delete
        manager.deleteProfile("1")
        assertNull(manager.getProfile("1"))
        
        manager.saveSyncPassphrase("testpass".toCharArray())
        val pass = manager.getSyncPassphrase()
        assertEquals("testpass", String(pass!!))
        
        manager.resetInvalidatedKeys()
        
        manager.getAllKeys()
        manager.getAllProfiles()
    }
}
