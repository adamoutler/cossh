package com.adamoutler.ssh.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import java.util.UUID

class AddEditProfileViewModel(
    application: Application,
    private val storageManager: SecurityStorageManager = SecurityStorageManager(application)
) : AndroidViewModel(application) {

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
}