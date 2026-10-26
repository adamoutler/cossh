package com.adamoutler.ssh.crypto

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.EphemeralProfileStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets

class SecurityStorageManager(private val context: Context, injectedPrefs: SharedPreferences? = null) {

    companion object {
        private const val TAG = "SecurityStorageManager"
        const val KEY_AUTOMATION_SECRET = "key_automation_secret"
    }

    val encryptedPrefs: SharedPreferences by lazy {
        injectedPrefs ?: run {
            try {
                val masterKey = try {
                    MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .setRequestStrongBoxBacked(true)
                        .build()
                } catch (e: java.security.ProviderException) {
                    // Log stripped for security
                    MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()
                } catch (e: java.security.KeyStoreException) {
                    // Log stripped for security
                    MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()
                }

                EncryptedSharedPreferences.create(
                    context,
                    // Matches data_extraction_rules exclusion
                    "secret_ssh_profiles",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            } catch (e: Exception) {
                val isRobolectric = try {
                    Class.forName("org.robolectric.Robolectric")
                    true
                } catch (ex: ClassNotFoundException) {
                    false
                }
                if (isRobolectric) {
                    // Log stripped for security
                    return@run context.getSharedPreferences("secret_ssh_profiles_fallback", Context.MODE_PRIVATE)
                }
                e.handleKeystoreExceptions(
                    "Failed to create EncryptedSharedPreferences. Hardware Keystore may be corrupted.",
                )
            }
        }
    }

    fun resetInvalidatedKeys() {
        try {
            val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keyStore.containsAlias("_androidx_security_master_key_")) {
                keyStore.deleteEntry("_androidx_security_master_key_")
            }
            context.getSharedPreferences("secret_ssh_profiles", Context.MODE_PRIVATE)
                .edit().clear().apply()
        } catch (e: Exception) {
            // Log stripped for security
        }
    }

    private fun decryptPassword(base64EncryptedPwd: String?): ByteArray? {
        if (base64EncryptedPwd == null) return null
        return java.util.Base64.getDecoder().decode(base64EncryptedPwd)
    }

    fun saveProfile(profile: ConnectionProfile) {
        if (profile.isEphemeral) {
            throw IllegalArgumentException("Cannot persist ephemeral connection profile")
        }
        try {
            val jsonString = Json.encodeToString(profile)
            val editor = encryptedPrefs.edit()
            editor.putString(profile.id, jsonString)

            if (profile.password != null) {
                val base64Password = java.util.Base64.getEncoder().encodeToString(profile.password!!)
                editor.putString("${profile.id}_pwd", base64Password)
            } else {
                editor.remove("${profile.id}_pwd")
            }
            editor.apply() // Asynchronous background write. In-memory state is updated immediately.
        } catch (e: Exception) {
            if (e is CryptoException || e is IllegalArgumentException) throw e
            e.handleKeystoreExceptions("Failed to save profile due to Keystore error.")
        }
    }

    fun getProfile(id: String): ConnectionProfile? {
        if (id.startsWith("ephemeral_") || EphemeralProfileStore.get(id) != null) {
            return EphemeralProfileStore.get(id)
        }
        try {
            val jsonString = encryptedPrefs.getString(id, null) ?: return null
            val profile = Json.decodeFromString<ConnectionProfile>(jsonString)
            profile.password = decryptPassword(encryptedPrefs.getString("${id}_pwd", null))
            return profile
        } catch (e: kotlinx.serialization.SerializationException) {
            // Log stripped for security
            return null
        } catch (e: IllegalArgumentException) {
            // Log stripped for security
            return null
        } catch (e: Exception) {
            if (e is CryptoException) throw e
            e.handleKeystoreExceptions("Failed to retrieve profile due to Keystore error.")
        }
    }

    fun getAllProfiles(): List<ConnectionProfile> {
        val profiles = mutableListOf<ConnectionProfile>()
        try {
            for ((key, value) in encryptedPrefs.all) {
                // Skip password entries, key entries, and security metadata
                if (value is String && !key.endsWith("_pwd") && !key.startsWith("key_") && !key.startsWith("app_lock_") && key != "sync_passphrase") {
                    try {
                        val profile = Json.decodeFromString<ConnectionProfile>(value)
                        // Metadata-only loading: DO NOT hydrate the password here. (SSH-138)
                        // profile.password = decryptPassword(encryptedPrefs.getString("${profile.id}_pwd", null))
                        profiles.add(profile)
                    } catch (e: kotlinx.serialization.SerializationException) {
                        // Log stripped for security
                    } catch (e: IllegalArgumentException) {
                        // Log stripped for security
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CryptoException) throw e
            e.handleKeystoreExceptions("Failed to retrieve profiles due to Keystore error.")
        }
        return profiles
    }

    fun deleteProfile(id: String) {
        if (id.startsWith("ephemeral_") || EphemeralProfileStore.get(id) != null) {
            EphemeralProfileStore.remove(id)
            return
        }
        try {
            encryptedPrefs.edit()
                .remove(id)
                .remove("${id}_pwd")
                .apply()
        } catch (e: Exception) {
            if (e is CryptoException) throw e
            e.handleKeystoreExceptions("Failed to delete profile due to Keystore error.")
        }
    }

    fun saveSyncPassphrase(passphrase: CharArray) {
        val encoder = StandardCharsets.UTF_8.newEncoder()
        val charBuffer = CharBuffer.wrap(passphrase)
        val byteBuffer = ByteBuffer.allocate((passphrase.size * encoder.maxBytesPerChar()).toInt())
        var rawBytes: ByteArray? = null
        try {
            encoder.encode(charBuffer, byteBuffer, true)
            encoder.flush(byteBuffer)
            byteBuffer.flip()
            rawBytes = ByteArray(byteBuffer.remaining())
            byteBuffer.get(rawBytes)
            val base64Pass = java.util.Base64.getEncoder().encodeToString(rawBytes)
            encryptedPrefs.edit().putString("sync_passphrase", base64Pass).apply()
        } finally {
            if (byteBuffer.hasArray()) byteBuffer.array().fill(0)
            rawBytes?.fill(0)
        }
    }

    fun getSyncPassphrase(): CharArray? {
        val base64Pass = encryptedPrefs.getString("sync_passphrase", null) ?: return null
        val decodedBytes = try {
            java.util.Base64.getDecoder().decode(base64Pass)
        } catch (_: Exception) {
            return null
        }
        val decoder = StandardCharsets.UTF_8.newDecoder()
        val byteBuffer = ByteBuffer.wrap(decodedBytes)
        val charBuffer = CharBuffer.allocate((decodedBytes.size * decoder.maxCharsPerByte()).toInt())
        return try {
            decoder.decode(byteBuffer, charBuffer, true)
            decoder.flush(charBuffer)
            charBuffer.flip()
            val resultChars = CharArray(charBuffer.remaining())
            charBuffer.get(resultChars)
            resultChars
        } catch (_: Exception) {
            null
        } finally {
            decodedBytes.fill(0)
            if (charBuffer.hasArray()) charBuffer.array().fill('\u0000')
        }
    }

    fun clearSyncPassphrase() {
        encryptedPrefs.edit().remove("sync_passphrase").apply()
    }

    fun setupAppLock(cipher: javax.crypto.Cipher) {
        val token = ByteArray(32)
        java.security.SecureRandom().nextBytes(token)
        try {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val tokenSha256 = md.digest(token)
            val ciphertext = cipher.doFinal(token)
            val iv = cipher.iv ?: throw IllegalStateException("Cipher IV must not be null")

            val encoder = java.util.Base64.getEncoder()
            encryptedPrefs.edit()
                .putString("app_lock_iv", encoder.encodeToString(iv))
                .putString("app_lock_ciphertext", encoder.encodeToString(ciphertext))
                .putString("app_lock_token_sha256", encoder.encodeToString(tokenSha256))
                .apply()
        } finally {
            token.fill(0)
        }
    }

    fun verifyAppLockToken(cipher: javax.crypto.Cipher): Boolean {
        var decryptedToken: ByteArray? = null
        return try {
            val ciphertextB64 = encryptedPrefs.getString("app_lock_ciphertext", null) ?: return false
            val tokenShaB64 = encryptedPrefs.getString("app_lock_token_sha256", null) ?: return false
            val decoder = java.util.Base64.getDecoder()
            val ciphertext = decoder.decode(ciphertextB64)
            val expectedHash = decoder.decode(tokenShaB64)

            val token = cipher.doFinal(ciphertext) ?: return false
            decryptedToken = token
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val actualHash = md.digest(token)
            java.security.MessageDigest.isEqual(actualHash, expectedHash)
        } catch (e: Exception) {
            false
        } finally {
            decryptedToken?.fill(0)
        }
    }

    fun getAppLockIv(): ByteArray? {
        val ivB64 = encryptedPrefs.getString("app_lock_iv", null) ?: return null
        return java.util.Base64.getDecoder().decode(ivB64)
    }

    fun isAppLockConfigured(): Boolean = encryptedPrefs.contains("app_lock_ciphertext") &&
        encryptedPrefs.contains("app_lock_iv") &&
        encryptedPrefs.contains("app_lock_token_sha256")

    fun clearAppLock() {
        encryptedPrefs.edit()
            .remove("app_lock_iv")
            .remove("app_lock_ciphertext")
            .remove("app_lock_token_sha256")
            .apply()
    }

    fun getAutomationSecretKey(): ByteArray? {
        val b64 = encryptedPrefs.getString(KEY_AUTOMATION_SECRET, null) ?: return null
        return try {
            java.util.Base64.getDecoder().decode(b64)
        } catch (_: Exception) {
            null
        }
    }

    fun generateOrGetAutomationSecretKey(): ByteArray {
        val existing = getAutomationSecretKey()
        if (existing != null) return existing
        return rotateAutomationSecretKey()
    }

    fun rotateAutomationSecretKey(): ByteArray {
        val newKey = ByteArray(32)
        java.security.SecureRandom().nextBytes(newKey)
        val b64 = java.util.Base64.getEncoder().encodeToString(newKey)
        encryptedPrefs.edit().putString(KEY_AUTOMATION_SECRET, b64).apply()
        return newKey
    }
}
