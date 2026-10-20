package com.adamoutler.ssh.crypto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class SyncPassphraseZeroKnowledgeTest {

    private lateinit var context: Context
    private lateinit var storageManager: SecurityStorageManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        storageManager = SecurityStorageManager(context)
        storageManager.clearSyncPassphrase()
    }

    @Test
    fun testPassphraseConversion_RoundTripPreservesExactChars() {
        val testCases = listOf(
            "SimplePass123!".toCharArray(),
            "Kōhī☕To🔑Biometric-🔐-2026".toCharArray(),
            "日本語パスフレーズ!#%^&*()_+|}{[]~`".toCharArray(),
            CharArray(512) { (33 + (it % 90)).toChar() },
        )

        for (passphrase in testCases) {
            storageManager.saveSyncPassphrase(passphrase)
            val retrieved = storageManager.getSyncPassphrase()
            assertArrayEquals("Failed for passphrase length ${passphrase.size}", passphrase, retrieved)
        }
    }

    @Test
    fun testClearSyncPassphrase_RemovesFromPrefs() {
        val pass = "temporaryPassphrase".toCharArray()
        storageManager.saveSyncPassphrase(pass)
        val retrieved = storageManager.getSyncPassphrase()
        assertArrayEquals(pass, retrieved)

        storageManager.clearSyncPassphrase()
        assertNull(storageManager.getSyncPassphrase())
    }

    @Test
    fun testPassphraseConversion_CorruptBase64ReturnsNull() {
        storageManager.encryptedPrefs.edit().putString("sync_passphrase", "not-a-valid-base64-string!@#$").apply()
        assertNull(storageManager.getSyncPassphrase())
    }

    @Test
    fun testGetSyncPassphrase_WhenNotSet_ReturnsNull() {
        assertNull(storageManager.getSyncPassphrase())
    }
}
