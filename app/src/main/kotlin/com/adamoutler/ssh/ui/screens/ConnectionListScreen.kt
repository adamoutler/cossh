package com.adamoutler.ssh.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adamoutler.ssh.network.ConnectionStateRepository
import com.adamoutler.ssh.ui.screens.connectionlist.ConnectionListContent

@Composable
fun ConnectionListScreen(
    viewModel: ConnectionListViewModel = viewModel(),
    onAddConnection: () -> Unit,
    onEditConnection: (String) -> Unit,
    onConnect: (String) -> Unit,
    onSettingsRequested: () -> Unit
) {
    val groupedProfiles by viewModel.groupedProfiles.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val activeConnections by ConnectionStateRepository.activeConnections.collectAsState()
    
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.loadProfiles()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackup(uri, "backup_password".toCharArray()) { success ->
                // Handle success/failure (e.g. show Toast)
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importBackup(uri, "backup_password".toCharArray()) { success ->
                // Handle success/failure
            }
        }
    }

    ConnectionListContent(
        groupedProfiles = groupedProfiles,
        searchQuery = searchQuery,
        activeConnections = activeConnections,
        onSearchQueryChange = { viewModel.updateSearchQuery(it) },
        onAddConnection = onAddConnection,
        onEditConnection = onEditConnection,
        onDeleteConnection = { viewModel.deleteProfile(it) },
        onConnect = onConnect,
        onMoveToFolder = { profileId, folderId -> viewModel.moveToFolder(profileId, folderId) },
        onExportRequested = { exportLauncher.launch("cossh_backup.zip") },
        onImportRequested = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
        onSettingsRequested = onSettingsRequested
    )
}
