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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

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

    @Test
    fun `test import real backup from downloads`() {
        val userHome = System.getProperty("user.home")
        val candidateFiles = listOf(
            File(userHome, "Downloads/connections_and_identities (5).cossh"),
            File(userHome, "Downloads/connections_and_identities.cossh"),
            File(userHome, "Downloads/connections_and_identities (3).cossh"),
        )
        val file = candidateFiles.firstOrNull { it.exists() }

        // When running locally where the file was pulled to Downloads, verify it directly
        org.junit.Assume.assumeTrue("Downloads backup file not present (skipped in CI)", file != null)

        val password = "aaaaaaaa".toCharArray()
        file!!.inputStream().use { stream ->
            val (profiles, identities) = BackupCryptoManager.importProfilesFromZip(stream, password)
            assertTrue("Profiles should be imported", profiles.isNotEmpty())
            assertTrue("Identities should be imported", identities.isNotEmpty())
            // 13 valid profiles (ghost profile with empty host & nickname dropped)
            assertEquals(13, profiles.size)
            assertEquals(4, identities.size)

            val cameraProfile = profiles.find { it.id == "a0ccbaf4-3eb6-4090-82d9-f7132abab8f3" }
            assertNotNull(cameraProfile)
            assertEquals("camera", cameraProfile?.nickname) // whitespace trimmed

            val desktopIdent = identities.find { it.id == "072d5954-3e62-4cad-a6ed-732ac8cbad2c" }
            assertNotNull(desktopIdent)
            assertEquals("adamoutler desktop", desktopIdent?.name) // whitespace trimmed
        }
    }
}
