package com.adamoutler.ssh.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import java.util.UUID

class AddEditProfileViewModel(
    application: Application,
    private val storageManager: SecurityStorageManager
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application,
        SecurityStorageManager(application)
    )

    fun saveProfile(
        id: String?,
        nickname: String,
        host: String,
        port: String,
        username: String,
        authType: AuthType,
        password: ByteArray?,
        keyReference: String?
    ) {
        val profileId = id ?: UUID.randomUUID().toString()
        val profile = ConnectionProfile(
            id = profileId,
            nickname = nickname,
            host = host,
            port = port.toIntOrNull() ?: 22,
            username = username,
            authType = authType,
            password = password,
            sshKeyPasswordReferenceId = keyReference
        )
        storageManager.saveProfile(profile)
    }

    fun getProfile(id: String): ConnectionProfile? {
        return storageManager.getProfile(id)
    }

    fun getAvailableKeys(): List<String> {
        // Retrieve the dynamic list of available SSH keys from storage.
        // Currently, key persistence is basic/not fully implemented, 
        // so we filter SharedPreferences or return keys if available.
        return storageManager.getAllKeys()
    }
}