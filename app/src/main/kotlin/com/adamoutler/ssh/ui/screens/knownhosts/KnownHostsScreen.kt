package com.adamoutler.ssh.ui.screens.knownhosts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adamoutler.ssh.crypto.KnownHostRecord
import com.adamoutler.ssh.ui.screens.KeyAlgorithmFilter
import com.adamoutler.ssh.ui.screens.KnownHostsUiState
import com.adamoutler.ssh.ui.screens.KnownHostsViewModel

@Composable
fun KnownHostsScreen(
    viewModel: KnownHostsViewModel = viewModel(),
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    KnownHostsContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onAlgorithmFilterSelect = viewModel::onAlgorithmFilterSelect,
        onToggleMultiSelectMode = viewModel::setMultiSelectMode,
        onToggleEntrySelection = viewModel::toggleEntrySelection,
        onToggleSelectAll = viewModel::toggleSelectAll,
        onConfirmSingleDelete = viewModel::confirmSingleDelete,
        onDismissSingleDelete = { viewModel.confirmSingleDelete(null) },
        onExecuteSingleDelete = viewModel::executeSingleDelete,
        onOpenBulkDeleteDialog = { viewModel.setShowBulkDeleteDialog(true) },
        onDismissBulkDeleteDialog = { viewModel.setShowBulkDeleteDialog(false) },
        onExecuteBulkDelete = viewModel::executeBulkDelete,
        onOpenHashAllDialog = { viewModel.setShowHashAllDialog(true) },
        onDismissHashAllDialog = { viewModel.setShowHashAllDialog(false) },
        onExecuteHashAll = viewModel::executeHashAll,
        onCopyFingerprint = viewModel::copyFingerprintToClipboard,
        onClearStatusMessage = viewModel::clearStatusMessage,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnownHostsContent(
    uiState: KnownHostsUiState,
    onNavigateBack: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onAlgorithmFilterSelect: (KeyAlgorithmFilter) -> Unit,
    onToggleMultiSelectMode: (Boolean) -> Unit,
    onToggleEntrySelection: (String) -> Unit,
    onToggleSelectAll: () -> Unit,
    onConfirmSingleDelete: (KnownHostRecord) -> Unit,
    onDismissSingleDelete: () -> Unit,
    onExecuteSingleDelete: () -> Unit,
    onOpenBulkDeleteDialog: () -> Unit,
    onDismissBulkDeleteDialog: () -> Unit,
    onExecuteBulkDelete: () -> Unit,
    onOpenHashAllDialog: () -> Unit,
    onDismissHashAllDialog: () -> Unit,
    onExecuteHashAll: () -> Unit,
    onCopyFingerprint: (String) -> Unit,
    onClearStatusMessage: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var expandedRandomartIds by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            onClearStatusMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (uiState.isMultiSelectMode) {
                TopAppBar(
                    title = { Text("${uiState.selectedEntryIds.size} Selected") },
                    navigationIcon = {
                        IconButton(
                            onClick = { onToggleMultiSelectMode(false) },
                            modifier = Modifier.testTag("ExitMultiSelectButton"),
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel Selection")
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = onToggleSelectAll,
                            modifier = Modifier.testTag("SelectAllButton"),
                        ) {
                            Text(
                                if (uiState.selectedEntryIds.size == uiState.filteredEntries.size && uiState.filteredEntries.isNotEmpty()) {
                                    "Deselect All"
                                } else {
                                    "Select All"
                                },
                            )
                        }
                        IconButton(
                            onClick = onOpenBulkDeleteDialog,
                            enabled = uiState.selectedEntryIds.isNotEmpty(),
                            modifier = Modifier.testTag("BulkDeleteActionButton"),
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete Selected",
                                tint = if (uiState.selectedEntryIds.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                    modifier = Modifier.testTag("KnownHostsMultiSelectTopBar"),
                )
            } else {
                TopAppBar(
                    title = { Text("Known Hosts") },
                    navigationIcon = {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("KnownHostsBackButton"),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (uiState.plaintextCount > 0) {
                            IconButton(
                                onClick = onOpenHashAllDialog,
                                modifier = Modifier.testTag("TopBarHashAllButton"),
                            ) {
                                Icon(Icons.Filled.Lock, contentDescription = "Hash All Plaintext Hosts")
                            }
                        }
                        IconButton(
                            onClick = { onToggleMultiSelectMode(true) },
                            modifier = Modifier.testTag("EnterMultiSelectButton"),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Select Multiple")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    modifier = Modifier.testTag("KnownHostsTopBar"),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchQueryChange,
                label = { Text("Filter by host, IP, or fingerprint") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("KnownHostsSearchQuery"),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Algorithm Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                item {
                    FilterChip(
                        selected = uiState.selectedAlgorithm == KeyAlgorithmFilter.ALL,
                        onClick = { onAlgorithmFilterSelect(KeyAlgorithmFilter.ALL) },
                        label = { Text("All (${uiState.entries.size})") },
                        modifier = Modifier.testTag("FilterChip_All"),
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.selectedAlgorithm == KeyAlgorithmFilter.HOST_CA,
                        onClick = { onAlgorithmFilterSelect(KeyAlgorithmFilter.HOST_CA) },
                        label = { Text("Host-CA (${uiState.countHostCa})") },
                        modifier = Modifier.testTag("FilterChip_HostCA"),
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.selectedAlgorithm == KeyAlgorithmFilter.ED25519,
                        onClick = { onAlgorithmFilterSelect(KeyAlgorithmFilter.ED25519) },
                        label = { Text("Ed25519 (${uiState.countEd25519})") },
                        modifier = Modifier.testTag("FilterChip_Ed25519"),
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.selectedAlgorithm == KeyAlgorithmFilter.RSA,
                        onClick = { onAlgorithmFilterSelect(KeyAlgorithmFilter.RSA) },
                        label = { Text("RSA (${uiState.countRsa})") },
                        modifier = Modifier.testTag("FilterChip_RSA"),
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.selectedAlgorithm == KeyAlgorithmFilter.ECDSA,
                        onClick = { onAlgorithmFilterSelect(KeyAlgorithmFilter.ECDSA) },
                        label = { Text("ECDSA (${uiState.countEcdsa})") },
                        modifier = Modifier.testTag("FilterChip_ECDSA"),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Plaintext Migration Banner
            if (uiState.plaintextCount > 0 && !uiState.isMultiSelectMode) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("PlaintextMigrationBanner"),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text(
                                    "${uiState.plaintextCount} Plaintext Hosts Detected",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "Hash hosts to protect network topology from inspection.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        Button(
                            onClick = onOpenHashAllDialog,
                            modifier = Modifier.testTag("HashAllPlaintextButton"),
                        ) {
                            Text("Hash All")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Host List
            if (uiState.filteredEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("EmptyKnownHostsBox"),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "No matching known host entries found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("EmptyKnownHostsMessage"),
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("KnownHostsList"),
                ) {
                    itemsIndexed(uiState.filteredEntries, key = { _, item -> item.id }) { index, record ->
                        ElevatedCard(
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("HostKeyCard_$index"),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (uiState.isMultiSelectMode) {
                                    Checkbox(
                                        checked = record.id in uiState.selectedEntryIds,
                                        onCheckedChange = { onToggleEntrySelection(record.id) },
                                        modifier = Modifier.testTag("HostKeyCheckbox_$index"),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Icon(
                                                imageVector = if (record.isHashed) Icons.Filled.Lock else Icons.Outlined.Info,
                                                contentDescription = if (record.isHashed) "Hashed Host" else "Plaintext Host",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Text(
                                                text = record.displayHost,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                            )
                                        }

                                        SuggestionChip(
                                            onClick = {},
                                            label = {
                                                Text(
                                                    if (record.isCertAuthority) "HOST-CA 🏛️" else record.keyType.uppercase().replace("SSH-", ""),
                                                    style = MaterialTheme.typography.labelSmall,
                                                )
                                            },
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "SHA-256 Fingerprint",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = record.fingerprint,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                        ),
                                        modifier = Modifier.testTag("HostKeyFingerprint_$index"),
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        TextButton(
                                            onClick = { onCopyFingerprint(record.fingerprint) },
                                            modifier = Modifier.testTag("CopyFingerprintButton_$index"),
                                        ) {
                                            Text("Copy Fingerprint")
                                        }

                                        TextButton(
                                            onClick = {
                                                expandedRandomartIds = if (record.id in expandedRandomartIds) {
                                                    expandedRandomartIds - record.id
                                                } else {
                                                    expandedRandomartIds + record.id
                                                }
                                            },
                                            modifier = Modifier.testTag("ToggleRandomartButton_$index"),
                                        ) {
                                            Text(if (record.id in expandedRandomartIds) "Hide Randomart" else "Show Randomart")
                                        }

                                        if (!uiState.isMultiSelectMode) {
                                            IconButton(
                                                onClick = { onConfirmSingleDelete(record) },
                                                modifier = Modifier.testTag("DeleteHostKeyButton_$index"),
                                            ) {
                                                Icon(
                                                    Icons.Filled.Delete,
                                                    contentDescription = "Delete Host Key",
                                                    tint = MaterialTheme.colorScheme.error,
                                                )
                                            }
                                        }
                                    }

                                    if (record.id in expandedRandomartIds) {
                                        val randomart = remember(record.publicKeyBase64, record.keyType) {
                                            try {
                                                val decoded = java.util.Base64.getDecoder().decode(record.publicKeyBase64)
                                                com.adamoutler.ssh.crypto.randomart.OpenSshRandomartGenerator.generateFromPublicKeyBytes(decoded, record.keyType)
                                            } catch (_: Exception) {
                                                null
                                            }
                                        }
                                        if (randomart != null) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            com.adamoutler.ssh.ui.components.RandomartBox(
                                                randomart = randomart,
                                                title = "OpenSSH Randomart",
                                                testTag = "HostKeyRandomart_$index",
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Single Entry Deletion Dialog
    uiState.entryPendingDelete?.let { record ->
        AlertDialog(
            onDismissRequest = onDismissSingleDelete,
            icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Host Key?") },
            text = {
                Text(
                    "Are you sure you want to remove the host key for '${record.displayHost}'? Future connections to this host will trigger a new TOFU host key verification prompt.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onExecuteSingleDelete,
                    modifier = Modifier.testTag("ConfirmSingleDeleteButton"),
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissSingleDelete,
                    modifier = Modifier.testTag("DismissSingleDeleteButton"),
                ) {
                    Text("Cancel")
                }
            },
            modifier = Modifier.testTag("SingleDeleteDialog"),
        )
    }

    // Bulk Deletion Dialog
    if (uiState.showBulkDeleteDialog) {
        AlertDialog(
            onDismissRequest = onDismissBulkDeleteDialog,
            icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete ${uiState.selectedEntryIds.size} Host Keys?") },
            text = {
                Text(
                    "Are you sure you want to remove ${uiState.selectedEntryIds.size} selected host keys from your known hosts cache? Future connections will require TOFU re-verification.",
                )
            },
            confirmButton = {
                Button(
                    onClick = onExecuteBulkDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("ConfirmBulkDeleteButton"),
                ) {
                    Text("Delete ${uiState.selectedEntryIds.size} Keys")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissBulkDeleteDialog,
                    modifier = Modifier.testTag("DismissBulkDeleteButton"),
                ) {
                    Text("Cancel")
                }
            },
            modifier = Modifier.testTag("BulkDeleteDialog"),
        )
    }

    // Hash All Plaintext Hosts Dialog
    if (uiState.showHashAllDialog) {
        AlertDialog(
            onDismissRequest = onDismissHashAllDialog,
            icon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Hash All Plaintext Hosts?") },
            text = {
                Text(
                    "Convert ${uiState.plaintextCount} plaintext host entries into OpenSSH hashed format (|1|salt|hash). This secures hostnames and IP addresses against enumeration attacks if device storage is inspected, while preserving seamless SSH connections.",
                )
            },
            confirmButton = {
                Button(
                    onClick = onExecuteHashAll,
                    modifier = Modifier.testTag("ConfirmHashAllButton"),
                ) {
                    Text("Hash Hosts")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissHashAllDialog,
                    modifier = Modifier.testTag("DismissHashAllButton"),
                ) {
                    Text("Cancel")
                }
            },
            modifier = Modifier.testTag("HashAllDialog"),
        )
    }
}
