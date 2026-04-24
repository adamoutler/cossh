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

    private val _groupedProfiles = MutableStateFlow<Map<String?, List<ConnectionProfile>>>(emptyMap())
    val groupedProfiles: StateFlow<Map<String?, List<ConnectionProfile>>> = _groupedProfiles.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        loadProfiles()
    }

    fun loadProfiles() {
        launchWithHandler {
            if (com.adamoutler.ssh.BuildConfig.DEBUG && storageManager.getAllProfiles().isEmpty()) {
                // Troubleshooting profile: mock.hackedyour.info — port 32222 is SSH, port 32223 is HTTP health check
                val identityStorage = com.adamoutler.ssh.crypto.IdentityStorageManager(getApplication())
                val wellKnownKey = """-----BEGIN OPENSSH PRIVATE KEY-----
b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtzc2gtZW
QyNTUxOQAAACAMXP6JRs0AflZ1/nV7BZlO3n84+gn/KdyLpEI4ISLuyQAAAJiQ30JXkN9C
VwAAAAtzc2gtZWQyNTUxOQAAACAMXP6JRs0AflZ1/nV7BZlO3n84+gn/KdyLpEI4ISLuyQ
AAAEBzoFMNN4gntE6K7Rb+DhpiP1cJZMhWhu6q+AYVLSg1vQxc/olGzQB+VnX+dXsFmU7e
fzj6Cf8p3IukQjghIu7JAAAAE2FkYW1vdXRsZXJASExBQi1BMjUBAg==
-----END OPENSSH PRIVATE KEY-----""".trimIndent()
                val mockIdentity = com.adamoutler.ssh.data.IdentityProfile(
                    id = "mock_identity",
                    name = "Mock Server Key",
                    username = "test",
                    privateKey = wellKnownKey.toByteArray()
                )
                identityStorage.saveIdentity(mockIdentity)

                val testProfile = ConnectionProfile(
                    id = "default_test_profile",
                    nickname = "mock.hackedyour.info",
                    host = "mock.hackedyour.info",
                    username = "test",
                    authType = com.adamoutler.ssh.data.AuthType.KEY,
                    port = 32222,
                    identityId = mockIdentity.id
                )
                storageManager.saveProfile(testProfile)
            }
            
            val all = storageManager.getAllProfiles().sortedBy { it.sortOrder }
            val query = _searchQuery.value
            val filtered = if (query.isBlank()) {
                all
            } else {
                all.filter {
                    it.nickname.contains(query, ignoreCase = true) ||
                    it.host.contains(query, ignoreCase = true)
                }
            }
            _profiles.value = filtered
            _groupedProfiles.value = filtered.groupBy { it.folderId }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        loadProfiles()
    }

    fun moveToFolder(profileId: String, folderId: String?) {
        launchWithHandler {
            val profile = storageManager.getProfile(profileId)
            if (profile != null) {
                storageManager.saveProfile(profile.copy(folderId = folderId))
                loadProfiles()
            }
        }
    }

    fun deleteProfile(profileId: String) {
        launchWithHandler {
            storageManager.deleteProfile(profileId)
            loadProfiles()
        }
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
