package com.adamoutler.ssh.ui.screens

import android.app.Application
import com.adamoutler.ssh.crypto.IdentityStorageManager
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.IdentityProfile
import com.adamoutler.ssh.ui.base.BaseAndroidViewModel
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class IdentityFormState(
    val name: String = "",
    val username: String = "",
    val authType: AuthType = AuthType.PASSWORD,
    val originalPassword: ByteArray? = null,
    val isPasswordLocked: Boolean = false,
    val password: String = "",
    val publicKey: String = "",
    val privateKey: ByteArray? = null,
    val manualKeyEntry: Boolean = false,
    val manualPrivKey: String = "",
    val isLoaded: Boolean = false
)

class IdentityViewModel(
    application: Application,
    private val storageManager: IdentityStorageManager
) : BaseAndroidViewModel(application) {

    constructor(application: Application) : this(
        application,
        IdentityStorageManager(application)
    )

    private val _identities = MutableStateFlow<List<IdentityProfile>>(emptyList())
    val identities: StateFlow<List<IdentityProfile>> = _identities.asStateFlow()

    private val _uiState = MutableStateFlow(IdentityFormState())
    val uiState: StateFlow<IdentityFormState> = _uiState.asStateFlow()

    init {
        loadIdentities()
    }

    fun updateState(updater: (IdentityFormState) -> IdentityFormState) {
        _uiState.update(updater)
    }

    fun loadIdentityIfNeeded(identityId: String?) {
        if (_uiState.value.isLoaded) return
        if (identityId != null) {
            val identity = getIdentity(identityId)
            if (identity != null) {
                _uiState.update {
                    it.copy(
                        name = identity.name,
                        username = identity.username,
                        authType = identity.authType,
                        originalPassword = identity.password,
                        isPasswordLocked = identity.password != null,
                        publicKey = identity.publicKey ?: "",
                        privateKey = identity.privateKey,
                        isLoaded = true
                    )
                }
            }
        } else {
            _uiState.update { it.copy(isLoaded = true) }
        }
    }

    fun resetState() {
        _uiState.value = IdentityFormState()
    }

    fun loadIdentities() {
        launchWithHandler {
            _identities.value = storageManager.getAllIdentities()
        }
    }

    fun saveIdentity(identity: IdentityProfile) {
        launchWithHandler {
            storageManager.saveIdentity(identity)
            loadIdentities()
        }
    }

    fun deleteIdentity(id: String) {
        launchWithHandler {
            storageManager.deleteIdentity(id)
            loadIdentities()
        }
    }

    fun getIdentity(id: String): IdentityProfile? {
        return storageManager.getIdentity(id)
    }
}
