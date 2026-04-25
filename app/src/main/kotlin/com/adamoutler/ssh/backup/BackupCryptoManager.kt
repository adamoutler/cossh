package com.adamoutler.ssh.backup

import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.IdentityProfile
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
    val version: Int = 2,
    val profiles: List<ConnectionProfile>,
    val profilePasswords: Map<String, String>,
    val identities: List<IdentityProfile> = emptyList(),
    val identityPasswords: Map<String, String> = emptyMap(),
    val identityPrivateKeys: Map<String, String> = emptyMap()
)

object BackupCryptoManager {
    private const val ITERATION_COUNT = 65536
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BIT = 128

    fun exportProfilesToZip(
        profiles: List<ConnectionProfile>,
        identities: List<IdentityProfile>,
        password: CharArray,
        outputStream: OutputStream
    ) {
        val passwordsMap = mutableMapOf<String, String>()
        for (profile in profiles) {
            profile.password?.let { pwdBytes ->
                passwordsMap[profile.id] = Base64.getEncoder().encodeToString(pwdBytes)
            }
        }
        
        val identityPasswordsMap = mutableMapOf<String, String>()
        val identityPrivateKeysMap = mutableMapOf<String, String>()
        for (identity in identities) {
            identity.password?.let {
                identityPasswordsMap[identity.id] = Base64.getEncoder().encodeToString(it)
            }
            identity.privateKey?.let {
                identityPrivateKeysMap[identity.id] = Base64.getEncoder().encodeToString(it)
            }
        }
        
        val payload = BackupPayload(
            version = 2,
            profiles = profiles, 
            profilePasswords = passwordsMap,
            identities = identities,
            identityPasswords = identityPasswordsMap,
            identityPrivateKeys = identityPrivateKeysMap
        )
        
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

    fun importProfilesFromZip(inputStream: InputStream, password: CharArray): Pair<List<ConnectionProfile>, List<IdentityProfile>> {
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

        val payload = Json { ignoreUnknownKeys = true }.decodeFromString<BackupPayload>(jsonString)
        
        for (profile in payload.profiles) {
            payload.profilePasswords[profile.id]?.let { pwdStr ->
                profile.password = Base64.getDecoder().decode(pwdStr)
            }
        }
        for (identity in payload.identities) {
            payload.identityPasswords[identity.id]?.let { pwdStr ->
                identity.password = Base64.getDecoder().decode(pwdStr)
            }
            payload.identityPrivateKeys[identity.id]?.let { pkStr ->
                identity.privateKey = Base64.getDecoder().decode(pkStr)
            }
        }
        
        return Pair(payload.profiles, payload.identities)
    }
}
