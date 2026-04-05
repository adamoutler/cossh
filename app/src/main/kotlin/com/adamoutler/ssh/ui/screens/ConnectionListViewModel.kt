package com.adamoutler.ssh.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.data.ConnectionProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ConnectionListViewModel(
    application: Application,
    private val storageManager: SecurityStorageManager = SecurityStorageManager(application)
) : AndroidViewModel(application) {

    private val _profiles = MutableStateFlow<List<ConnectionProfile>>(emptyList())
    val profiles: StateFlow<List<ConnectionProfile>> = _profiles.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        loadProfiles()
    }

    fun loadProfiles() {
        viewModelScope.launch {
            val all = storageManager.getAllProfiles()
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
}