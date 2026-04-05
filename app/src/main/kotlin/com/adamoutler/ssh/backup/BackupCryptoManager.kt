package com.adamoutler.ssh.backup

import com.adamoutler.ssh.data.ConnectionProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
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

@Serializable
data class BackupPayload(
    val version: Int = 1,
    val profiles: List<ConnectionProfile>,
    val profilePasswords: Map<String, String>
)

object BackupCryptoManager {
    private const val ITERATION_COUNT = 65536
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BIT = 128

    fun exportProfilesToZip(profiles: List<ConnectionProfile>, password: CharArray, outputStream: OutputStream) {
        val passwordsMap = mutableMapOf<String, String>()
        for (profile in profiles) {
            profile.password?.let { pwdBytes ->
                passwordsMap[profile.id] = Base64.getEncoder().encodeToString(pwdBytes)
            }
        }
        val payload = BackupPayload(profiles = profiles, profilePasswords = passwordsMap)
        
        val jsonString = Json.encodeToString(payload)
        val plainTextBytes = jsonString.toByteArray(Charsets.UTF_8)

        val salt = ByteArray(SALT_LENGTH)
        val iv = ByteArray(IV_LENGTH)
        val secureRandom = SecureRandom()
        secureRandom.nextBytes(salt)
        secureRandom.nextBytes(iv)

        val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keySpec = PBEKeySpec(password, salt, ITERATION_COUNT, KEY_LENGTH)
        val secretKeyBytes = secretKeyFactory.generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(secretKeyBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        val cipherText = cipher.doFinal(plainTextBytes)

        val encryptedData = ByteArrayOutputStream().apply {
            write(salt)
            write(iv)
            write(cipherText)
        }.toByteArray()

        ZipOutputStream(outputStream).use { zipOut ->
            val entry = ZipEntry("backup.enc")
            zipOut.putNextEntry(entry)
            zipOut.write(encryptedData)
            zipOut.closeEntry()
        }
    }

    fun importProfilesFromZip(inputStream: InputStream, password: CharArray): List<ConnectionProfile> {
        var encryptedData: ByteArray? = null
        ZipInputStream(inputStream).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                if (entry.name == "backup.enc") {
                    encryptedData = zipIn.readBytes()
                    break
                }
                entry = zipIn.nextEntry
            }
        }

        if (encryptedData == null || encryptedData!!.size < SALT_LENGTH + IV_LENGTH) {
            throw IllegalArgumentException("Invalid backup file")
        }

        val data = encryptedData!!
        val salt = data.copyOfRange(0, SALT_LENGTH)
        val iv = data.copyOfRange(SALT_LENGTH, SALT_LENGTH + IV_LENGTH)
        val cipherText = data.copyOfRange(SALT_LENGTH + IV_LENGTH, data.size)

        val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keySpec = PBEKeySpec(password, salt, ITERATION_COUNT, KEY_LENGTH)
        val secretKeyBytes = secretKeyFactory.generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(secretKeyBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

        val plainTextBytes = cipher.doFinal(cipherText)
        val jsonString = String(plainTextBytes, Charsets.UTF_8)

        val payload = Json.decodeFromString<BackupPayload>(jsonString)
        
        for (profile in payload.profiles) {
            payload.profilePasswords[profile.id]?.let { pwdStr ->
                profile.password = Base64.getDecoder().decode(pwdStr)
            }
        }
        
        return payload.profiles
    }
}
