package com.adamoutler.ssh.ui.screens

import android.app.Application
import com.adamoutler.ssh.crypto.IdentityStorageManager
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.IdentityProfile
import com.adamoutler.ssh.ui.base.BaseAndroidViewModel
import java.util.UUID

class AddEditProfileViewModel(
    application: Application,
    private val storageManager: SecurityStorageManager,
    private val identityStorageManager: IdentityStorageManager
) : BaseAndroidViewModel(application) {

    constructor(application: Application) : this(
        application,
        SecurityStorageManager(application),
        IdentityStorageManager(application)
    )

    fun saveProfile(
        id: String?,
        nickname: String,
        host: String,
        port: String,
        username: String,
        authType: AuthType,
        password: ByteArray?,
        keyReference: String?,
        identityId: String? = null
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
            sshKeyPasswordReferenceId = keyReference,
            identityId = identityId
        )
        storageManager.saveProfile(profile)
    }

    fun getProfile(id: String): ConnectionProfile? {
        return storageManager.getProfile(id)
    }

    fun getAvailableKeys(): List<String> {
        return storageManager.getAllKeys()
    }

    fun getIdentities(): List<IdentityProfile> {
        return identityStorageManager.getAllIdentities()
    }
}