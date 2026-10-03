package com.adamoutler.ssh.backup

import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.IdentityProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.adamoutler.ssh.util.AppLog
import javax.crypto.AEADBadTagException
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
    val identityPrivateKeys: Map<String, String> = emptyMap(),
)

object BackupCryptoManager {
    private const val TAG = "CoSSH:BackupCryptoManager"
    private const val ITERATION_COUNT = 65536
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BIT = 128

    fun exportProfilesToZip(
        profiles: List<ConnectionProfile>,
        identities: List<IdentityProfile>,
        password: CharArray,
        outputStream: OutputStream,
    ) {
        val (cleanProfiles, cleanIdentities) = BackupSanitizer.sanitize(profiles, identities)
        AppLog.i(TAG, "Exporting ${cleanProfiles.size} profiles and ${cleanIdentities.size} identities.")

        val passwordsMap = mutableMapOf<String, String>()
        for (profile in cleanProfiles) {
            profile.password?.let { pwdBytes ->
                passwordsMap[profile.id] = Base64.getEncoder().encodeToString(pwdBytes)
            }
        }

        val identityPasswordsMap = mutableMapOf<String, String>()
        val identityPrivateKeysMap = mutableMapOf<String, String>()
        for (identity in cleanIdentities) {
            identity.password?.let {
                identityPasswordsMap[identity.id] = Base64.getEncoder().encodeToString(it)
            }
            identity.privateKey?.let {
                identityPrivateKeysMap[identity.id] = Base64.getEncoder().encodeToString(it)
            }
        }

        val payload = BackupPayload(
            version = 2,
            profiles = cleanProfiles,
            profilePasswords = passwordsMap,
            identities = cleanIdentities,
            identityPasswords = identityPasswordsMap,
            identityPrivateKeys = identityPrivateKeysMap,
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
        AppLog.i(TAG, "Export completed successfully. Payload size: ${encryptedData.size} bytes.")
    }

    fun importProfilesFromZip(inputStream: InputStream, password: CharArray): Pair<List<ConnectionProfile>, List<IdentityProfile>> {
        AppLog.i(TAG, "Opening backup ZIP archive...")
        var encryptedData: ByteArray? = null
        try {
            ZipInputStream(inputStream).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    AppLog.d(TAG, "Found ZIP entry: ${entry.name}")
                    if (entry.name == "backup.enc") {
                        encryptedData = zipIn.readBytes()
                        break
                    }
                    entry = zipIn.nextEntry
                }
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to read ZIP stream: ${e.message}", e)
            throw IllegalArgumentException("Corrupted backup file: Not a valid ZIP archive (${e.message})", e)
        }

        if (encryptedData == null) {
            AppLog.e(TAG, "Missing 'backup.enc' inside ZIP archive.")
            throw IllegalArgumentException("Invalid backup file: Missing encrypted payload entry ('backup.enc')")
        }

        val data = encryptedData!!
        if (data.size < SALT_LENGTH + IV_LENGTH) {
            AppLog.e(TAG, "Payload too small: ${data.size} bytes (minimum expected: ${SALT_LENGTH + IV_LENGTH})")
            throw IllegalArgumentException("Invalid backup file: Payload is too small (${data.size} bytes)")
        }

        val salt = data.copyOfRange(0, SALT_LENGTH)
        val iv = data.copyOfRange(SALT_LENGTH, SALT_LENGTH + IV_LENGTH)
        val cipherText = data.copyOfRange(SALT_LENGTH + IV_LENGTH, data.size)

        AppLog.d(TAG, "Deriving AES-256 key via PBKDF2WithHmacSHA256 (iterations=$ITERATION_COUNT)...")
        val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keySpec = PBEKeySpec(password, salt, ITERATION_COUNT, KEY_LENGTH)
        val secretKeyBytes = secretKeyFactory.generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(secretKeyBytes, "AES")

        AppLog.d(TAG, "Decrypting payload with AES/GCM/NoPadding (${cipherText.size} bytes)...")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

        val plainTextBytes = try {
            cipher.doFinal(cipherText)
        } catch (e: AEADBadTagException) {
            AppLog.e(TAG, "Decryption authentication failed: Incorrect password or corrupted payload.", e)
            throw IllegalArgumentException("Incorrect password or corrupted backup file", e)
        } catch (e: Exception) {
            AppLog.e(TAG, "Decryption cipher error: ${e.message}", e)
            throw IllegalArgumentException("Decryption error: ${e.message}", e)
        }

        AppLog.d(TAG, "Decrypted ${plainTextBytes.size} bytes. Parsing JSON payload...")
        val jsonString = String(plainTextBytes, Charsets.UTF_8)

        val payload = try {
            Json { ignoreUnknownKeys = true }.decodeFromString<BackupPayload>(jsonString)
        } catch (e: Exception) {
            AppLog.e(TAG, "JSON deserialization failed: ${e.message}", e)
            throw IllegalArgumentException("Incompatible backup format: ${e.message}", e)
        }

        AppLog.i(TAG, "Decoded payload: ${payload.profiles.size} profiles, ${payload.identities.size} identities.")

        for (profile in payload.profiles) {
            payload.profilePasswords[profile.id]?.let { pwdStr ->
                try {
                    profile.password = Base64.getDecoder().decode(pwdStr)
                } catch (e: Exception) {
                    AppLog.w(TAG, "Failed to decode password for profile ${profile.id}: ${e.message}")
                }
            }
        }
        for (identity in payload.identities) {
            payload.identityPasswords[identity.id]?.let { pwdStr ->
                try {
                    identity.password = Base64.getDecoder().decode(pwdStr)
                } catch (e: Exception) {
                    AppLog.w(TAG, "Failed to decode password for identity ${identity.id}: ${e.message}")
                }
            }
            payload.identityPrivateKeys[identity.id]?.let { pkStr ->
                try {
                    identity.privateKey = Base64.getDecoder().decode(pkStr)
                } catch (e: Exception) {
                    AppLog.w(TAG, "Failed to decode private key for identity ${identity.id}: ${e.message}")
                }
            }
        }

        AppLog.d(TAG, "Sanitizing unpacked profiles and identities...")
        val (sanitizedProfiles, sanitizedIdentities) = BackupSanitizer.sanitize(payload.profiles, payload.identities)
        return Pair(sanitizedProfiles, sanitizedIdentities)
    }
}
