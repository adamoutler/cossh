package com.adamoutler.ssh.backup

import android.content.Context
import android.net.Uri
import com.adamoutler.ssh.crypto.IdentityStorageManager
import com.adamoutler.ssh.crypto.SecurityStorageManager

import com.adamoutler.ssh.util.AppLog

class BackupManager(
    private val context: Context,
    private val securityStorageManager: SecurityStorageManager,
    private val identityStorageManager: IdentityStorageManager,
) {
    companion object {
        private const val TAG = "CoSSH:BackupManager"
    }

    fun exportBackup(uri: Uri, password: CharArray) {
        AppLog.i(TAG, "Starting backup export to URI: $uri")
        // Fetch fully hydrated profiles and identities for export (SSH-138)
        val rawProfiles = securityStorageManager.getAllProfiles().mapNotNull { securityStorageManager.getProfile(it.id) }
        val rawIdentities = identityStorageManager.getAllIdentities().mapNotNull { identityStorageManager.getIdentity(it.id) }
        AppLog.i(TAG, "Loaded ${rawProfiles.size} profiles and ${rawIdentities.size} identities from storage.")

        val outputStream = context.contentResolver.openOutputStream(uri)
            ?: throw IllegalStateException("Could not open output stream for URI: $uri. Check storage permissions.")

        outputStream.use { stream ->
            BackupCryptoManager.exportProfilesToZip(rawProfiles, rawIdentities, password, stream)
        }
        AppLog.i(TAG, "Backup export completed successfully.")
    }

    fun importBackup(uri: Uri, password: CharArray): Pair<Int, Int> {
        AppLog.i(TAG, "Starting backup import from URI: $uri")
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Could not open input stream for URI: $uri. File may no longer exist or permission was revoked.")

        val (profiles, identities) = inputStream.use { stream ->
            BackupCryptoManager.importProfilesFromZip(stream, password)
        }

        AppLog.i(TAG, "Ready to persist ${identities.size} identities and ${profiles.size} profiles.")

        var identitiesSaved = 0
        for (identity in identities) {
            try {
                AppLog.d(TAG, "Saving identity: '${identity.name}' (${identity.id})")
                identityStorageManager.saveIdentity(identity)
                identitiesSaved++
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to save identity '${identity.name}' (${identity.id}): ${e.message}", e)
                throw IllegalStateException("Failed to persist identity '${identity.name}': ${e.message}", e)
            }
        }

        var profilesSaved = 0
        for (profile in profiles) {
            try {
                AppLog.d(TAG, "Saving profile: '${profile.nickname}' (${profile.id}) -> ${profile.host}:${profile.port}")
                securityStorageManager.saveProfile(profile)
                profilesSaved++
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to save profile '${profile.nickname}' (${profile.id}): ${e.message}", e)
                throw IllegalStateException("Failed to persist profile '${profile.nickname}': ${e.message}", e)
            }
        }

        AppLog.i(TAG, "Import completed successfully: $profilesSaved profiles and $identitiesSaved identities saved.")
        return Pair(profilesSaved, identitiesSaved)
    }
}
