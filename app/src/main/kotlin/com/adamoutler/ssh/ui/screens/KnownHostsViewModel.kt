package com.adamoutler.ssh.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adamoutler.ssh.crypto.KnownHostRecord
import com.adamoutler.ssh.crypto.KnownHostsManager
import com.adamoutler.ssh.security.SecureClipboardManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class KeyAlgorithmFilter {
    ALL,
    HOST_CA,
    ED25519,
    RSA,
    ECDSA,
}

data class KnownHostsUiState(
    val entries: List<KnownHostRecord> = emptyList(),
    val searchQuery: String = "",
    val selectedAlgorithm: KeyAlgorithmFilter = KeyAlgorithmFilter.ALL,
    val isMultiSelectMode: Boolean = false,
    val selectedEntryIds: Set<String> = emptySet(),
    val entryPendingDelete: KnownHostRecord? = null,
    val showBulkDeleteDialog: Boolean = false,
    val showHashAllDialog: Boolean = false,
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
) {
    val plaintextCount: Int
        get() = entries.count { !it.isHashed && !it.isCertAuthority }

    val filteredEntries: List<KnownHostRecord>
        get() = entries.filter { record ->
            // Algorithm Filter
            val matchesAlgo = when (selectedAlgorithm) {
                KeyAlgorithmFilter.ALL -> true
                KeyAlgorithmFilter.HOST_CA -> record.isCertAuthority
                KeyAlgorithmFilter.ED25519 -> !record.isCertAuthority && record.keyType.contains("ed25519", ignoreCase = true)
                KeyAlgorithmFilter.RSA -> !record.isCertAuthority && record.keyType.contains("rsa", ignoreCase = true)
                KeyAlgorithmFilter.ECDSA -> !record.isCertAuthority && record.keyType.contains("ecdsa", ignoreCase = true)
            }
            if (!matchesAlgo) return@filter false

            // Search Filter
            if (searchQuery.isBlank()) return@filter true
            val query = searchQuery.trim().lowercase()
            record.rawHostPattern.lowercase().contains(query) ||
                record.displayHost.lowercase().contains(query) ||
                record.fingerprint.lowercase().contains(query) ||
                record.keyType.lowercase().contains(query)
        }

    val countHostCa: Int
        get() = entries.count { it.isCertAuthority }

    val countEd25519: Int
        get() = entries.count { !it.isCertAuthority && it.keyType.contains("ed25519", ignoreCase = true) }

    val countRsa: Int
        get() = entries.count { !it.isCertAuthority && it.keyType.contains("rsa", ignoreCase = true) }

    val countEcdsa: Int
        get() = entries.count { !it.isCertAuthority && it.keyType.contains("ecdsa", ignoreCase = true) }
}

open class KnownHostsViewModel(
    application: Application,
    private val knownHostsManager: KnownHostsManager = KnownHostsManager(application),
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(KnownHostsUiState())
    val uiState: StateFlow<KnownHostsUiState> = _uiState.asStateFlow()

    init {
        loadEntries()
    }

    fun loadEntries() {
        _uiState.update { it.copy(isLoading = true) }
        val loaded = knownHostsManager.getEntries()
        _uiState.update {
            it.copy(
                entries = loaded,
                isLoading = false,
            )
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onAlgorithmFilterSelect(filter: KeyAlgorithmFilter) {
        _uiState.update { it.copy(selectedAlgorithm = filter) }
    }

    fun setMultiSelectMode(enabled: Boolean) {
        _uiState.update {
            it.copy(
                isMultiSelectMode = enabled,
                selectedEntryIds = if (enabled) it.selectedEntryIds else emptySet(),
            )
        }
    }

    fun toggleEntrySelection(entryId: String) {
        _uiState.update { state ->
            val updated = if (entryId in state.selectedEntryIds) {
                state.selectedEntryIds - entryId
            } else {
                state.selectedEntryIds + entryId
            }
            state.copy(selectedEntryIds = updated)
        }
    }

    fun toggleSelectAll() {
        _uiState.update { state ->
            val allIds = state.filteredEntries.map { it.id }.toSet()
            val newSelection = if (state.selectedEntryIds.size == allIds.size && allIds.isNotEmpty()) {
                emptySet()
            } else {
                allIds
            }
            state.copy(selectedEntryIds = newSelection)
        }
    }

    fun confirmSingleDelete(record: KnownHostRecord?) {
        _uiState.update { it.copy(entryPendingDelete = record) }
    }

    fun executeSingleDelete() {
        val record = _uiState.value.entryPendingDelete ?: return
        knownHostsManager.removeEntry(record.id)
        _uiState.update {
            it.copy(
                entryPendingDelete = null,
                statusMessage = "Host key removed.",
            )
        }
        loadEntries()
    }

    fun setShowBulkDeleteDialog(show: Boolean) {
        _uiState.update { it.copy(showBulkDeleteDialog = show) }
    }

    fun executeBulkDelete() {
        val selected = _uiState.value.selectedEntryIds
        if (selected.isNotEmpty()) {
            val removedCount = knownHostsManager.removeEntries(selected)
            _uiState.update {
                it.copy(
                    showBulkDeleteDialog = false,
                    selectedEntryIds = emptySet(),
                    isMultiSelectMode = false,
                    statusMessage = "Removed $removedCount host keys.",
                )
            }
            loadEntries()
        }
    }

    fun setShowHashAllDialog(show: Boolean) {
        _uiState.update { it.copy(showHashAllDialog = show) }
    }

    fun executeHashAll() {
        val count = knownHostsManager.hashAllPlaintextHosts()
        _uiState.update {
            it.copy(
                showHashAllDialog = false,
                statusMessage = "Hashed $count plaintext host keys.",
            )
        }
        loadEntries()
    }

    fun copyFingerprintToClipboard(fingerprint: String) {
        val context = getApplication<Application>()
        SecureClipboardManager.copyToClipboard(
            context = context,
            text = fingerprint,
            label = "SSH Fingerprint",
            scope = viewModelScope,
        )
        _uiState.update { it.copy(statusMessage = "Fingerprint copied. Auto-clearing in 30s.") }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}
