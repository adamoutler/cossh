package com.adamoutler.ssh.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.crypto.IdentityStorageManager
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.IdentityProfile
import com.adamoutler.ssh.data.Protocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
class BackupManagerTest {

    private lateinit var context: Context
    private lateinit var securityStorageManager: SecurityStorageManager
    private lateinit var identityStorageManager: IdentityStorageManager
    private lateinit var backupManager: BackupManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        securityStorageManager = SecurityStorageManager(context)
        identityStorageManager = IdentityStorageManager(context)
        backupManager = BackupManager(context, securityStorageManager, identityStorageManager)
    }

    @Test
    fun `test exportBackup and importBackup`() {
        val passwordBytes = "my_secret_password".toByteArray()
        val profile1 = ConnectionProfile("id1", "Host 1", "host1.com", 22, Protocol.SSH, "user1", AuthType.PASSWORD, 0, passwordBytes, null, null, null, null, emptyMap(), emptyList(), emptyList(), "/var/www")

        val identityPasswordBytes = "identity_password".toByteArray()
        val identityPrivateKeyBytes = "identity_private_key".toByteArray()
        val identity1 = IdentityProfile("ident1", "My Identity", "iduser", identityPasswordBytes, identityPrivateKeyBytes, null, AuthType.KEY)

        // Save to managers
        securityStorageManager.saveProfile(profile1)
        identityStorageManager.saveIdentity(identity1)

        val backupPassword = "strong_backup_password".toCharArray()

        // Create a temp file to act as the export target
        val tempFile = File.createTempFile("backup_test", ".zip")
        val uri = Uri.fromFile(tempFile)

        // Export
        backupManager.exportBackup(uri, backupPassword)

        assertTrue(tempFile.exists() && tempFile.length() > 0)

        // Clear managers to ensure import works
        securityStorageManager.deleteProfile("id1")
        identityStorageManager.deleteIdentity("ident1")

        assertEquals(0, securityStorageManager.getAllProfiles().size)
        assertEquals(0, identityStorageManager.getAllIdentities().size)

        // Import
        backupManager.importBackup(uri, backupPassword)

        val restoredProfiles = securityStorageManager.getAllProfiles()
        val restoredIdentities = identityStorageManager.getAllIdentities()

        assertEquals(1, restoredProfiles.size)
        val restored1 = securityStorageManager.getProfile("id1") // Fetch hydrated profile
        assertNotNull(restored1)
        assertEquals("Host 1", restored1?.nickname)
        assertNotNull(restored1?.password)
        assertArrayEquals(passwordBytes, restored1?.password)

        assertEquals(1, restoredIdentities.size)
        val restoredIdent1 = identityStorageManager.getIdentity("ident1") // Fetch hydrated identity
        assertNotNull(restoredIdent1)
        assertEquals("My Identity", restoredIdent1?.name)
        assertNotNull(restoredIdent1?.password)
        assertArrayEquals(identityPasswordBytes, restoredIdent1?.password)
        assertNotNull(restoredIdent1?.privateKey)
        assertArrayEquals(identityPrivateKeyBytes, restoredIdent1?.privateKey)
    }
}
