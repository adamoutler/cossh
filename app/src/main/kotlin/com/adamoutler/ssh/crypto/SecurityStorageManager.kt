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
        } catch (e: Exception) {
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
        val jsonString = Json.encodeToString(profile)
        encryptedPrefs.edit().putString(profile.id, jsonString).apply()
    }

    fun getProfile(id: String): ConnectionProfile? {
        val jsonString = encryptedPrefs.getString(id, null) ?: return null
        return try {
            Json.decodeFromString<ConnectionProfile>(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    fun getAllProfiles(): List<ConnectionProfile> {
        val profiles = mutableListOf<ConnectionProfile>()
        for ((_, value) in encryptedPrefs.all) {
            if (value is String) {
                try {
                    profiles.add(Json.decodeFromString<ConnectionProfile>(value))
                } catch (e: Exception) {
                    // Log failure or skip corrupted entry
                }
            }
        }
        return profiles
    }

    fun deleteProfile(id: String) {
        encryptedPrefs.edit().remove(id).apply()
    }
}
