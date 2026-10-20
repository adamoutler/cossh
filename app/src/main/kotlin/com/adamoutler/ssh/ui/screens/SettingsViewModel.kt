package com.adamoutler.ssh.ui.screens

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.adamoutler.ssh.backup.BackupCryptoManager
import com.adamoutler.ssh.billing.BillingManager
import com.adamoutler.ssh.crypto.IdentityStorageManager
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.crypto.SettingsManager
import com.adamoutler.ssh.sync.DriveSyncManager
import com.adamoutler.ssh.sync.SyncWorker
import com.adamoutler.ssh.ui.theme.TerminalFontChoice
import com.adamoutler.ssh.ui.theme.TerminalTheme
import com.adamoutler.ssh.util.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream

data class SettingsUiState(
    val isCloudSyncEnabled: Boolean = false,
    val isCloudSyncActive: Boolean = true,
    val isSyncing: Boolean = false,
    val isRestoring: Boolean = false,
    val restoreStatusMessage: String? = null,
    val defaultGroupName: String = "Uncategorized",
    val isPassphraseSet: Boolean = false,
    val showPassphraseDialog: Boolean = false,
    val showRestoreDialog: Boolean = false,
    val showDisableSyncDialog: Boolean = false,
    val globalColorTheme: String = "COBALT_BLUE",
    val globalFontChoice: String = "DEFAULT_MONOSPACE",
    val isAppLockEnabled: Boolean = false,
    val appLockTimeoutSeconds: Int = 0,
)

class SettingsViewModel(
    application: Application,
    private val billingManager: BillingManager,
    private val driveSyncManager: DriveSyncManager,
    private val settingsManager: SettingsManager = SettingsManager(application),
    private val securityStorageManager: SecurityStorageManager = SecurityStorageManager(application),
    private val identityStorageManager: IdentityStorageManager = IdentityStorageManager(application),
    private val workManager: WorkManager? = try {
        WorkManager.getInstance(application)
    } catch (_: Throwable) {
        null
    },
) : AndroidViewModel(application) {

    companion object {
        const val WORK_NAME_CLOUD_SYNC = "cossh_cloud_sync"
    }

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            isCloudSyncActive = settingsManager.isCloudSyncActive,
            defaultGroupName = settingsManager.defaultGroupName,
            isPassphraseSet = securityStorageManager.getSyncPassphrase() != null,
            globalColorTheme = settingsManager.globalColorTheme,
            globalFontChoice = settingsManager.globalFontChoice,
            isAppLockEnabled = settingsManager.isAppLockEnabled,
            appLockTimeoutSeconds = settingsManager.appLockTimeoutSeconds,
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

    fun updateColorTheme(theme: TerminalTheme) {
        settingsManager.globalColorTheme = theme.id
        _uiState.update { it.copy(globalColorTheme = theme.id) }
    }

    fun updateFontChoice(font: TerminalFontChoice) {
        settingsManager.globalFontChoice = font.id
        _uiState.update { it.copy(globalFontChoice = font.id) }
    }

    fun updateDefaultGroupName(name: String) {
        val newName = if (name.isBlank()) "Uncategorized" else name
        settingsManager.defaultGroupName = newName
        _uiState.update { it.copy(defaultGroupName = newName) }
    }

    fun updateAppLockEnabled(enabled: Boolean) {
        settingsManager.isAppLockEnabled = enabled
        if (enabled) {
            if (!securityStorageManager.isAppLockConfigured()) {
                try {
                    val cipher = com.adamoutler.ssh.crypto.PasswordCipher.initEncryptCipher()
                    securityStorageManager.setupAppLock(cipher)
                } catch (e: Exception) {
                    // Handled if cipher initialization fails
                }
            }
        } else {
            securityStorageManager.clearAppLock()
        }
        _uiState.update { it.copy(isAppLockEnabled = enabled) }
    }

    fun updateAppLockTimeout(timeoutSeconds: Int) {
        settingsManager.appLockTimeoutSeconds = timeoutSeconds
        _uiState.update { it.copy(appLockTimeoutSeconds = timeoutSeconds) }
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

    fun showRestoreDialog() {
        _uiState.update { it.copy(showRestoreDialog = true) }
    }

    fun dismissRestoreDialog() {
        _uiState.update { it.copy(showRestoreDialog = false) }
    }

    fun showDisableSyncDialog() {
        _uiState.update { it.copy(showDisableSyncDialog = true) }
    }

    fun dismissDisableSyncDialog() {
        _uiState.update { it.copy(showDisableSyncDialog = false) }
    }

    fun clearRestoreStatusMessage() {
        _uiState.update { it.copy(restoreStatusMessage = null) }
    }

    fun enableCloudSync() {
        settingsManager.isCloudSyncActive = true
        _uiState.update { it.copy(isCloudSyncActive = true) }
    }

    fun disableCloudSync(deleteRemoteBackup: Boolean) {
        workManager?.cancelUniqueWork(WORK_NAME_CLOUD_SYNC)
        securityStorageManager.clearSyncPassphrase()
        settingsManager.isCloudSyncActive = false
        _uiState.update {
            it.copy(
                isCloudSyncActive = false,
                isPassphraseSet = false,
                showDisableSyncDialog = false,
            )
        }
        if (deleteRemoteBackup) {
            viewModelScope.launch {
                driveSyncManager.deleteBackup()
            }
        }
    }

    fun savePassphraseAndSync(passphrase: String, activity: Activity) {
        securityStorageManager.saveSyncPassphrase(passphrase.toCharArray())
        settingsManager.isCloudSyncActive = true
        _uiState.update {
            it.copy(
                isPassphraseSet = true,
                isCloudSyncActive = true,
                showPassphraseDialog = false,
                isSyncing = true,
            )
        }

        viewModelScope.launch {
            try {
                driveSyncManager.authenticate(activity)
                workManager?.enqueueUniqueWork(
                    WORK_NAME_CLOUD_SYNC,
                    ExistingWorkPolicy.REPLACE,
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
                workManager?.enqueueUniqueWork(
                    WORK_NAME_CLOUD_SYNC,
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<SyncWorker>().build(),
                )
            } finally {
                _uiState.update { it.copy(isSyncing = false) }
            }
        }
    }

    fun restoreFromCloud(passphrase: CharArray, activity: Activity? = null) {
        _uiState.update { it.copy(isRestoring = true, restoreStatusMessage = null) }
        viewModelScope.launch {
            try {
                if (driveSyncManager.oauthToken == null && activity != null) {
                    driveSyncManager.authenticate(activity)
                }
                val backupBytes = driveSyncManager.downloadBackup(passphrase)
                if (backupBytes == null || backupBytes.isEmpty()) {
                    _uiState.update { it.copy(restoreStatusMessage = "No remote backup found.") }
                    return@launch
                }
                val (profiles, identities) = BackupCryptoManager.importProfilesFromZip(
                    ByteArrayInputStream(backupBytes),
                    passphrase,
                )
                profiles.forEach { securityStorageManager.saveProfile(it) }
                identities.forEach { identityStorageManager.saveIdentity(it) }
                securityStorageManager.saveSyncPassphrase(passphrase)
                _uiState.update {
                    it.copy(
                        isPassphraseSet = true,
                        restoreStatusMessage = "Successfully restored ${profiles.size} profiles and ${identities.size} identities.",
                    )
                }
            } catch (e: Exception) {
                AppLog.e("SettingsViewModel", "Restore failed: ${e.message}", e)
                _uiState.update { it.copy(restoreStatusMessage = "Restore failed: ${e.message}") }
            } finally {
                passphrase.fill('\u0000')
                _uiState.update { it.copy(isRestoring = false, showRestoreDialog = false) }
            }
        }
    }
}
