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

    @Test
    fun testBackupManagerExportImport() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val identityStorageManager = com.adamoutler.ssh.crypto.IdentityStorageManager(app)
        val backupManager = com.adamoutler.ssh.backup.BackupManager(
            app,
            storageManager,
            identityStorageManager
        )
        val profile = ConnectionProfile(
            id = "backup-id-1",
            nickname = "Backup Server",
            host = "10.10.10.10",
            port = 22,
            username = "root",
            authType = AuthType.PASSWORD,
            password = "backup-password".toByteArray()
        )
        storageManager.saveProfile(profile)

        val backupFile = java.io.File(
            androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir,
            "espressotest_backup.zip"
        )
        val uri = android.net.Uri.fromFile(backupFile)
        val password = "espresso-password".toCharArray()

        // Test Export
        backupManager.exportBackup(uri, password)
        org.junit.Assert.assertTrue(backupFile.exists())
        org.junit.Assert.assertTrue(backupFile.length() > 0)
        android.util.Log.d("BackupManagerTest", "Successfully created backup file via native APIs at ${backupFile.absolutePath}")

        // Clean up
        storageManager.deleteProfile("backup-id-1")

        // Test Import
        backupManager.importBackup(uri, password)
        val retrieved = storageManager.getProfile("backup-id-1")
        assertNotNull(retrieved)
        assertEquals("Backup Server", retrieved?.nickname)

        // Clean up
        storageManager.deleteProfile("backup-id-1")
        backupFile.delete()
    }
}
