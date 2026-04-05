package com.adamoutler.ssh.backup

import android.content.Context
import android.net.Uri
import com.adamoutler.ssh.crypto.SecurityStorageManager

class BackupManager(
    private val context: Context,
    private val securityStorageManager: SecurityStorageManager
) {

    fun exportBackup(uri: Uri, password: CharArray) {
        val profiles = securityStorageManager.getAllProfiles()
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            BackupCryptoManager.exportProfilesToZip(profiles, password, outputStream)
        } ?: throw IllegalStateException("Could not open output stream for URI: $uri")
    }

    fun importBackup(uri: Uri, password: CharArray) {
        val profiles = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BackupCryptoManager.importProfilesFromZip(inputStream, password)
        } ?: throw IllegalStateException("Could not open input stream for URI: $uri")

        profiles.forEach { profile ->
            securityStorageManager.saveProfile(profile)
        }
    }
}
