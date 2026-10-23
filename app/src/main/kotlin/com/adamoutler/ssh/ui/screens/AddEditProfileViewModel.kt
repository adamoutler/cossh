package com.adamoutler.ssh.ui.screens

import android.app.Application
import com.adamoutler.ssh.crypto.IdentityStorageManager
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.IdentityProfile
import com.adamoutler.ssh.data.KeepScreenOnMode
import com.adamoutler.ssh.data.PortForwardConfig
import com.adamoutler.ssh.data.Protocol
import com.adamoutler.ssh.ui.base.BaseAndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

data class ProfileFormState(
    val nickname: String = "",
    val host: String = "",
    val port: String = "22",
    val protocol: Protocol = Protocol.SSH,
    val username: String = "",
    val authType: AuthType = AuthType.PASSWORD,
    val originalPassword: ByteArray? = null,
    val isPasswordLocked: Boolean = false,
    val password: String = "",
    val keyReference: String = "",
    val identityId: String? = null,
    val envVarsText: String = "",
    val portForwards: List<PortForwardConfig> = emptyList(),
    val commandSnippets: List<com.adamoutler.ssh.data.CommandSnippet> = emptyList(),
    val initialDirectory: String = "",
    val terminalInputState: Int = 0,
    val keepScreenOnMode: KeepScreenOnMode = KeepScreenOnMode.SYSTEM_DEFAULT,
    val useLocalDns: Boolean = false,
    val keepAliveIntervalSeconds: Int = 15,
    val autoReconnect: Boolean = true,
    val maxReconnectAttempts: Int = 5,
    val jumpHostProfileId: String? = null,
    val isLoaded: Boolean = false,
    val hostError: String? = null,
    val portError: String? = null,
)

data class ResolvedCredentials(
    val username: String,
    val authType: AuthType,
    val password: ByteArray?,
    val keyReference: String?,
    val identityId: String?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ResolvedCredentials) return false
        if (username != other.username) return false
        if (authType != other.authType) return false
        if (password != null) {
            if (other.password == null) return false
            if (!password.contentEquals(other.password)) return false
        } else if (other.password != null) {
            return false
        }
        if (keyReference != other.keyReference) return false
        if (identityId != other.identityId) return false
        return true
    }

    override fun hashCode(): Int {
        var result = username.hashCode()
        result = 31 * result + authType.hashCode()
        result = 31 * result + (password?.contentHashCode() ?: 0)
        result = 31 * result + (keyReference?.hashCode() ?: 0)
        result = 31 * result + (identityId?.hashCode() ?: 0)
        return result
    }
}

class AddEditProfileViewModel(
    application: Application,
    private val storageManager: SecurityStorageManager,
    private val identityStorageManager: IdentityStorageManager,
) : BaseAndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ProfileFormState())
    val uiState: StateFlow<ProfileFormState> = _uiState.asStateFlow()

    constructor(application: Application) : this(
        application,
        SecurityStorageManager(application),
        IdentityStorageManager(application),
    )

    fun updateState(updater: (ProfileFormState) -> ProfileFormState) {
        _uiState.update(updater)
    }

    fun setProtocol(newProto: Protocol) {
        _uiState.update {
            val newPort = if (newProto == Protocol.TELNET && it.port == "22") {
                "23"
            } else if (newProto == Protocol.SSH && it.port == "23") {
                "22"
            } else {
                it.port
            }
            it.copy(
                protocol = newProto,
                port = newPort,
                authType = if (newProto == Protocol.TELNET) AuthType.PASSWORD else it.authType,
            )
        }
    }

    fun validateHost(host: String): Boolean = host.trim().isNotEmpty()

    fun validatePort(port: String): Boolean {
        val portInt = port.toIntOrNull()
        return portInt != null && portInt in 1..65535
    }

    fun parseEnvVars(envVarsText: String): Map<String, String> {
        if (envVarsText.isBlank()) return emptyMap()
        return envVarsText.split(",")
            .map { it.trim() }
            .filter { it.contains("=") }
            .associate {
                val parts = it.split("=", limit = 2)
                parts[0].trim() to parts[1].trim()
            }
    }

    fun resolveCredentials(state: ProfileFormState = _uiState.value): ResolvedCredentials {
        val selectedIdent = state.identityId?.let { identityStorageManager.getIdentity(it) }
        val finalUsername = if (selectedIdent != null && state.protocol == Protocol.SSH) {
            selectedIdent.username
        } else {
            state.username
        }
        val finalAuthType = if (state.protocol == Protocol.TELNET) {
            AuthType.PASSWORD
        } else if (selectedIdent != null && state.protocol == Protocol.SSH) {
            selectedIdent.authType
        } else {
            state.authType
        }

        val passBytes = if ((state.identityId == null || state.protocol == Protocol.TELNET) && finalAuthType == AuthType.PASSWORD) {
            if (state.isPasswordLocked) {
                state.originalPassword
            } else if (state.password.isNotEmpty()) {
                state.password.toByteArray(Charsets.UTF_8)
            } else {
                null
            }
        } else {
            null
        }

        val keyRef = if ((state.identityId == null || state.protocol == Protocol.TELNET) && finalAuthType == AuthType.KEY) {
            state.keyReference.ifEmpty { null }
        } else {
            null
        }

        val finalIdentityId = if (state.protocol == Protocol.SSH) state.identityId else null

        return ResolvedCredentials(
            username = finalUsername,
            authType = finalAuthType,
            password = passBytes,
            keyReference = keyRef,
            identityId = finalIdentityId,
        )
    }

    fun validateForm(state: ProfileFormState = _uiState.value): Boolean {
        val hostErr = if (!validateHost(state.host)) "Host cannot be empty" else null
        val portErr = if (!validatePort(state.port)) "Port must be between 1 and 65535" else null

        _uiState.update {
            it.copy(
                hostError = hostErr,
                portError = portErr,
            )
        }

        return hostErr == null && portErr == null
    }

    fun saveProfile(profileId: String? = null): Boolean = saveProfileFromState(profileId)

    fun saveProfileFromState(id: String? = null): Boolean {
        val currentState = _uiState.value
        if (!validateForm(currentState)) {
            return false
        }

        val creds = resolveCredentials(currentState)
        val parsedEnvVars = parseEnvVars(currentState.envVarsText)
        val portInt = currentState.port.toIntOrNull() ?: if (currentState.protocol == Protocol.TELNET) 23 else 22

        val profileId = id ?: UUID.randomUUID().toString()
        val profile = ConnectionProfile(
            id = profileId,
            nickname = currentState.nickname,
            host = currentState.host.trim(),
            port = portInt,
            protocol = currentState.protocol,
            username = creds.username,
            authType = creds.authType,
            password = creds.password,
            sshKeyPasswordReferenceId = creds.keyReference,
            identityId = creds.identityId,
            envVars = parsedEnvVars,
            portForwards = currentState.portForwards,
            commandSnippets = currentState.commandSnippets,
            initialDirectory = currentState.initialDirectory.takeIf { it.isNotBlank() },
            terminalInputState = currentState.terminalInputState,
            keepScreenOnMode = currentState.keepScreenOnMode,
            useLocalDns = currentState.useLocalDns,
            keepAliveIntervalSeconds = currentState.keepAliveIntervalSeconds,
            autoReconnect = currentState.autoReconnect,
            maxReconnectAttempts = currentState.maxReconnectAttempts,
            jumpHostProfileId = currentState.jumpHostProfileId,
        )
        storageManager.saveProfile(profile)
        resetState()
        return true
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
                        protocol = profile.protocol,
                        username = profile.username,
                        authType = profile.authType,
                        originalPassword = profile.password,
                        isPasswordLocked = profile.password != null,
                        keyReference = profile.sshKeyPasswordReferenceId ?: "",
                        identityId = profile.identityId,
                        envVarsText = profile.envVars.entries.joinToString(",") { entry -> "${entry.key}=${entry.value}" },
                        portForwards = profile.portForwards,
                        commandSnippets = profile.commandSnippets,
                        initialDirectory = profile.initialDirectory ?: "",
                        terminalInputState = profile.terminalInputState,
                        keepScreenOnMode = profile.keepScreenOnMode,
                        useLocalDns = profile.useLocalDns,
                        keepAliveIntervalSeconds = profile.keepAliveIntervalSeconds,
                        autoReconnect = profile.autoReconnect,
                        maxReconnectAttempts = profile.maxReconnectAttempts,
                        jumpHostProfileId = profile.jumpHostProfileId,
                        isLoaded = true,
                        hostError = null,
                        portError = null,
                    )
                }
            }
        } else {
            _uiState.update { it.copy(isLoaded = true) }
        }
    }

    fun resetState() {
        _uiState.value.originalPassword?.fill(0)
        _uiState.value = ProfileFormState()
    }

    override fun onCleared() {
        resetState()
        super.onCleared()
    }

    fun saveProfile(
        id: String?,
        nickname: String,
        host: String,
        port: String,
        protocol: Protocol,
        username: String,
        authType: AuthType,
        password: ByteArray?,
        keyReference: String?,
        identityId: String? = null,
        envVars: Map<String, String> = emptyMap(),
        portForwards: List<PortForwardConfig> = emptyList(),
        commandSnippets: List<com.adamoutler.ssh.data.CommandSnippet> = emptyList(),
        initialDirectory: String? = null,
        terminalInputState: Int = 0,
        keepScreenOnMode: KeepScreenOnMode = KeepScreenOnMode.SYSTEM_DEFAULT,
        useLocalDns: Boolean = false,
        keepAliveIntervalSeconds: Int = 15,
        autoReconnect: Boolean = true,
        maxReconnectAttempts: Int = 5,
        jumpHostProfileId: String? = null,
    ) {
        val portInt = if (validatePort(port)) {
            port.toInt()
        } else {
            if (protocol == Protocol.TELNET) 23 else 22
        }
        val profileId = id ?: UUID.randomUUID().toString()
        val profile = ConnectionProfile(
            id = profileId,
            nickname = nickname,
            host = host.trim(),
            port = portInt,
            protocol = protocol,
            username = username,
            authType = authType,
            password = password,
            sshKeyPasswordReferenceId = keyReference,
            identityId = identityId,
            envVars = envVars,
            portForwards = portForwards,
            commandSnippets = commandSnippets,
            initialDirectory = initialDirectory?.takeIf { it.isNotBlank() },
            terminalInputState = terminalInputState,
            keepScreenOnMode = keepScreenOnMode,
            useLocalDns = useLocalDns,
            keepAliveIntervalSeconds = keepAliveIntervalSeconds,
            autoReconnect = autoReconnect,
            maxReconnectAttempts = maxReconnectAttempts,
            jumpHostProfileId = jumpHostProfileId,
        )
        storageManager.saveProfile(profile)
        resetState()
    }

    fun getProfile(id: String): ConnectionProfile? = storageManager.getProfile(id)

    fun getAllProfiles(): List<ConnectionProfile> = storageManager.getAllProfiles()

    fun getIdentities(): List<IdentityProfile> = identityStorageManager.getAllIdentities()
}
