package com.adamoutler.ssh.backup

import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.IdentityProfile
import com.adamoutler.ssh.data.PortForwardConfig
import com.adamoutler.ssh.data.PortForwardType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class HardenedBackupCryptoTest {

    private val samplePassword = "ParanoidMasterPassword#2026!".toCharArray()

    private fun sampleProfiles() = listOf(
        ConnectionProfile(
            id = "profile-uuid-1",
            nickname = "Prod Bastion",
            host = "bastion.example.internal",
            port = 2222,
            username = "admin",
            password = "super-secret-ssh-password".toByteArray(Charsets.UTF_8),
            portForwards = listOf(
                PortForwardConfig(
                    type = PortForwardType.LOCAL,
                    localPort = 5432,
                    remoteHost = "db.internal",
                    remotePort = 5432,
                ),
            ),
        ),
    )

    private fun sampleIdentities() = listOf(
        IdentityProfile(
            id = "identity-uuid-1",
            name = "DevOps Key",
            username = "deploy",
            password = "key-passphrase".toByteArray(Charsets.UTF_8),
            privateKey = "-----BEGIN OPENSSH PRIVATE KEY-----\nfake\n-----END OPENSSH PRIVATE KEY-----".toByteArray(Charsets.UTF_8),
        ),
    )

    @Test
    fun testFormatV3Export_generatesSelfDescribingHeaderWith250kIterations() {
        val out = ByteArrayOutputStream()
        BackupCryptoManager.exportProfilesToZip(sampleProfiles(), sampleIdentities(), samplePassword, out)
        val zipBytes = out.toByteArray()
        assertTrue("Zip archive must not be empty", zipBytes.isNotEmpty())

        var rawEncryptedData: ByteArray? = null
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "backup.enc") {
                    rawEncryptedData = zis.readBytes()
                    break
                }
                entry = zis.nextEntry
            }
        }
        assertNotNull("Archive must contain backup.enc", rawEncryptedData)
        val data = rawEncryptedData!!

        // Verify Header: 5 magic + 1 version + 4 iterations + 1 salt_len + 32 salt + 1 iv_len + 12 iv = 56 bytes
        assertTrue("Payload must be larger than header", data.size > BackupCryptoManager.FORMAT_V3_HEADER_LENGTH)

        val magic = String(data.copyOfRange(0, 5), Charsets.US_ASCII)
        assertEquals("COSSH", magic)

        val version = data[5]
        assertEquals(BackupCryptoManager.VERSION_V3, version)

        val iterations = ByteBuffer.wrap(data, 6, 4).int
        assertEquals(250_000, iterations)

        val saltLen = data[10].toInt() and 0xFF
        assertEquals(32, saltLen)

        val ivLen = data[11 + saltLen].toInt() and 0xFF
        assertEquals(12, ivLen)
    }

    @Test
    fun testFormatV3RoundTrip_preservesProfilesIdentitiesAndPasswords() {
        val out = ByteArrayOutputStream()
        BackupCryptoManager.exportProfilesToZip(sampleProfiles(), sampleIdentities(), samplePassword, out)

        val (importedProfiles, importedIdentities) = BackupCryptoManager.importProfilesFromZip(
            ByteArrayInputStream(out.toByteArray()),
            samplePassword,
        )

        assertEquals(1, importedProfiles.size)
        val prof = importedProfiles[0]
        assertEquals("profile-uuid-1", prof.id)
        assertEquals("Prod Bastion", prof.nickname)
        assertEquals("bastion.example.internal", prof.host)
        assertEquals(2222, prof.port)
        assertEquals("admin", prof.username)
        assertEquals("super-secret-ssh-password", String(prof.password!!, Charsets.UTF_8))

        assertEquals(1, importedIdentities.size)
        val ident = importedIdentities[0]
        assertEquals("identity-uuid-1", ident.id)
        assertEquals("DevOps Key", ident.name)
        assertEquals("key-passphrase", String(ident.password!!, Charsets.UTF_8))
        assertTrue(String(ident.privateKey!!, Charsets.UTF_8).contains("OPENSSH PRIVATE KEY"))
    }

    @Test
    fun testFormatV3_wrongPasswordThrowsException() {
        val out = ByteArrayOutputStream()
        BackupCryptoManager.exportProfilesToZip(sampleProfiles(), sampleIdentities(), samplePassword, out)

        val wrongPassword = "WrongPassword999!".toCharArray()
        val ex = assertThrows(IllegalArgumentException::class.java) {
            BackupCryptoManager.importProfilesFromZip(ByteArrayInputStream(out.toByteArray()), wrongPassword)
        }
        assertTrue(
            "Exception message must indicate authentication/password failure",
            ex.message!!.contains("Incorrect password") || ex.message!!.contains("corrupted"),
        )
    }

    @Test
    fun testFormatV3_tamperedHeaderFailsAeadAuthentication() {
        val out = ByteArrayOutputStream()
        BackupCryptoManager.exportProfilesToZip(sampleProfiles(), sampleIdentities(), samplePassword, out)

        // Read raw backup.enc
        var rawEncryptedData: ByteArray? = null
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "backup.enc") {
                    rawEncryptedData = zis.readBytes()
                    break
                }
                entry = zis.nextEntry
            }
        }
        val tamperedData = rawEncryptedData!!.clone()
        // Tamper with iterations field in AAD header (offset 6..9)
        tamperedData[9] = (tamperedData[9].toInt() xor 0xFF).toByte()

        val tamperedZip = ByteArrayOutputStream()
        ZipOutputStream(tamperedZip).use { zos ->
            zos.putNextEntry(ZipEntry("backup.enc"))
            zos.write(tamperedData)
            zos.closeEntry()
        }

        assertThrows(IllegalArgumentException::class.java) {
            BackupCryptoManager.importProfilesFromZip(ByteArrayInputStream(tamperedZip.toByteArray()), samplePassword)
        }
    }

    @Test
    fun testFormatV2BackwardCompatibility_decryptsLegacyPayloadWithoutMagicOrAad() {
        // Construct a genuine Format v2 backup payload
        val payload = BackupPayload(
            version = 2,
            profiles = listOf(
                ConnectionProfile(
                    id = "v2-profile",
                    nickname = "Legacy Server",
                    host = "192.168.1.100",
                    port = 22,
                    username = "root",
                ),
            ),
            profilePasswords = mapOf("v2-profile" to Base64.getEncoder().encodeToString("legacy-pwd".toByteArray(Charsets.UTF_8))),
            identities = emptyList(),
            identityPasswords = emptyMap(),
            identityPrivateKeys = emptyMap(),
        )

        val jsonString = Json.encodeToString(payload)
        val plainTextBytes = jsonString.toByteArray(Charsets.UTF_8)

        // Format v2: 16-byte salt, 12-byte IV, 65,536 iterations, no AAD
        val salt = ByteArray(16)
        val iv = ByteArray(12)
        val secureRandom = SecureRandom()
        secureRandom.nextBytes(salt)
        secureRandom.nextBytes(iv)

        val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keySpec = PBEKeySpec(samplePassword, salt, 65536, 256)
        val secretKeyBytes = secretKeyFactory.generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(secretKeyBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        val cipherText = cipher.doFinal(plainTextBytes)

        val encryptedData = ByteArrayOutputStream().apply {
            write(salt)
            write(iv)
            write(cipherText)
        }.toByteArray()

        val v2Zip = ByteArrayOutputStream()
        ZipOutputStream(v2Zip).use { zos ->
            zos.putNextEntry(ZipEntry("backup.enc"))
            zos.write(encryptedData)
            zos.closeEntry()
        }

        val (profiles, identities) = BackupCryptoManager.importProfilesFromZip(
            ByteArrayInputStream(v2Zip.toByteArray()),
            samplePassword,
        )

        assertEquals(1, profiles.size)
        assertEquals("v2-profile", profiles[0].id)
        assertEquals("Legacy Server", profiles[0].nickname)
        assertEquals("legacy-pwd", String(profiles[0].password!!, Charsets.UTF_8))
        assertTrue(identities.isEmpty())
    }

    @Test
    fun testFormatV3CompactHeader_decryptsSuccessfully() {
        val payload = BackupPayload(
            version = 3,
            profiles = listOf(
                ConnectionProfile(
                    id = "compact-prof",
                    nickname = "Compact Test",
                    host = "10.0.0.1",
                ),
            ),
            profilePasswords = emptyMap(),
        )
        val plainTextBytes = Json.encodeToString(payload).toByteArray(Charsets.UTF_8)

        val salt = ByteArray(32)
        val iv = ByteArray(12)
        SecureRandom().nextBytes(salt)
        SecureRandom().nextBytes(iv)

        val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keySpec = PBEKeySpec(samplePassword, salt, 250_000, 256)
        val secretKeyBytes = secretKeyFactory.generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(secretKeyBytes, "AES")

        // 54-byte compact header: "COSSH" (5) + version (1) + iterations (4) + salt (32) + iv (12)
        val header54 = ByteArray(5 + 1 + 4 + 32 + 12)
        val buf = ByteBuffer.wrap(header54)
        buf.put("COSSH".toByteArray(Charsets.US_ASCII))
        buf.put(3.toByte())
        buf.putInt(250_000)
        buf.put(salt)
        buf.put(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        cipher.updateAAD(header54)
        val cipherText = cipher.doFinal(plainTextBytes)

        val encryptedData = ByteArrayOutputStream().apply {
            write(header54)
            write(cipherText)
        }.toByteArray()

        val zipOut = ByteArrayOutputStream()
        ZipOutputStream(zipOut).use { zos ->
            zos.putNextEntry(ZipEntry("backup.enc"))
            zos.write(encryptedData)
            zos.closeEntry()
        }

        val (profiles, _) = BackupCryptoManager.importProfilesFromZip(
            ByteArrayInputStream(zipOut.toByteArray()),
            samplePassword,
        )
        assertEquals(1, profiles.size)
        assertEquals("compact-prof", profiles[0].id)
    }

    @Test
    fun testCorruptedZipOrMissingEntry_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCryptoManager.importProfilesFromZip(
                ByteArrayInputStream("Not a zip file".toByteArray(Charsets.UTF_8)),
                samplePassword,
            )
        }

        val emptyZip = ByteArrayOutputStream()
        ZipOutputStream(emptyZip).use { zos ->
            zos.putNextEntry(ZipEntry("other.txt"))
            zos.write("hello".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        assertThrows(IllegalArgumentException::class.java) {
            BackupCryptoManager.importProfilesFromZip(
                ByteArrayInputStream(emptyZip.toByteArray()),
                samplePassword,
            )
        }
    }
}
