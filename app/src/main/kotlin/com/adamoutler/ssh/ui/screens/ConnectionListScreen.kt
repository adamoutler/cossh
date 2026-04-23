package com.adamoutler.ssh.ui.screens

import android.net.Uri
import android.widget.Toast
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adamoutler.ssh.network.ConnectionStateRepository
import com.adamoutler.ssh.network.SshService
import com.adamoutler.ssh.ui.screens.connectionlist.ConnectionListContent

@Composable
fun ConnectionListScreen(
    viewModel: ConnectionListViewModel = viewModel(),
    onAddConnection: () -> Unit,
    onEditConnection: (String) -> Unit,
    onConnect: (String, String?) -> Unit,
    onSettingsRequested: () -> Unit
) {
    val groupedProfiles by viewModel.groupedProfiles.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val activeConnectionCounts by ConnectionStateRepository.activeConnectionCounts.collectAsState()
    
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

    val context = LocalContext.current
    var profileIdToConnect by remember { mutableStateOf<String?>(null) }

    if (profileIdToConnect != null) {
        val profileToConnect = groupedProfiles.values.flatten().find { it.id == profileIdToConnect }
        val profileName = profileToConnect?.nickname ?: profileToConnect?.host ?: "Connection"
        val activeCount = activeConnectionCounts[profileIdToConnect] ?: 0
        if (activeCount > 0) {
            // Connection Resume / Multiple Sessions Logic:
            // When tapping a connection that is already active (one or more background sessions running),
            // display a dialog showing the specific connection's name.
            // The dialog lists all active sessions for this profile so the user can explicitly
            // select which session to resume, or opt to start a fresh connection.
            val activeSessions = ConnectionStateRepository.sessions.values
                .filter { it.profileId == profileIdToConnect }
                .sortedBy { it.connectedAt }
            
            AlertDialog(
                onDismissRequest = { profileIdToConnect = null },
                title = { Text("Active Sessions: $profileName") },
                text = {
                    Column {
                        Text("This connection is already active. Select a session to resume or start a new one.")
                        Spacer(modifier = Modifier.height(8.dp))
                        androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                            items(activeSessions.size) { index ->
                                val session = activeSessions[index]
                                val dateStr = java.text.SimpleDateFormat("MMM dd, HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(session.connectedAt))
                                TextButton(onClick = {
                                    onConnect(profileIdToConnect!!, session.sessionId)
                                    profileIdToConnect = null
                                }) { 
                                    Text("Resume Session ${index + 1} ($dateStr)") 
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val newSessionId = java.util.UUID.randomUUID().toString()
                        val intent = Intent(context, SshService::class.java).apply {
                            action = SshService.ACTION_START
                            putExtra(SshService.EXTRA_PROFILE_ID, profileIdToConnect)
                            putExtra(SshService.EXTRA_SESSION_ID, newSessionId)
                        }
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }
                        onConnect(profileIdToConnect!!, newSessionId)
                        profileIdToConnect = null
                    }) { Text("Start New") }
                },
                dismissButton = {
                    TextButton(onClick = { profileIdToConnect = null }) { Text("Cancel") }
                }
            )
        } else {
            val newSessionId = java.util.UUID.randomUUID().toString()
            val intent = Intent(context, SshService::class.java).apply {
                action = SshService.ACTION_START
                putExtra(SshService.EXTRA_PROFILE_ID, profileIdToConnect)
                putExtra(SshService.EXTRA_SESSION_ID, newSessionId)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            onConnect(profileIdToConnect!!, newSessionId)
            profileIdToConnect = null
        }
    }

    ConnectionListContent(
        groupedProfiles = groupedProfiles,
        searchQuery = searchQuery,
        activeConnectionCounts = activeConnectionCounts,
        onSearchQueryChange = { viewModel.updateSearchQuery(it) },
        onAddConnection = onAddConnection,
        onEditConnection = onEditConnection,
        onDeleteConnection = { viewModel.deleteProfile(it) },
        onConnect = { profileId -> profileIdToConnect = profileId },
        onMoveToFolder = { profileId, folderId -> viewModel.moveToFolder(profileId, folderId) },
        onExportRequested = { exportLauncher.launch("cossh_backup.zip") },
        onImportRequested = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
        onSettingsRequested = onSettingsRequested
    )
}
