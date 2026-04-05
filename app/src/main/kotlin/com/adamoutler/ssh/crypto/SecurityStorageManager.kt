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
    }

    fun saveProfile(profile: ConnectionProfile) {
        val safeProfile = if (profile.password != null) {
            profile.copy(password = PasswordCipher.encrypt(profile.password))
        } else {
            profile
        }
        val jsonString = Json.encodeToString(safeProfile)
        encryptedPrefs.edit().putString(profile.id, jsonString).apply()
    }

    fun getProfile(id: String): ConnectionProfile? {
        val jsonString = encryptedPrefs.getString(id, null) ?: return null
        return try {
            val profile = Json.decodeFromString<ConnectionProfile>(jsonString)
            if (profile.password != null) {
                profile.copy(password = PasswordCipher.decrypt(profile.password))
            } else {
                profile
            }
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
        for ((_, value) in encryptedPrefs.all) {
            if (value is String) {
                try {
                    val profile = Json.decodeFromString<ConnectionProfile>(value)
                    if (profile.password != null) {
                        profiles.add(profile.copy(password = PasswordCipher.decrypt(profile.password)))
                    } else {
                        profiles.add(profile)
                    }
                } catch (e: kotlinx.serialization.SerializationException) {
                    android.util.Log.e("SecurityStorageManager", "Failed to deserialize profile during list generation", e)
                } catch (e: IllegalArgumentException) {
                    android.util.Log.e("SecurityStorageManager", "Invalid profile format during list generation", e)
                }
            }
        }
        return profiles
    }

    fun deleteProfile(id: String) {
        encryptedPrefs.edit().remove(id).apply()
    }
}
