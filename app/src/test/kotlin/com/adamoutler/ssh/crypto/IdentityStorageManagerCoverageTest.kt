package com.adamoutler.ssh.crypto

import android.content.Context
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.os.Build

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class IdentityStorageManagerCoverageTest {

    @Test
    fun testExceptionInEncryptedPrefs() {
        val mockContext = object : android.content.ContextWrapper(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()) {
            override fun getSharedPreferences(name: String?, mode: Int): android.content.SharedPreferences {
                throw RuntimeException("Mock failure")
            }
            override fun getApplicationContext(): Context {
                return this
            }
        }
        val manager = IdentityStorageManager(mockContext)
        
        var exceptionThrown = false
        try {
            manager.encryptedPrefs
        } catch (e: Exception) {
            exceptionThrown = true
        }
        assertTrue(exceptionThrown)
    }

    @Test
    fun testResetInvalidatedKeys() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val manager = IdentityStorageManager(app, app.getSharedPreferences("test_id", 0))
        manager.resetInvalidatedKeys()
    }

    @Test
    fun testDecryptSensitive_InvalidBase64() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        app.getSharedPreferences("test_id", 0).edit().putString("1", "{}").putString("1_pwd", "invalid base64!!!").commit()
        val manager = IdentityStorageManager(app, app.getSharedPreferences("test_id", 0))
        val identity = manager.getIdentity("1")
        assertNull(identity?.password)
    }
}
