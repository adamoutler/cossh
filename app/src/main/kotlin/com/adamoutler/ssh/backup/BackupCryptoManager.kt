package com.adamoutler.ssh.backup

import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.IdentityProfile
import com.adamoutler.ssh.util.AppLog
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
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class BackupPayload(
    val version: Int = 3,
    val profiles: List<ConnectionProfile>,
    val profilePasswords: Map<String, String>,
    val identities: List<IdentityProfile> = emptyList(),
    val identityPasswords: Map<String, String> = emptyMap(),
    val identityPrivateKeys: Map<String, String> = emptyMap(),
)

object BackupCryptoManager {
    private const val TAG = "CoSSH:BackupCryptoManager"

    // Format v3 Constants
    const val MAGIC = "COSSH"
    val MAGIC_BYTES = MAGIC.toByteArray(Charsets.US_ASCII)
    const val VERSION_V3: Byte = 3
    const val ITERATION_COUNT_V3 = 250_000
    const val SALT_LENGTH_V3 = 32
    const val IV_LENGTH_V3 = 12
    const val FORMAT_V3_HEADER_LENGTH = 56
    const val FORMAT_V3_HEADER_LENGTH_COMPACT = 54

    // Format v2 Legacy Constants for Backward Compatibility
    const val ITERATION_COUNT_V2 = 65536
    const val SALT_LENGTH_V2 = 16
    const val IV_LENGTH_V2 = 12

    private const val KEY_LENGTH = 256
    private const val TAG_LENGTH_BIT = 128

    @Volatile
    var defaultIterationsV3: Int = ITERATION_COUNT_V3
        private set

    @Volatile
    var defaultIterationsV2: Int = ITERATION_COUNT_V2
        private set

    fun setIterationsForTesting(v3: Int = 1_000, v2: Int = 1_000) {
        defaultIterationsV3 = v3
        defaultIterationsV2 = v2
    }

    fun resetIterationsForTesting() {
        defaultIterationsV3 = ITERATION_COUNT_V3
        defaultIterationsV2 = ITERATION_COUNT_V2
    }

    fun buildFormatV3Header(salt: ByteArray, iv: ByteArray, iterations: Int = defaultIterationsV3): ByteArray {
        val header = ByteArray(MAGIC_BYTES.size + 1 + 4 + 1 + salt.size + 1 + iv.size)
        val buf = java.nio.ByteBuffer.wrap(header)
        buf.put(MAGIC_BYTES)
        buf.put(VERSION_V3)
        buf.putInt(iterations)
        buf.put(salt.size.toByte())
        buf.put(salt)
        buf.put(iv.size.toByte())
        buf.put(iv)
        return header
    }

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
            version = 3,
            profiles = cleanProfiles,
            profilePasswords = passwordsMap,
            identities = cleanIdentities,
            identityPasswords = identityPasswordsMap,
            identityPrivateKeys = identityPrivateKeysMap,
        )

        var plainTextBytes: ByteArray? = null
        var secretKeyBytes: ByteArray? = null
        val salt = ByteArray(SALT_LENGTH_V3)
        val iv = ByteArray(IV_LENGTH_V3)

        try {
            val jsonString = Json.encodeToString(payload)
            plainTextBytes = jsonString.toByteArray(Charsets.UTF_8)

            val secureRandom = SecureRandom()
            secureRandom.nextBytes(salt)
            secureRandom.nextBytes(iv)

            val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val keySpec = PBEKeySpec(password, salt, defaultIterationsV3, KEY_LENGTH)
            val secretKey = try {
                secretKeyBytes = secretKeyFactory.generateSecret(keySpec).encoded
                SecretKeySpec(secretKeyBytes, "AES")
            } finally {
                keySpec.clearPassword()
            }

            val header = buildFormatV3Header(salt, iv, defaultIterationsV3)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
            cipher.updateAAD(header)
            val cipherText = cipher.doFinal(plainTextBytes)

            val encryptedData = ByteArrayOutputStream().apply {
                write(header)
                write(cipherText)
            }.toByteArray()

            ZipOutputStream(outputStream).use { zipOut ->
                val entry = ZipEntry("backup.enc")
                zipOut.putNextEntry(entry)
                zipOut.write(encryptedData)
                zipOut.closeEntry()
            }
            AppLog.i(TAG, "Export completed successfully. Payload size: ${encryptedData.size} bytes.")
        } finally {
            plainTextBytes?.fill(0)
            secretKeyBytes?.fill(0)
            salt.fill(0)
            iv.fill(0)
        }
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
        var plainTextBytes: ByteArray? = null
        var secretKeyBytes: ByteArray? = null
        var salt: ByteArray? = null
        var iv: ByteArray? = null
        var keySpec: PBEKeySpec? = null

        try {
            val isFormatV3 = data.size >= MAGIC_BYTES.size &&
                data.copyOfRange(0, MAGIC_BYTES.size).contentEquals(MAGIC_BYTES)

            if (isFormatV3) {
                if (data.size < MAGIC_BYTES.size + 1 + 4) {
                    throw IllegalArgumentException("Invalid Format v3 backup header: Truncated header")
                }
                val version = data[MAGIC_BYTES.size]
                if (version != VERSION_V3) {
                    throw IllegalArgumentException("Unsupported backup format version: $version")
                }
                val iterations = java.nio.ByteBuffer.wrap(data, MAGIC_BYTES.size + 1, 4).int
                val offsetAfterIter = MAGIC_BYTES.size + 1 + 4 // 10

                val hasLengthBytes = data.size >= offsetAfterIter + 1 + SALT_LENGTH_V3 + 1 + IV_LENGTH_V3 &&
                    data[offsetAfterIter] == SALT_LENGTH_V3.toByte() &&
                    data[offsetAfterIter + 1 + SALT_LENGTH_V3] == IV_LENGTH_V3.toByte()

                val (s, i, header) = if (hasLengthBytes) {
                    val saltLen = data[offsetAfterIter].toInt() and 0xFF
                    val sBytes = data.copyOfRange(offsetAfterIter + 1, offsetAfterIter + 1 + saltLen)
                    val ivLen = data[offsetAfterIter + 1 + saltLen].toInt() and 0xFF
                    val iBytes = data.copyOfRange(offsetAfterIter + 2 + saltLen, offsetAfterIter + 2 + saltLen + ivLen)
                    val h = data.copyOfRange(0, offsetAfterIter + 2 + saltLen + ivLen)
                    Triple(sBytes, iBytes, h)
                } else {
                    // 54-byte compact header without length prefixes
                    val sBytes = data.copyOfRange(offsetAfterIter, offsetAfterIter + SALT_LENGTH_V3)
                    val iBytes = data.copyOfRange(offsetAfterIter + SALT_LENGTH_V3, offsetAfterIter + SALT_LENGTH_V3 + IV_LENGTH_V3)
                    val h = data.copyOfRange(0, offsetAfterIter + SALT_LENGTH_V3 + IV_LENGTH_V3)
                    Triple(sBytes, iBytes, h)
                }
                salt = s
                iv = i
                val cipherText = data.copyOfRange(header.size, data.size)

                AppLog.d(TAG, "Deriving AES-256 key via PBKDF2WithHmacSHA256 (iterations=$iterations, Format v3)...")
                val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                val spec = PBEKeySpec(password, salt, iterations, KEY_LENGTH)
                keySpec = spec
                secretKeyBytes = secretKeyFactory.generateSecret(spec).encoded
                val secretKey = SecretKeySpec(secretKeyBytes, "AES")

                AppLog.d(TAG, "Decrypting Format v3 payload with AES/GCM/NoPadding (${cipherText.size} bytes)...")
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val gcmSpec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
                cipher.updateAAD(header)

                plainTextBytes = try {
                    cipher.doFinal(cipherText)
                } catch (e: AEADBadTagException) {
                    AppLog.e(TAG, "Decryption authentication failed: Incorrect password or corrupted payload.", e)
                    throw IllegalArgumentException("Incorrect password or corrupted backup file", e)
                } catch (e: Exception) {
                    AppLog.e(TAG, "Decryption cipher error: ${e.message}", e)
                    throw IllegalArgumentException("Decryption error: ${e.message}", e)
                }
            } else {
                // Format v2 backward compatibility fallback
                AppLog.i(TAG, "Format v3 magic absent; falling back to Format v2 backward compatibility mode.")
                if (data.size < SALT_LENGTH_V2 + IV_LENGTH_V2) {
                    AppLog.e(TAG, "Payload too small: ${data.size} bytes (minimum expected: ${SALT_LENGTH_V2 + IV_LENGTH_V2})")
                    throw IllegalArgumentException("Invalid backup file: Payload is too small (${data.size} bytes)")
                }

                salt = data.copyOfRange(0, SALT_LENGTH_V2)
                iv = data.copyOfRange(SALT_LENGTH_V2, SALT_LENGTH_V2 + IV_LENGTH_V2)
                val cipherText = data.copyOfRange(SALT_LENGTH_V2 + IV_LENGTH_V2, data.size)

                AppLog.d(TAG, "Deriving AES-256 key via PBKDF2WithHmacSHA256 (iterations=$defaultIterationsV2, Format v2)...")
                val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                val spec = PBEKeySpec(password, salt, defaultIterationsV2, KEY_LENGTH)
                keySpec = spec
                secretKeyBytes = secretKeyFactory.generateSecret(spec).encoded
                val secretKey = SecretKeySpec(secretKeyBytes, "AES")

                AppLog.d(TAG, "Decrypting Format v2 payload with AES/GCM/NoPadding (${cipherText.size} bytes)...")
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val gcmSpec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

                plainTextBytes = try {
                    cipher.doFinal(cipherText)
                } catch (e: AEADBadTagException) {
                    AppLog.e(TAG, "Decryption authentication failed: Incorrect password or corrupted payload.", e)
                    throw IllegalArgumentException("Incorrect password or corrupted backup file", e)
                } catch (e: Exception) {
                    AppLog.e(TAG, "Decryption cipher error: ${e.message}", e)
                    throw IllegalArgumentException("Decryption error: ${e.message}", e)
                }
            }

            val decryptedBytes = plainTextBytes ?: throw IllegalArgumentException("Decryption produced empty payload")
            AppLog.d(TAG, "Decrypted ${decryptedBytes.size} bytes. Parsing JSON payload...")
            val jsonString = String(decryptedBytes, Charsets.UTF_8)

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
        } finally {
            keySpec?.clearPassword()
            plainTextBytes?.fill(0)
            secretKeyBytes?.fill(0)
            salt?.fill(0)
            iv?.fill(0)
        }
    }
}
