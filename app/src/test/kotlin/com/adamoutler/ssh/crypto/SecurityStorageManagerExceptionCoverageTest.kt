package com.adamoutler.ssh.crypto

import android.content.Context
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.os.Build

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class SecurityStorageManagerExceptionCoverageTest {

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
        val manager = SecurityStorageManager(mockContext)
        
        var exceptionThrown = false
        try {
            manager.encryptedPrefs
        } catch (e: Exception) {
            exceptionThrown = true
        }
        org.junit.Assert.assertTrue(exceptionThrown)
    }
}
