package com.adamoutler.ssh.crypto

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecurityStorageManagerInstrumentedTest {

    private lateinit var storageManager: SecurityStorageManager

    @Before
    fun setup() {
        // Native instantiation: Do not inject SharedPreferences. Let it build the MasterKey via AndroidKeyStore
        storageManager = SecurityStorageManager(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun testNativeEncryptionAndRetrieval() {
        val profile = ConnectionProfile(
            id = "native-id-1",
            nickname = "Native Encrypted Server",
            host = "10.10.10.10",
            port = 22,
            username = "root",
            authType = AuthType.PASSWORD,
            password = "nativepassword".toByteArray()
        )

        storageManager.saveProfile(profile)
        
        val retrieved = storageManager.getProfile("native-id-1")
        assertNotNull(retrieved)
        assertEquals(profile.nickname, retrieved?.nickname)
        
        // Clean up
        storageManager.deleteProfile("native-id-1")
        assertNull(storageManager.getProfile("native-id-1"))
    }
}
