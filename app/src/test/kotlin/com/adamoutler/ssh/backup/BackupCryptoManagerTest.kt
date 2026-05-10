package com.adamoutler.ssh.backup

import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.IdentityProfile
import com.adamoutler.ssh.data.PortForwardConfig
import com.adamoutler.ssh.data.PortForwardType
import com.adamoutler.ssh.data.Protocol
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BackupCryptoManagerTest {

    @Test
    fun `test BackupPayload serialization`() {
        val payload = BackupPayload(
            version = 2,
            profiles = listOf(
                ConnectionProfile(
                    id = "p-1",
                    nickname = "Test",
                    host = "localhost",
                    username = "test-user",
                    portForwards = listOf(PortForwardConfig(PortForwardType.LOCAL, 8080, "127.0.0.1", 80)),
                ),
            ),
            profilePasswords = mapOf("p-1" to "encoded-pass"),
            identities = listOf(
                IdentityProfile(id = "i-1", name = "Test Identity", username = "test-user"),
            ),
            identityPasswords = mapOf("i-1" to "encoded-ipass"),
            identityPrivateKeys = mapOf("i-1" to "encoded-ikey"),
        )

        val jsonString = Json.encodeToString(payload)
        val deserialized = Json.decodeFromString<BackupPayload>(jsonString)

        assertEquals(payload.version, deserialized.version)
        assertEquals(payload.profiles.size, deserialized.profiles.size)
        assertEquals(payload.profilePasswords, deserialized.profilePasswords)
        assertEquals(payload.identities.size, deserialized.identities.size)
        assertEquals(payload.identityPasswords, deserialized.identityPasswords)
        assertEquals(payload.identityPrivateKeys, deserialized.identityPrivateKeys)
    }

    @Test
    fun `test export and import profiles`() {
        val profiles = listOf(
            ConnectionProfile(
                id = "p-1",
                nickname = "Profile 1",
                host = "1.2.3.4",
                password = "test-password".toByteArray(),
            ),
        )
        val identities = listOf(
            IdentityProfile(
                id = "i-1",
                name = "Identity 1",
                username = "id-user",
                password = "id-password".toByteArray(),
                privateKey = "id-priv-key".toByteArray(),
            ),
        )

        val password = "secure-backup-password".toCharArray()
        val outputStream = ByteArrayOutputStream()

        // Export
        BackupCryptoManager.exportProfilesToZip(profiles, identities, password, outputStream)

        val zipData = outputStream.toByteArray()
        assertTrue(zipData.isNotEmpty())

        // Import
        val inputStream = ByteArrayInputStream(zipData)
        val (importedProfiles, importedIdentities) = BackupCryptoManager.importProfilesFromZip(inputStream, password)

        assertEquals(1, importedProfiles.size)
        assertEquals("p-1", importedProfiles[0].id)
        assertEquals("test-password", importedProfiles[0].password?.let { String(it) })

        assertEquals(1, importedIdentities.size)
        assertEquals("i-1", importedIdentities[0].id)
        assertEquals("id-password", importedIdentities[0].password?.let { String(it) })
        assertEquals("id-priv-key", importedIdentities[0].privateKey?.let { String(it) })
    }
}
