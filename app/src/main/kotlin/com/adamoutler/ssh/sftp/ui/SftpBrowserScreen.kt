package com.adamoutler.ssh.sftp.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adamoutler.ssh.sftp.model.SftpFileItem
import com.adamoutler.ssh.sftp.transfer.SftpTransferItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SftpBrowserScreen(
    profileNickname: String,
    viewModel: SftpBrowserViewModel,
    activeTransfer: SftpTransferItem? = null,
    onCancelTransfer: (String) -> Unit = {},
    onDownloadFile: (SftpFileItem) -> Unit = {},
    onUploadFile: () -> Unit = {},
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    var isSearchExpanded by remember { mutableStateOf(false) }
    var isSortMenuExpanded by remember { mutableStateOf(false) }

    var itemToRename by remember { mutableStateOf<SftpFileItem?>(null) }
    var itemToDelete by remember { mutableStateOf<SftpFileItem?>(null) }
    var showCreateDirDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                if (uiState.isMultiSelectMode) {
                    TopAppBar(
                        title = { Text("${uiState.selectedPaths.size} selected") },
                        navigationIcon = {
                            IconButton(onClick = { viewModel.clearSelection() }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear Selection")
                            }
                        },
                        actions = {
                            IconButton(onClick = { viewModel.selectAll() }) {
                                Icon(Icons.Filled.SelectAll, contentDescription = "Select All")
                            }
                            IconButton(onClick = { viewModel.deleteSelectedItems() }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete Selected")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    )
                } else {
                    TopAppBar(
                        title = {
                            if (isSearchExpanded) {
                                OutlinedTextField(
                                    value = uiState.searchQuery,
                                    onValueChange = { viewModel.updateSearchQuery(it) },
                                    placeholder = { Text("Search files...") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("SftpSearchInput"),
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            isSearchExpanded = false
                                            viewModel.updateSearchQuery("")
                                        }) {
                                            Icon(Icons.Filled.Close, contentDescription = "Close Search")
                                        }
                                    },
                                )
                            } else {
                                Text(profileNickname.ifBlank { "SFTP Browser" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            if (!isSearchExpanded) {
                                IconButton(onClick = { isSearchExpanded = true }) {
                                    Icon(Icons.Filled.Search, contentDescription = "Search")
                                }
                            }

                            Box {
                                IconButton(onClick = { isSortMenuExpanded = true }) {
                                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                                }
                                DropdownMenu(
                                    expanded = isSortMenuExpanded,
                                    onDismissRequest = { isSortMenuExpanded = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Name (A-Z)") },
                                        onClick = {
                                            viewModel.updateSortMode(SftpSortMode.NAME_ASC)
                                            isSortMenuExpanded = false
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Name (Z-A)") },
                                        onClick = {
                                            viewModel.updateSortMode(SftpSortMode.NAME_DESC)
                                            isSortMenuExpanded = false
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Size (Large First)") },
                                        onClick = {
                                            viewModel.updateSortMode(SftpSortMode.SIZE_DESC)
                                            isSortMenuExpanded = false
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Date (Newest First)") },
                                        onClick = {
                                            viewModel.updateSortMode(SftpSortMode.DATE_DESC)
                                            isSortMenuExpanded = false
                                        },
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text(if (uiState.foldersFirst) "✓ Folders First" else "Folders First") },
                                        onClick = {
                                            viewModel.toggleFoldersFirst(!uiState.foldersFirst)
                                            isSortMenuExpanded = false
                                        },
                                    )
                                }
                            }

                            IconButton(onClick = { showCreateDirDialog = true }) {
                                Icon(Icons.Filled.CreateNewFolder, contentDescription = "New Folder")
                            }
                            IconButton(onClick = { viewModel.loadDirectory(uiState.currentPath) }) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                            }
                        },
                    )
                }

                // Interactive Breadcrumb Navigation Strip
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().testTag("BreadcrumbsStrip"),
                ) {
                    val scrollState = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(scrollState)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        uiState.breadcrumbs.forEachIndexed { index, crumb ->
                            TextButton(
                                onClick = { viewModel.loadDirectory(crumb.fullPath) },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = crumb.name,
                                    fontWeight = if (index == uiState.breadcrumbs.size - 1) FontWeight.Bold else FontWeight.Normal,
                                    color = if (index == uiState.breadcrumbs.size - 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (index < uiState.breadcrumbs.size - 1) {
                                Text("/", color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onUploadFile) {
                Icon(Icons.Filled.UploadFile, contentDescription = "Upload File")
            }
        },
        bottomBar = {
            SftpTransferBanner(
                transfer = activeTransfer,
                onCancel = onCancelTransfer,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                uiState.errorMessage != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(uiState.errorMessage ?: "", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadDirectory(uiState.currentPath) }) {
                            Text("Retry")
                        }
                    }
                }

                uiState.filteredItems.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("This folder is empty", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
                    }
                }

                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize().testTag("SftpFileList")) {
                        items(uiState.filteredItems, key = { it.fullPath }) { item ->
                            SftpFileListItem(
                                item = item,
                                isSelected = uiState.selectedPaths.contains(item.fullPath),
                                isMultiSelectMode = uiState.isMultiSelectMode,
                                onClick = {
                                    if (uiState.isMultiSelectMode) {
                                        viewModel.toggleItemSelection(item.fullPath)
                                    } else if (item.isDirectory) {
                                        viewModel.loadDirectory(item.fullPath)
                                    } else {
                                        viewModel.openPreview(item)
                                    }
                                },
                                onLongClick = {
                                    viewModel.toggleItemSelection(item.fullPath)
                                },
                                onDownload = { onDownloadFile(item) },
                                onRename = { itemToRename = item },
                                onPermissions = { viewModel.openChmodDialog(item) },
                                onDelete = { itemToDelete = item },
                                onViewText = { viewModel.openPreview(item) },
                            )
                        }
                    }
                }
            }
        }
    }

    // --- Permissions Dialog ---
    if (uiState.activeChmodItem != null) {
        val chmodItem = uiState.activeChmodItem!!
        SftpPermissionsDialog(
            initialPermissionsOctal = chmodItem.octalPermissions,
            filename = chmodItem.name,
            onDismiss = { viewModel.closeChmodDialog() },
            onConfirm = { octal -> viewModel.applyPermissions(octal) },
        )
    }

    // --- In-App Text Preview Modal ---
    if (uiState.activePreviewItem != null) {
        val previewItem = uiState.activePreviewItem!!
        SftpTextViewerModal(
            filename = previewItem.name,
            content = uiState.previewContent,
            fileSizeBytes = previewItem.size,
            isLoading = uiState.isPreviewLoading,
            errorMessage = uiState.previewErrorMessage,
            onDismiss = { viewModel.closePreview() },
        )
    }

    // --- Create Directory Dialog ---
    if (showCreateDirDialog) {
        var dirName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDirDialog = false },
            title = { Text("Create New Directory") },
            text = {
                OutlinedTextField(
                    value = dirName,
                    onValueChange = { dirName = it },
                    label = { Text("Directory Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("CreateDirInput"),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (dirName.isNotBlank()) {
                            viewModel.createDirectory(dirName.trim())
                            showCreateDirDialog = false
                        }
                    },
                    modifier = Modifier.testTag("ConfirmCreateDirButton"),
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDirDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    // --- Rename Dialog ---
    if (itemToRename != null) {
        val renameItem = itemToRename!!
        var newName by remember { mutableStateOf(renameItem.name) }
        AlertDialog(
            onDismissRequest = { itemToRename = null },
            title = { Text("Rename ${if (renameItem.isDirectory) "Directory" else "File"}") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("RenameInput"),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank() && newName != renameItem.name) {
                            viewModel.renameItem(renameItem.fullPath, newName.trim())
                            itemToRename = null
                        }
                    },
                    modifier = Modifier.testTag("ConfirmRenameButton"),
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToRename = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    // --- Delete Confirmation Dialog ---
    if (itemToDelete != null) {
        val deleteTarget = itemToDelete!!
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Delete ${if (deleteTarget.isDirectory) "Directory" else "File"}?") },
            text = {
                Text("Are you sure you want to permanently delete '${deleteTarget.name}' from the remote server?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteItem(deleteTarget)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("ConfirmDeleteButton"),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SftpFileListItem(
    item: SftpFileItem,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDownload: () -> Unit,
    onRename: () -> Unit,
    onPermissions: () -> Unit,
    onDelete: () -> Unit,
    onViewText: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("SftpItem_${item.name}")
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isMultiSelectMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }

            Icon(
                imageVector = getFileIcon(item),
                contentDescription = if (item.isDirectory) "Directory" else "File",
                tint = if (item.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp).padding(end = 12.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.formattedSize,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.extraSmall,
                    ) {
                        Text(
                            text = item.permissions,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }

                    val dateStr = remember(item.modifiedTimeEpochMs) {
                        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                        sdf.format(Date(item.modifiedTimeEpochMs))
                    }
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!isMultiSelectMode) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Options")
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        if (!item.isDirectory) {
                            DropdownMenuItem(
                                text = { Text("Download") },
                                leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onDownload()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("View as Text") },
                                leadingIcon = { Icon(Icons.Filled.Visibility, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onViewText()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Permissions (chmod)") },
                            leadingIcon = { Icon(Icons.Filled.Security, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onPermissions()
                            },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun getFileIcon(item: SftpFileItem): ImageVector {
    if (item.isDirectory) return Icons.Filled.Folder
    if (item.isSymlink) return Icons.Filled.Link

    val ext = item.name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "sh", "py", "kt", "java", "c", "cpp", "js", "ts", "html", "css", "json", "xml", "yml", "yaml" -> Icons.Filled.Code
        "zip", "tar", "gz", "bz2", "xz", "7z", "rar" -> Icons.Filled.Archive
        "png", "jpg", "jpeg", "gif", "webp", "svg" -> Icons.Filled.Image
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
}
