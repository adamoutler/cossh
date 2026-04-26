package com.adamoutler.ssh.crypto

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.adamoutler.ssh.data.IdentityProfile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Base64

/**
 * IdentityStorageManager handles secure persistence of IdentityProfiles.
 * Sensitive fields (password, privateKey) are encrypted via hardware-backed Keystore.
 */
class IdentityStorageManager(private val context: Context, injectedPrefs: SharedPreferences? = null) {

    val encryptedPrefs: SharedPreferences by lazy {
        injectedPrefs ?: run {
            try {
                val masterKey = try {
                    MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .setRequestStrongBoxBacked(true)
                        .build()
                } catch (e: Exception) {
                    android.util.Log.w("IdentityStorageManager", "StrongBox unavailable, falling back to standard Keystore")
                    MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()
                }

                EncryptedSharedPreferences.create(
                    context,
                    "secret_ssh_identities",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                val isRobolectric = System.getProperty("robolectric.logging") != null || android.os.Build.FINGERPRINT.contains("robolectric")
                if (isRobolectric) {
                    android.util.Log.e("IdentityStorageManager", "Failed to create EncryptedSharedPreferences, falling back to regular SharedPreferences for Robolectric testing only", e)
                    return@run context.getSharedPreferences("secret_ssh_identities_fallback", Context.MODE_PRIVATE)
                }
                e.handleKeystoreExceptions("Failed to create EncryptedSharedPreferences. Hardware Keystore may be corrupted.")
            }
        }
    }

    fun resetInvalidatedKeys() {
        try {
            val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keyStore.containsAlias("_androidx_security_master_key_")) {
                keyStore.deleteEntry("_androidx_security_master_key_")
            }
            context.getSharedPreferences("secret_ssh_identities", Context.MODE_PRIVATE)
                .edit().clear().commit()
        } catch (e: Exception) {
            android.util.Log.e("IdentityStorageManager", "Failed to reset Keystore", e)
        }
    }

    private fun decryptSensitive(encryptedBase64: String?): ByteArray? {
        if (encryptedBase64 == null) return null
        return try {
            Base64.getDecoder().decode(encryptedBase64)
        } catch (e: Exception) {
            android.util.Log.e("IdentityStorageManager", "Failed to decrypt sensitive field", e)
            null
        }
    }

    fun saveIdentity(identity: IdentityProfile) {
        try {
            val jsonString = Json.encodeToString(identity)
            val editor = encryptedPrefs.edit()
            editor.putString(identity.id, jsonString)
            
            // Encrypt and save password
            if (identity.password != null) {
                editor.putString("${identity.id}_pwd", Base64.getEncoder().encodeToString(identity.password!!))
            } else {
                editor.remove("${identity.id}_pwd")
            }

            // Encrypt and save private key
            if (identity.privateKey != null) {
                editor.putString("${identity.id}_key", Base64.getEncoder().encodeToString(identity.privateKey!!))
            } else {
                editor.remove("${identity.id}_key")
            }
            
            editor.commit()
        } catch (e: Exception) {
            if (e is CryptoException) throw e
            e.handleKeystoreExceptions("Failed to save identity due to Keystore error.")
        }
    }

    fun getIdentity(id: String): IdentityProfile? {
        try {
            val jsonString = encryptedPrefs.getString(id, null) ?: return null
            val identity = Json.decodeFromString<IdentityProfile>(jsonString)
            identity.password = decryptSensitive(encryptedPrefs.getString("${id}_pwd", null))
            identity.privateKey = decryptSensitive(encryptedPrefs.getString("${id}_key", null))
            return identity
        } catch (e: Exception) {
            if (e is CryptoException) throw e
            if (e is kotlinx.serialization.SerializationException || e is IllegalArgumentException) {
                android.util.Log.e("IdentityStorageManager", "Failed to load identity $id", e)
                return null
            }
            e.handleKeystoreExceptions("Failed to retrieve identity due to Keystore error.")
        }
    }

    fun getAllIdentities(): List<IdentityProfile> {
        val identities = mutableListOf<IdentityProfile>()
        try {
            for ((key, _) in encryptedPrefs.all) {
                // Main entries are identified by UUID strings (not ending in _pwd or _key)
                if (!key.endsWith("_pwd") && !key.endsWith("_key")) {
                    getIdentity(key)?.let { identities.add(it) }
                }
            }
        } catch (e: Exception) {
            if (e is CryptoException) throw e
            e.handleKeystoreExceptions("Failed to retrieve identities due to Keystore error.")
        }
        return identities
    }

    fun deleteIdentity(id: String) {
        try {
            encryptedPrefs.edit()
                .remove(id)
                .remove("${id}_pwd")
                .remove("${id}_key")
                .apply()
        } catch (e: Exception) {
            if (e is CryptoException) throw e
            e.handleKeystoreExceptions("Failed to delete identity due to Keystore error.")
        }
    }
}
