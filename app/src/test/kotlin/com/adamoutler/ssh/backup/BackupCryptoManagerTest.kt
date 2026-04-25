package com.adamoutler.ssh.backup

import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.IdentityProfile
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BackupCryptoManagerTest {

    @Test
    fun testExportAndImportBackup() {
        val passwordBytes = "my_secret_password".toByteArray()
        val profile1 = ConnectionProfile("id1", "Host 1", "host1.com", 22, "user1", AuthType.PASSWORD, 0, passwordBytes)
        val profile2 = ConnectionProfile("id2", "Host 2", "host2.com", 22, "user2", AuthType.KEY, 0)

        val profiles = listOf(profile1, profile2)
        
        val identityPasswordBytes = "identity_password".toByteArray()
        val identityPrivateKeyBytes = "identity_private_key".toByteArray()
        val identity1 = IdentityProfile("ident1", "My Identity", "iduser", identityPasswordBytes, identityPrivateKeyBytes, null, AuthType.KEY)
        val identities = listOf(identity1)
        
        val backupPassword = "strong_backup_password".toCharArray()

        val outputStream = ByteArrayOutputStream()
        
        // Test export
        BackupCryptoManager.exportProfilesToZip(profiles, identities, backupPassword, outputStream)
        
        val encryptedData = outputStream.toByteArray()
        assertTrue(encryptedData.isNotEmpty())

        val inputStream = ByteArrayInputStream(encryptedData)
        
        // Test import
        val (restoredProfiles, restoredIdentities) = BackupCryptoManager.importProfilesFromZip(inputStream, backupPassword)
        
        assertEquals(2, restoredProfiles.size)

        val restored1 = restoredProfiles.find { it.id == "id1" }
        assertNotNull(restored1)
        assertEquals("Host 1", restored1?.nickname)
        assertArrayEquals(passwordBytes, restored1?.password)

        val restored2 = restoredProfiles.find { it.id == "id2" }
        assertNotNull(restored2)
        assertEquals("Host 2", restored2?.nickname)
        
        assertEquals(1, restoredIdentities.size)
        val restoredIdent1 = restoredIdentities.find { it.id == "ident1" }
        assertNotNull(restoredIdent1)
        assertEquals("My Identity", restoredIdent1?.name)
        assertArrayEquals(identityPasswordBytes, restoredIdent1?.password)
        assertArrayEquals(identityPrivateKeyBytes, restoredIdent1?.privateKey)
    }
}
