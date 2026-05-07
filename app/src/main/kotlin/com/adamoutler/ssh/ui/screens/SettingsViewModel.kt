package com.adamoutler.ssh.ui.screens

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.adamoutler.ssh.billing.BillingManager
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.crypto.SettingsManager
import com.adamoutler.ssh.sync.DriveSyncManager
import com.adamoutler.ssh.sync.SyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isCloudSyncEnabled: Boolean = false,
    val isSyncing: Boolean = false,
    val defaultGroupName: String = "Uncategorized",
    val isPassphraseSet: Boolean = false,
    val showPassphraseDialog: Boolean = false,
)

class SettingsViewModel(
    application: Application,
    private val billingManager: BillingManager,
    private val driveSyncManager: DriveSyncManager,
    private val settingsManager: SettingsManager = SettingsManager(application),
    private val securityStorageManager: SecurityStorageManager = SecurityStorageManager(application),
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            defaultGroupName = settingsManager.defaultGroupName,
            isPassphraseSet = securityStorageManager.getSyncPassphrase() != null,
        ),
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            billingManager.isCloudSyncEnabled.collect { isEnabled ->
                _uiState.update { it.copy(isCloudSyncEnabled = isEnabled) }
            }
        }
    }

    fun updateDefaultGroupName(name: String) {
        val newName = if (name.isBlank()) "Uncategorized" else name
        settingsManager.defaultGroupName = newName
        _uiState.update { it.copy(defaultGroupName = newName) }
    }

    fun purchaseCloudSync(activity: Activity) {
        billingManager.purchaseCloudSync(activity)
    }

    fun showPassphraseDialog() {
        _uiState.update { it.copy(showPassphraseDialog = true) }
    }

    fun dismissPassphraseDialog() {
        _uiState.update { it.copy(showPassphraseDialog = false) }
    }

    fun savePassphraseAndSync(passphrase: String, activity: Activity) {
        securityStorageManager.saveSyncPassphrase(passphrase.toCharArray())
        _uiState.update { it.copy(isPassphraseSet = true, showPassphraseDialog = false, isSyncing = true) }

        viewModelScope.launch {
            try {
                driveSyncManager.authenticate(activity)
                WorkManager.getInstance(getApplication()).enqueue(
                    OneTimeWorkRequestBuilder<SyncWorker>().build(),
                )
            } finally {
                _uiState.update { it.copy(isSyncing = false) }
            }
        }
    }

    fun authenticateGoogle(activity: Activity) {
        if (!_uiState.value.isPassphraseSet) {
            showPassphraseDialog()
            return
        }

        _uiState.update { it.copy(isSyncing = true) }
        viewModelScope.launch {
            try {
                driveSyncManager.authenticate(activity)
                WorkManager.getInstance(getApplication()).enqueue(
                    OneTimeWorkRequestBuilder<SyncWorker>().build(),
                )
            } finally {
                _uiState.update { it.copy(isSyncing = false) }
            }
        }
    }
}
