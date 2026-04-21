package com.adamoutler.ssh.ui.screens

import android.app.Application
import com.adamoutler.ssh.crypto.IdentityStorageManager
import com.adamoutler.ssh.data.IdentityProfile
import com.adamoutler.ssh.ui.base.BaseAndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    init {
        loadIdentities()
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
