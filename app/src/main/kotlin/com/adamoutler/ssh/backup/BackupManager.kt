package com.adamoutler.ssh.backup

import android.content.Context
import android.net.Uri
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.crypto.IdentityStorageManager

class BackupManager(
    private val context: Context,
    private val securityStorageManager: SecurityStorageManager,
    private val identityStorageManager: IdentityStorageManager
) {

    fun exportBackup(uri: Uri, password: CharArray) {
        val profiles = securityStorageManager.getAllProfiles()
        val identities = identityStorageManager.getAllIdentities()
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            BackupCryptoManager.exportProfilesToZip(profiles, identities, password, outputStream)
        } ?: throw IllegalStateException("Could not open output stream for URI: $uri")
    }

    fun importBackup(uri: Uri, password: CharArray) {
        val (profiles, identities) = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BackupCryptoManager.importProfilesFromZip(inputStream, password)
        } ?: throw IllegalStateException("Could not open input stream for URI: $uri")

        profiles.forEach { profile ->
            securityStorageManager.saveProfile(profile)
        }
        identities.forEach { identity ->
            identityStorageManager.saveIdentity(identity)
        }
    }
}
