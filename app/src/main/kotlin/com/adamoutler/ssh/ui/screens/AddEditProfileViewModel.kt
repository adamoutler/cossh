package com.adamoutler.ssh.ui.screens

import android.app.Application
import com.adamoutler.ssh.crypto.IdentityStorageManager
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.IdentityProfile
import com.adamoutler.ssh.ui.base.BaseAndroidViewModel
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

import com.adamoutler.ssh.data.PortForwardConfig
import com.adamoutler.ssh.data.PortForwardType

data class ProfileFormState(
    val nickname: String = "",
    val host: String = "",
    val port: String = "22",
    val username: String = "",
    val authType: AuthType = AuthType.PASSWORD,
    val originalPassword: ByteArray? = null,
    val isPasswordLocked: Boolean = false,
    val password: String = "",
    val keyReference: String = "",
    val identityId: String? = null,
    val envVarsText: String = "",
    val portForwardsText: String = "",
    val isLoaded: Boolean = false
)

class AddEditProfileViewModel(
    application: Application,
    private val storageManager: SecurityStorageManager,
    private val identityStorageManager: IdentityStorageManager
) : BaseAndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ProfileFormState())
    val uiState: StateFlow<ProfileFormState> = _uiState.asStateFlow()

    constructor(application: Application) : this(
        application,
        SecurityStorageManager(application),
        IdentityStorageManager(application)
    )

    fun updateState(updater: (ProfileFormState) -> ProfileFormState) {
        _uiState.update(updater)
    }

    fun loadProfileIfNeeded(profileId: String?) {
        if (_uiState.value.isLoaded) return
        if (profileId != null) {
            val profile = getProfile(profileId)
            if (profile != null) {
                _uiState.update {
                    it.copy(
                        nickname = profile.nickname,
                        host = profile.host,
                        port = profile.port.toString(),
                        username = profile.username,
                        authType = profile.authType,
                        originalPassword = profile.password,
                        isPasswordLocked = profile.password != null,
                        keyReference = profile.sshKeyPasswordReferenceId ?: "",
                        identityId = profile.identityId,
                        envVarsText = profile.envVars.entries.joinToString(",") { entry -> "${entry.key}=${entry.value}" },
                        portForwardsText = profile.portForwards.joinToString(",") { pf ->
                            val prefix = if (pf.type == PortForwardType.LOCAL) "L" else "R"
                            "$prefix:${pf.localPort}:${pf.remoteHost}:${pf.remotePort}"
                        },
                        isLoaded = true
                    )
                }
            }
        } else {
            _uiState.update { it.copy(isLoaded = true) }
        }
    }

    fun resetState() {
        _uiState.value = ProfileFormState()
    }

    fun saveProfile(
        id: String?,
        nickname: String,
        host: String,
        port: String,
        username: String,
        authType: AuthType,
        password: ByteArray?,
        keyReference: String?,
        identityId: String? = null,
        envVars: Map<String, String> = emptyMap(),
        portForwards: List<PortForwardConfig> = emptyList()
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
            identityId = identityId,
            envVars = envVars,
            portForwards = portForwards
        )
        storageManager.saveProfile(profile)
        resetState()
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