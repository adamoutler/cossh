package com.adamoutler.ssh.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adamoutler.ssh.network.SshSessionProvider
import com.adamoutler.ssh.ui.screens.connectionlist.ConnectionListContent
import com.adamoutler.ssh.ui.screens.connectionlist.dialogs.ActiveSessionSelectorDialog
import com.adamoutler.ssh.ui.screens.connectionlist.dialogs.BackupPasswordDialog

@Composable
fun ConnectionListScreen(
    viewModel: ConnectionListViewModel = viewModel(),
    onAddConnection: () -> Unit,
    onEditConnection: (String) -> Unit,
    onConnect: (String) -> Unit
) {
    val profiles by viewModel.profiles.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val activeConnections by SshSessionProvider.activeConnections.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var showSessionSelector by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                if (SshSessionProvider.activeConnections.value.size > 1) {
                    showSessionSelector = true
                }
            }
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadProfiles()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
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

    if (showSessionSelector) {
        ActiveSessionSelectorDialog(
            activeConnections = activeConnections,
            profiles = profiles,
            onSelectSession = { profileId ->
                showSessionSelector = false
                onConnect(profileId)
            },
            onStartNew = { showSessionSelector = false },
            onDismiss = { showSessionSelector = false }
        )
    }

    if (showPasswordDialog) {
        BackupPasswordDialog(
            isExporting = isExporting,
            passwordInput = passwordInput,
            onPasswordChange = { passwordInput = it },
            onConfirm = {
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
            },
            onDismiss = {
                showPasswordDialog = false
                pendingExportUri = null
                pendingImportUri = null
            }
        )
    }

    ConnectionListContent(
        profiles = profiles,
        searchQuery = searchQuery,
        activeConnections = activeConnections,
        onSearchQueryChange = { viewModel.updateSearchQuery(it) },
        onAddConnection = onAddConnection,
        onEditConnection = onEditConnection,
        onConnect = onConnect,
        onMoveProfile = { from, to -> viewModel.moveProfile(from, to) },
        onExportRequested = { exportLauncher.launch("cossh_backup.zip") },
        onImportRequested = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
    )
}
