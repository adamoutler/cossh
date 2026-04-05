package com.adamoutler.ssh.ui.screens

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adamoutler.ssh.data.ConnectionProfile

@Composable
fun ConnectionListScreen(
    viewModel: ConnectionListViewModel = viewModel(),
    onAddConnection: () -> Unit,
    onEditConnection: (String) -> Unit,
    onConnect: (String) -> Unit
) {
    val profiles by viewModel.profiles.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadProfiles()
    }

    var pendingExportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var isExporting by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            pendingExportUri = uri
            isExporting = true
            passwordInput = ""
            showPasswordDialog = true
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            isExporting = false
            passwordInput = ""
            showPasswordDialog = true
        }
    }

    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false; pendingExportUri = null; pendingImportUri = null },
            title = { Text(if (isExporting) "Export Backup" else "Import Backup") },
            text = {
                Column {
                    Text(if (isExporting) "Enter a password to encrypt your backup:" else "Enter the backup password to decrypt:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val uri = if (isExporting) pendingExportUri else pendingImportUri
                    val pwd = passwordInput.toCharArray()
                    showPasswordDialog = false
                    if (uri != null) {
                        if (isExporting) {
                            viewModel.exportBackup(uri, pwd) { success ->
                                Toast.makeText(context, if (success) "Export successful" else "Export failed", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            viewModel.importBackup(uri, pwd) { success ->
                                Toast.makeText(context, if (success) "Import successful" else "Import failed (invalid password or corrupted file)", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    pendingExportUri = null
                    pendingImportUri = null
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false; pendingExportUri = null; pendingImportUri = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    ConnectionListScreenContent(
        profiles = profiles,
        searchQuery = searchQuery,
        onSearchQueryChange = { viewModel.updateSearchQuery(it) },
        onAddConnection = onAddConnection,
        onEditConnection = onEditConnection,
        onConnect = onConnect,
        onExportRequested = { exportLauncher.launch("cossh_backup.zip") },
        onImportRequested = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionListScreenContent(
    profiles: List<ConnectionProfile>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onAddConnection: () -> Unit,
    onEditConnection: (String) -> Unit,
    onConnect: (String) -> Unit,
    onExportRequested: () -> Unit = {},
    onImportRequested: () -> Unit = {},
    initialMenuExpanded: Boolean = false
) {
    var menuExpanded by remember { mutableStateOf(initialMenuExpanded) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CoSSH Connections") },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Export Backup") },
                            onClick = {
                                menuExpanded = false
                                onExportRequested()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Import Backup") },
                            onClick = {
                                menuExpanded = false
                                onImportRequested()
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddConnection) {
                Icon(Icons.Filled.Add, contentDescription = "Add Connection")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search connections...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                singleLine = true
            )

            val context = LocalContext.current
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(profiles) { profile ->
                    ConnectionItem(
                        profile = profile,
                        onClick = {
                            Log.d("ConnectionListScreen", "Connecting to ${profile.nickname} (${profile.host})")
                            val intent = android.content.Intent(context, com.adamoutler.ssh.network.SshService::class.java).apply {
                                action = com.adamoutler.ssh.network.SshService.ACTION_START
                                putExtra(com.adamoutler.ssh.network.SshService.EXTRA_PROFILE_ID, profile.id)
                            }
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }
                            onConnect(profile.id)
                        },
                        onEdit = { onEditConnection(profile.id) }
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ConnectionItem(profile: ConnectionProfile, onClick: () -> Unit, onEdit: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onEdit
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = profile.nickname, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "${profile.username}@${profile.host}:${profile.port}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
