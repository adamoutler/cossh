package com.adamoutler.ssh.ui.screens

import android.app.Application
import android.net.Uri
import android.util.Log
import com.adamoutler.ssh.backup.BackupManager
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.ui.base.BaseAndroidViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class ConnectionListViewModel(
    application: Application,
    private val storageManager: SecurityStorageManager,
    private val backupManager: BackupManager
) : BaseAndroidViewModel(application) {

    constructor(application: Application) : this(
        application,
        SecurityStorageManager(application),
        BackupManager(application, SecurityStorageManager(application))
    )

    private val _profiles = MutableStateFlow<List<ConnectionProfile>>(emptyList())
    val profiles: StateFlow<List<ConnectionProfile>> = _profiles.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        loadProfiles()
    }

    fun loadProfiles() {
        launchWithHandler {
            if (com.adamoutler.ssh.BuildConfig.DEBUG && storageManager.getAllProfiles().isEmpty()) {
                // Troubleshooting profile: 192.168.1.115 — port 32222 is SSH, port 32223 is HTTP health check
                val testProfile = ConnectionProfile(
                    id = "default_test_profile",
                    nickname = "Troubleshooting (192.168.1.115)",
                    host = "192.168.1.115",
                    username = "test",
                    authType = com.adamoutler.ssh.data.AuthType.PASSWORD,
                    port = 32222
                ).apply {
                    password = "test".toByteArray()
                }
                storageManager.saveProfile(testProfile)
            }
            
            val all = storageManager.getAllProfiles().sortedBy { it.sortOrder }
            val query = _searchQuery.value
            if (query.isBlank()) {
                _profiles.value = all
            } else {
                _profiles.value = all.filter {
                    it.nickname.contains(query, ignoreCase = true) ||
                    it.host.contains(query, ignoreCase = true)
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        loadProfiles()
    }

    fun moveProfile(fromIndex: Int, toIndex: Int) {
        val currentList = _profiles.value.toMutableList()
        if (fromIndex !in currentList.indices || toIndex !in currentList.indices) return

        val item = currentList.removeAt(fromIndex)
        currentList.add(toIndex, item)
        _profiles.value = currentList

        launchWithHandler(Dispatchers.IO) {
            currentList.forEachIndexed { index, profile ->
                if (profile.sortOrder != index) {
                    profile.sortOrder = index
                    storageManager.saveProfile(profile)
                }
            }
        }
    }

    fun exportBackup(uri: Uri, password: CharArray, onComplete: (Boolean) -> Unit) {
        launchWithHandler {
            try {
                withContext(Dispatchers.IO) {
                    backupManager.exportBackup(uri, password)
                }
                onComplete(true)
            } catch (e: Exception) {
                Log.e("ConnectionListViewModel", "Export failed", e)
                onComplete(false)
            }
        }
    }

    fun importBackup(uri: Uri, password: CharArray, onComplete: (Boolean) -> Unit) {
        launchWithHandler {
            try {
                withContext(Dispatchers.IO) {
                    backupManager.importBackup(uri, password)
                }
                loadProfiles()
                onComplete(true)
            } catch (e: Exception) {
                Log.e("ConnectionListViewModel", "Import failed", e)
                onComplete(false)
            }
        }
    }
}
