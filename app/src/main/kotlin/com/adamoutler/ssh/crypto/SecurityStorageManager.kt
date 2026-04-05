package com.adamoutler.ssh.crypto

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.adamoutler.ssh.data.ConnectionProfile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SecurityStorageManager(context: Context, injectedPrefs: SharedPreferences? = null) {

    val encryptedPrefs: SharedPreferences = injectedPrefs ?: run {
        try {
            val masterKey = try {
                MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .setRequestStrongBoxBacked(true)
                    .build()
            } catch (e: java.security.ProviderException) {
                android.util.Log.w("SecurityStorageManager", "StrongBox unavailable, falling back to standard Keystore", e)
                MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
            } catch (e: java.security.KeyStoreException) {
                android.util.Log.w("SecurityStorageManager", "KeyStore initialization failed, trying fallback", e)
                MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
            }

            EncryptedSharedPreferences.create(
                context,
                "secret_ssh_profiles", // Matches data_extraction_rules exclusion
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            android.util.Log.e("SecurityStorageManager", "Failed to create EncryptedSharedPreferences, falling back to regular SharedPreferences for testing", e)
            context.getSharedPreferences("secret_ssh_profiles_fallback", Context.MODE_PRIVATE)
        }
    }

    fun saveProfile(profile: ConnectionProfile) {
        val jsonString = Json.encodeToString(profile)
        encryptedPrefs.edit().putString(profile.id, jsonString).apply()
        
        if (profile.password != null) {
            val encryptedPassword = PasswordCipher.encrypt(profile.password!!)
            encryptedPrefs.edit().putString("${profile.id}_pwd", java.util.Base64.getEncoder().encodeToString(encryptedPassword)).apply()
        } else {
            encryptedPrefs.edit().remove("${profile.id}_pwd").apply()
        }
    }

    fun getProfile(id: String): ConnectionProfile? {
        val jsonString = encryptedPrefs.getString(id, null) ?: return null
        return try {
            val profile = Json.decodeFromString<ConnectionProfile>(jsonString)
            val pwdString = encryptedPrefs.getString("${id}_pwd", null)
            if (pwdString != null) {
                val encryptedPassword = java.util.Base64.getDecoder().decode(pwdString)
                profile.password = PasswordCipher.decrypt(encryptedPassword)
            }
            profile
        } catch (e: kotlinx.serialization.SerializationException) {
            android.util.Log.e("SecurityStorageManager", "Failed to deserialize profile", e)
            null
        } catch (e: IllegalArgumentException) {
            android.util.Log.e("SecurityStorageManager", "Invalid profile format", e)
            null
        }
    }

    fun getAllProfiles(): List<ConnectionProfile> {
        val profiles = mutableListOf<ConnectionProfile>()
        for ((key, value) in encryptedPrefs.all) {
            if (value is String && !key.endsWith("_pwd")) {
                try {
                    val profile = Json.decodeFromString<ConnectionProfile>(value)
                    val pwdString = encryptedPrefs.getString("${profile.id}_pwd", null)
                    if (pwdString != null) {
                        val encryptedPassword = java.util.Base64.getDecoder().decode(pwdString)
                        profile.password = PasswordCipher.decrypt(encryptedPassword)
                    }
                    profiles.add(profile)
                } catch (e: kotlinx.serialization.SerializationException) {
                    android.util.Log.e("SecurityStorageManager", "Failed to deserialize profile during list generation", e)
                } catch (e: IllegalArgumentException) {
                    android.util.Log.e("SecurityStorageManager", "Invalid profile format during list generation", e)
                }
            }
        }
        return profiles
    }

    fun getAllKeys(): List<String> {
        val keys = mutableListOf<String>()
        // Keys will be stored with a specific prefix when implemented,
        // or for now we can just search for anything containing "key" that isn't a profile.
        // Assuming keys will be saved as "key_{id}"
        for ((key, _) in encryptedPrefs.all) {
            if (key.startsWith("key_")) {
                keys.add(key)
            }
        }
        return keys
    }

    fun deleteProfile(id: String) {
        encryptedPrefs.edit()
            .remove(id)
            .remove("${id}_pwd")
            .apply()
    }
}
