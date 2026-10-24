package com.adamoutler.ssh.sftp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adamoutler.ssh.sftp.SftpClientManager
import com.adamoutler.ssh.sftp.SftpPathValidator
import com.adamoutler.ssh.sftp.model.SftpFileItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PathBreadcrumb(
    val name: String,
    val fullPath: String,
)

enum class SftpSortMode {
    NAME_ASC,
    NAME_DESC,
    SIZE_ASC,
    SIZE_DESC,
    DATE_DESC,
    DATE_ASC,
}

data class SftpBrowserUiState(
    val currentPath: String = "/",
    val breadcrumbs: List<PathBreadcrumb> = listOf(PathBreadcrumb("root", "/")),
    val items: List<SftpFileItem> = emptyList(),
    val filteredItems: List<SftpFileItem> = emptyList(),
    val searchQuery: String = "",
    val sortMode: SftpSortMode = SftpSortMode.NAME_ASC,
    val foldersFirst: Boolean = true,
    val selectedPaths: Set<String> = emptySet(),
    val isMultiSelectMode: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val activePreviewItem: SftpFileItem? = null,
    val previewContent: String? = null,
    val isPreviewLoading: Boolean = false,
    val previewErrorMessage: String? = null,
    val activeChmodItem: SftpFileItem? = null,
)

class SftpBrowserViewModel(
    private val sftpManager: SftpClientManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SftpBrowserUiState())
    val uiState: StateFlow<SftpBrowserUiState> = _uiState.asStateFlow()

    init {
        loadDirectory("/")
    }

    fun loadDirectory(path: String) {
        val canonical = SftpPathValidator.canonicalize(path)
        val crumbs = buildBreadcrumbs(canonical)

        _uiState.update {
            it.copy(
                currentPath = canonical,
                breadcrumbs = crumbs,
                isLoading = true,
                errorMessage = null,
                selectedPaths = emptySet(),
                isMultiSelectMode = false,
            )
        }

        viewModelScope.launch {
            try {
                val list = sftpManager.listDirectory(canonical)
                _uiState.update { current ->
                    val filtered = applySortAndFilter(list, current.searchQuery, current.sortMode, current.foldersFirst)
                    current.copy(
                        items = list,
                        filteredItems = filtered,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to list directory",
                    )
                }
            }
        }
    }

    fun navigateUp() {
        val current = _uiState.value.currentPath
        val parent = SftpPathValidator.getParent(current)
        if (parent != current) {
            loadDirectory(parent)
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { current ->
            val filtered = applySortAndFilter(current.items, query, current.sortMode, current.foldersFirst)
            current.copy(searchQuery = query, filteredItems = filtered)
        }
    }

    fun updateSortMode(sortMode: SftpSortMode) {
        _uiState.update { current ->
            val filtered = applySortAndFilter(current.items, current.searchQuery, sortMode, current.foldersFirst)
            current.copy(sortMode = sortMode, filteredItems = filtered)
        }
    }

    fun toggleFoldersFirst(enabled: Boolean) {
        _uiState.update { current ->
            val filtered = applySortAndFilter(current.items, current.searchQuery, current.sortMode, enabled)
            current.copy(foldersFirst = enabled, filteredItems = filtered)
        }
    }

    fun toggleItemSelection(fullPath: String) {
        _uiState.update { current ->
            val updated = if (current.selectedPaths.contains(fullPath)) {
                current.selectedPaths - fullPath
            } else {
                current.selectedPaths + fullPath
            }
            current.copy(
                selectedPaths = updated,
                isMultiSelectMode = updated.isNotEmpty(),
            )
        }
    }

    fun selectAll() {
        _uiState.update { current ->
            val allPaths = current.filteredItems.map { it.fullPath }.toSet()
            current.copy(selectedPaths = allPaths, isMultiSelectMode = allPaths.isNotEmpty())
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedPaths = emptySet(), isMultiSelectMode = false) }
    }

    fun openPreview(item: SftpFileItem) {
        _uiState.update {
            it.copy(
                activePreviewItem = item,
                previewContent = null,
                isPreviewLoading = item.size <= (2 * 1024 * 1024L),
                previewErrorMessage = null,
            )
        }

        if (item.size > (2 * 1024 * 1024L)) {
            return
        }

        viewModelScope.launch {
            try {
                val content = sftpManager.readFileContent(item.fullPath)
                _uiState.update { it.copy(previewContent = content, isPreviewLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isPreviewLoading = false, previewErrorMessage = e.message) }
            }
        }
    }

    fun closePreview() {
        _uiState.update { it.copy(activePreviewItem = null, previewContent = null, previewErrorMessage = null) }
    }

    fun openChmodDialog(item: SftpFileItem) {
        _uiState.update { it.copy(activeChmodItem = item) }
    }

    fun closeChmodDialog() {
        _uiState.update { it.copy(activeChmodItem = null) }
    }

    fun applyPermissions(octal: Int) {
        val item = _uiState.value.activeChmodItem ?: return
        closeChmodDialog()
        viewModelScope.launch {
            try {
                sftpManager.setPermissions(item.fullPath, octal)
                loadDirectory(_uiState.value.currentPath)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Chmod failed: ${e.message}") }
            }
        }
    }

    fun createDirectory(name: String) {
        val target = SftpPathValidator.join(_uiState.value.currentPath, name)
        viewModelScope.launch {
            try {
                sftpManager.createDirectory(target)
                loadDirectory(_uiState.value.currentPath)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Create folder failed: ${e.message}") }
            }
        }
    }

    fun renameItem(oldPath: String, newName: String) {
        val parent = SftpPathValidator.getParent(oldPath)
        val newPath = SftpPathValidator.join(parent, newName)
        viewModelScope.launch {
            try {
                sftpManager.rename(oldPath, newPath)
                loadDirectory(_uiState.value.currentPath)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Rename failed: ${e.message}") }
            }
        }
    }

    fun deleteItem(item: SftpFileItem) {
        viewModelScope.launch {
            try {
                sftpManager.delete(item.fullPath, item.isDirectory)
                loadDirectory(_uiState.value.currentPath)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Delete failed: ${e.message}") }
            }
        }
    }

    fun deleteSelectedItems() {
        val paths = _uiState.value.selectedPaths.toList()
        val itemMap = _uiState.value.items.associateBy { it.fullPath }
        clearSelection()

        viewModelScope.launch {
            try {
                for (p in paths) {
                    val item = itemMap[p]
                    if (item != null) {
                        sftpManager.delete(item.fullPath, item.isDirectory)
                    }
                }
                loadDirectory(_uiState.value.currentPath)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Bulk delete failed: ${e.message}") }
            }
        }
    }

    companion object {
        fun buildBreadcrumbs(canonicalPath: String): List<PathBreadcrumb> {
            if (canonicalPath == "/" || canonicalPath.isEmpty()) {
                return listOf(PathBreadcrumb("root", "/"))
            }

            val crumbs = mutableListOf(PathBreadcrumb("root", "/"))
            val segments = canonicalPath.split('/').filter { it.isNotEmpty() }
            var runningPath = ""
            for (seg in segments) {
                runningPath += "/$seg"
                crumbs.add(PathBreadcrumb(seg, runningPath))
            }
            return crumbs
        }

        fun applySortAndFilter(
            items: List<SftpFileItem>,
            query: String,
            sortMode: SftpSortMode,
            foldersFirst: Boolean,
        ): List<SftpFileItem> {
            val filtered = if (query.isBlank()) {
                items
            } else {
                items.filter { it.name.contains(query, ignoreCase = true) }
            }

            val comparator = when (sortMode) {
                SftpSortMode.NAME_ASC -> compareBy<SftpFileItem> { it.name.lowercase() }
                SftpSortMode.NAME_DESC -> compareByDescending<SftpFileItem> { it.name.lowercase() }
                SftpSortMode.SIZE_ASC -> compareBy<SftpFileItem> { it.size }
                SftpSortMode.SIZE_DESC -> compareByDescending<SftpFileItem> { it.size }
                SftpSortMode.DATE_ASC -> compareBy<SftpFileItem> { it.modifiedTimeEpochMs }
                SftpSortMode.DATE_DESC -> compareByDescending<SftpFileItem> { it.modifiedTimeEpochMs }
            }

            return if (foldersFirst) {
                filtered.sortedWith(compareBy<SftpFileItem> { !it.isDirectory }.then(comparator))
            } else {
                filtered.sortedWith(comparator)
            }
        }
    }
}
