package com.adamoutler.ssh.ui.screens

import android.app.Application
import android.net.Uri
import com.adamoutler.ssh.backup.BackupManager
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.ui.base.BaseAndroidViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

sealed class ConnectionListItem {
    data class Header(val title: String, val folderId: String?) : ConnectionListItem()
    data class Profile(val profile: ConnectionProfile) : ConnectionListItem()
}

class ConnectionListViewModel(
    application: Application,
    private val storageManager: SecurityStorageManager,
    private val backupManager: BackupManager,
) : BaseAndroidViewModel(application) {

    constructor(application: Application) : this(
        application,
        SecurityStorageManager(application),
        BackupManager(application, SecurityStorageManager(application), com.adamoutler.ssh.crypto.IdentityStorageManager(application)),
    )

    private val _profiles = MutableStateFlow<List<ConnectionProfile>>(emptyList())
    val profiles: StateFlow<List<ConnectionProfile>> = _profiles.asStateFlow()

    private val _groupedProfiles = MutableStateFlow<Map<String?, List<ConnectionProfile>>>(emptyMap())
    val groupedProfiles: StateFlow<Map<String?, List<ConnectionProfile>>> = _groupedProfiles.asStateFlow()

    private val _flatItems = MutableStateFlow<List<ConnectionListItem>>(emptyList())
    val flatItems: StateFlow<List<ConnectionListItem>> = _flatItems.asStateFlow()

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
                val generatedKey = com.adamoutler.ssh.crypto.SSHKeyGenerator.generateRSAKeyPair()
                val dynamicUser = "dev_" + System.currentTimeMillis()

                val mockIdentity = com.adamoutler.ssh.data.IdentityProfile(
                    id = "mock_identity",
                    name = "Mock Server Key",
                    username = dynamicUser,
                    privateKey = generatedKey.private.encoded,
                )
                identityStorage.saveIdentity(mockIdentity)

                val testProfile = ConnectionProfile(
                    id = "default_test_profile",
                    nickname = "mock.hackedyour.info",
                    host = "mock.hackedyour.info",
                    username = dynamicUser,
                    authType = com.adamoutler.ssh.data.AuthType.KEY,
                    port = 32222,
                    identityId = mockIdentity.id,
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

            val grouped = filtered.groupBy { it.folderId }
            _groupedProfiles.value = grouped

            val defaultGroupName = com.adamoutler.ssh.crypto.SettingsManager(getApplication()).defaultGroupName
            val flatList = mutableListOf<ConnectionListItem>()
            val showHeaders = grouped.size > 1 || grouped.keys.firstOrNull() != null
            grouped.forEach { (folderId, profs) ->
                if (showHeaders) {
                    flatList.add(ConnectionListItem.Header(folderId ?: defaultGroupName, folderId))
                }
                profs.forEach { flatList.add(ConnectionListItem.Profile(it)) }
            }
            _flatItems.value = flatList
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

    fun moveProfileInFlatList(fromIndex: Int, toIndex: Int) {
        val currentList = _flatItems.value.toMutableList()
        if (fromIndex !in currentList.indices || toIndex !in currentList.indices) return

        val item = currentList[fromIndex]

        if (item is ConnectionListItem.Header) {
            // Find the entire block for this category
            var blockEnd = fromIndex + 1
            while (blockEnd < currentList.size && currentList[blockEnd] is ConnectionListItem.Profile) {
                blockEnd++
            }
            val block = currentList.subList(fromIndex, blockEnd).toList()

            // If dragging within its own block, ignore
            if (toIndex in fromIndex until blockEnd) return

            // Calculate the target index.
            // If dragging down, we want to place the block AFTER the target item.
            // If dragging up, we want to place the block BEFORE the target item.
            var insertIndex = toIndex
            if (toIndex >= blockEnd) {
                // We dragged it down past our own block.
                // We remove our block first, which shifts everything down by block.size.
                insertIndex -= block.size
                // Since we want to drop it AFTER the item we are currently hovering over,
                // and that item just shifted left by block.size, we add 1.
                insertIndex += 1
            }

            currentList.subList(fromIndex, blockEnd).clear()
            currentList.addAll(insertIndex, block)
        } else if (item is ConnectionListItem.Profile) {
            // Avoid dropping a profile before the very first header
            var effectiveToIndex = toIndex
            if (effectiveToIndex == 0 && currentList.firstOrNull() is ConnectionListItem.Header) {
                effectiveToIndex = 1
            }

            currentList.removeAt(fromIndex)
            currentList.add(effectiveToIndex, item)
        }

        _flatItems.value = currentList // Optimistic UI update

        var currentFolderId: String? = null
        val updatedProfiles = mutableListOf<ConnectionProfile>()
        var sortIndex = 0

        for (flatItem in currentList) {
            when (flatItem) {
                is ConnectionListItem.Header -> {
                    currentFolderId = flatItem.folderId
                }

                is ConnectionListItem.Profile -> {
                    val profile = flatItem.profile
                    if (profile.folderId != currentFolderId || profile.sortOrder != sortIndex) {
                        val updated = profile.copy(folderId = currentFolderId, sortOrder = sortIndex)
                        updatedProfiles.add(updated)
                    }
                    sortIndex++
                }
            }
        }

        launchWithHandler(Dispatchers.IO) {
            updatedProfiles.forEach { storageManager.saveProfile(it) }
            loadProfiles()
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
                println(("ConnectionListViewModel").toString() + ": " + ("Export failed").toString() + " " + (e).toString())
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
                println(("ConnectionListViewModel").toString() + ": " + ("Import failed").toString() + " " + (e).toString())
                onComplete(false)
            }
        }
    }
}
