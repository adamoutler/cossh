package com.adamoutler.ssh.crypto

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.adamoutler.ssh.data.ConnectionProfile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SecurityStorageManager(private val context: Context, injectedPrefs: SharedPreferences? = null) {

    companion object {
        private const val TAG = "SecurityStorageManager"
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
            if (e is CryptoException) throw e
            e.handleKeystoreExceptions("Failed to save profile due to Keystore error.")
        }
    }

    fun getProfile(id: String): ConnectionProfile? {
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
                // Skip password entries and key entries
                if (value is String && !key.endsWith("_pwd") && !key.startsWith("key_")) {
                    try {
                        val profile = Json.decodeFromString<ConnectionProfile>(value)
                        profile.password = decryptPassword(encryptedPrefs.getString("${profile.id}_pwd", null))
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

    fun getAllKeys(): List<String> {
        val keys = mutableListOf<String>()
        try {
            // Keys will be stored with a specific prefix when implemented,
            // or for now we can just search for anything containing "key" that isn't a profile.
            // Assuming keys will be saved as "key_{id}"
            for ((key, _) in encryptedPrefs.all) {
                if (key.startsWith("key_")) {
                    keys.add(key)
                }
            }
        } catch (e: Exception) {
            if (e is CryptoException) throw e
            e.handleKeystoreExceptions("Failed to retrieve keys due to Keystore error.")
        }
        return keys
    }

    fun deleteProfile(id: String) {
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
        val base64Pass = java.util.Base64.getEncoder().encodeToString(String(passphrase).toByteArray(Charsets.UTF_8))
        encryptedPrefs.edit().putString("sync_passphrase", base64Pass).apply()
    }

    fun getSyncPassphrase(): CharArray? {
        val base64Pass = encryptedPrefs.getString("sync_passphrase", null) ?: return null
        return String(java.util.Base64.getDecoder().decode(base64Pass), Charsets.UTF_8).toCharArray()
    }
}
