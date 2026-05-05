package com.adamoutler.ssh.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    onSettingsRequested: () -> Unit,
    onManageIdentitiesRequested: () -> Unit,
) {
    val groupedProfiles by viewModel.groupedProfiles.collectAsState()
    val flatItems by viewModel.flatItems.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val activeConnectionCounts by ConnectionStateRepository.activeConnectionCounts.collectAsState()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.loadProfiles()
    }

    var showExportPasswordDialog by remember { mutableStateOf<Uri?>(null) }
    var showImportPasswordDialog by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) {
            showExportPasswordDialog = uri
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            showImportPasswordDialog = uri
        }
    }

    val context = LocalContext.current

    if (showExportPasswordDialog != null) {
        val passwordBuffer = remember { java.util.concurrent.atomic.AtomicReference(CharArray(0)) }
        val confirmPasswordBuffer = remember { java.util.concurrent.atomic.AtomicReference(CharArray(0)) }
        var isPasswordStrong by remember { mutableStateOf(false) }
        var doPasswordsMatch by remember { mutableStateOf(false) }

        val isFormValid = isPasswordStrong && doPasswordsMatch

        val checkForm = {
            val pass = passwordBuffer.get()
            val confirm = confirmPasswordBuffer.get()
            isPasswordStrong = pass.size >= 8
            doPasswordsMatch = pass.isNotEmpty() && pass.contentEquals(confirm)
        }

        AlertDialog(
            onDismissRequest = {
                showExportPasswordDialog = null
                passwordBuffer.get().fill('\u0000')
                confirmPasswordBuffer.get().fill('\u0000')
            },
            title = { Text("Secure Backup", color = MaterialTheme.colorScheme.primary) },
            text = {
                Column {
                    Text(
                        "Create a strong password to encrypt your connection profiles. You will need this to restore your backup.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    com.adamoutler.ssh.ui.components.SecurePasswordEditText(
                        hint = "Backup Password",
                        onPasswordChanged = {
                            passwordBuffer.get().fill('\u0000')
                            passwordBuffer.set(it)
                            checkForm()
                        },
                    )

                    if (!isPasswordStrong && passwordBuffer.get().isNotEmpty()) {
                        Text("Password must be at least 8 characters", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    com.adamoutler.ssh.ui.components.SecurePasswordEditText(
                        hint = "Confirm Password",
                        onPasswordChanged = {
                            confirmPasswordBuffer.get().fill('\u0000')
                            confirmPasswordBuffer.set(it)
                            checkForm()
                        },
                    )

                    if (!doPasswordsMatch && confirmPasswordBuffer.get().isNotEmpty()) {
                        Text("Passwords do not match", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = showExportPasswordDialog!!
                        val pass = passwordBuffer.get()

                        viewModel.exportBackup(uri, pass) { success ->
                            pass.fill('\u0000')
                            confirmPasswordBuffer.get().fill('\u0000')
                            if (success) {
                                Toast.makeText(context, "Export successful", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                        showExportPasswordDialog = null
                    },
                    enabled = isFormValid,
                ) {
                    Text("Export Encrypted Backup")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExportPasswordDialog = null
                    passwordBuffer.get().fill('\u0000')
                    confirmPasswordBuffer.get().fill('\u0000')
                }) { Text("Cancel") }
            },
        )
    }

    if (showImportPasswordDialog != null) {
        val passwordBuffer = remember { java.util.concurrent.atomic.AtomicReference(CharArray(0)) }
        var hasPassword by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = {
                showImportPasswordDialog = null
                passwordBuffer.get().fill('\u0000')
            },
            title = { Text("Import Backup", color = MaterialTheme.colorScheme.primary) },
            text = {
                Column {
                    Text(
                        "Enter the password used to encrypt this backup.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    com.adamoutler.ssh.ui.components.SecurePasswordEditText(
                        hint = "Password",
                        onPasswordChanged = {
                            passwordBuffer.get().fill('\u0000')
                            passwordBuffer.set(it)
                            hasPassword = it.isNotEmpty()
                        },
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = showImportPasswordDialog!!
                        val pass = passwordBuffer.get()

                        viewModel.importBackup(uri, pass) { success ->
                            pass.fill('\u0000')
                            if (success) {
                                Toast.makeText(context, "Import successful", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Import failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                        showImportPasswordDialog = null
                    },
                    enabled = hasPassword,
                ) { Text("Import Backup") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportPasswordDialog = null
                    passwordBuffer.get().fill('\u0000')
                }) { Text("Cancel") }
            },
        )
    }

    var profileIdToConnect by remember { mutableStateOf<String?>(null) }

    if (profileIdToConnect != null) {
        val profileToConnect = groupedProfiles.values.flatten().find { it.id == profileIdToConnect }
        val profileName = profileToConnect?.nickname ?: profileToConnect?.host ?: "Connection"
        val activeCount = activeConnectionCounts[profileIdToConnect] ?: 0
        if (activeCount > 0) {
            val activeSessions = ConnectionStateRepository.sessions.values
                .filter { it.profileId == profileIdToConnect }
                .sortedBy { it.connectedAt }

            ActiveSessionsDialog(
                profileName = profileName,
                activeSessions = activeSessions,
                onDismiss = { profileIdToConnect = null },
                onResumeSession = { sessionId ->
                    onConnect(profileIdToConnect!!, sessionId)
                    profileIdToConnect = null
                },
                onStartNewSession = {
                    val newSessionId = java.util.UUID.randomUUID().toString()
                    ConnectionStateRepository.clearConnectionState(profileIdToConnect!!)
                    val intent = Intent(context, SshService::class.java).apply {
                        action = SshService.ACTION_START
                        putExtra(SshService.EXTRA_PROFILE_ID, profileIdToConnect)
                        putExtra(SshService.EXTRA_SESSION_ID, newSessionId)
                    }
                    context.startForegroundService(intent)
                    onConnect(profileIdToConnect!!, newSessionId)
                    profileIdToConnect = null
                }
            )
        } else {
            val newSessionId = java.util.UUID.randomUUID().toString()
            val intent = Intent(context, SshService::class.java).apply {
                action = SshService.ACTION_START
                putExtra(SshService.EXTRA_PROFILE_ID, profileIdToConnect)
                putExtra(SshService.EXTRA_SESSION_ID, newSessionId)
            }
            context.startForegroundService(intent)
            onConnect(profileIdToConnect!!, newSessionId)
            profileIdToConnect = null
        }
    }

    ConnectionListContent(
        groupedProfiles = groupedProfiles,
        flatItems = flatItems,
        searchQuery = searchQuery,
        activeConnectionCounts = activeConnectionCounts,
        onSearchQueryChange = { viewModel.updateSearchQuery(it) },
        onAddConnection = onAddConnection,
        onEditConnection = onEditConnection,
        onDeleteConnection = { viewModel.deleteProfile(it) },
        onConnect = { profileId -> profileIdToConnect = profileId },
        onMoveProfile = { fromIndex, toIndex -> viewModel.moveProfileInFlatList(fromIndex, toIndex) },
        onMoveToFolder = { profileId, folderId -> viewModel.moveToFolder(profileId, folderId) },
        onExportRequested = { exportLauncher.launch("connections_and_identities.cossh") },
        onImportRequested = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
        onSettingsRequested = onSettingsRequested,
        onManageIdentitiesRequested = onManageIdentitiesRequested,
    )
}

@com.adamoutler.ssh.ui.screens.Generated
@Composable
fun ExportBackupDialog(
    onDismiss: () -> Unit,
    onExport: (CharArray) -> Unit,
) {
    val passwordBuffer = remember { java.util.concurrent.atomic.AtomicReference(CharArray(0)) }
    val confirmPasswordBuffer = remember { java.util.concurrent.atomic.AtomicReference(CharArray(0)) }
    var isPasswordStrong by remember { mutableStateOf(false) }
    var doPasswordsMatch by remember { mutableStateOf(false) }

    val isFormValid = isPasswordStrong && doPasswordsMatch

    val checkForm = {
        val pass = passwordBuffer.get()
        val confirm = confirmPasswordBuffer.get()
        isPasswordStrong = pass.size >= 8
        doPasswordsMatch = pass.isNotEmpty() && pass.contentEquals(confirm)
    }

    AlertDialog(
        onDismissRequest = {
            onDismiss()
            passwordBuffer.get().fill('\u0000')
            confirmPasswordBuffer.get().fill('\u0000')
        },
        title = { Text("Secure Backup", color = MaterialTheme.colorScheme.primary) },
        text = {
            Column {
                Text(
                    "Create a strong password to encrypt your connection profiles. You will need this to restore your backup.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(16.dp))

                com.adamoutler.ssh.ui.components.SecurePasswordEditText(
                    hint = "Backup Password",
                    onPasswordChanged = {
                        passwordBuffer.get().fill('\u0000')
                        passwordBuffer.set(it)
                        checkForm()
                    },
                )

                if (!isPasswordStrong && passwordBuffer.get().isNotEmpty()) {
                    Text("Password must be at least 8 characters", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(8.dp))

                com.adamoutler.ssh.ui.components.SecurePasswordEditText(
                    hint = "Confirm Password",
                    onPasswordChanged = {
                        confirmPasswordBuffer.get().fill('\u0000')
                        confirmPasswordBuffer.set(it)
                        checkForm()
                    },
                )

                if (!doPasswordsMatch && confirmPasswordBuffer.get().isNotEmpty()) {
                    Text("Passwords do not match", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pass = passwordBuffer.get()
                    onExport(pass)
                    passwordBuffer.get().fill('\u0000')
                    confirmPasswordBuffer.get().fill('\u0000')
                },
                enabled = isFormValid,
            ) {
                Text("Export Encrypted Backup")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onDismiss()
                passwordBuffer.get().fill('\u0000')
                confirmPasswordBuffer.get().fill('\u0000')
            }) { Text("Cancel") }
        },
    )
}

@com.adamoutler.ssh.ui.screens.Generated
@Composable
fun ImportBackupDialog(
    onDismiss: () -> Unit,
    onImport: (CharArray) -> Unit,
) {
    val passwordBuffer = remember { java.util.concurrent.atomic.AtomicReference(CharArray(0)) }
    var hasPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {
            onDismiss()
            passwordBuffer.get().fill('\u0000')
        },
        title = { Text("Import Backup", color = MaterialTheme.colorScheme.primary) },
        text = {
            Column {
                Text(
                    "Enter the password used to encrypt this backup.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(16.dp))
                com.adamoutler.ssh.ui.components.SecurePasswordEditText(
                    hint = "Password",
                    onPasswordChanged = {
                        passwordBuffer.get().fill('\u0000')
                        passwordBuffer.set(it)
                        hasPassword = it.isNotEmpty()
                    },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pass = passwordBuffer.get()
                    onImport(pass)
                    passwordBuffer.get().fill('\u0000')
                },
                enabled = hasPassword,
            ) { Text("Import Backup") }
        },
        dismissButton = {
            TextButton(onClick = {
                onDismiss()
                passwordBuffer.get().fill('\u0000')
            }) { Text("Cancel") }
        },
    )
}

@com.adamoutler.ssh.ui.screens.Generated
@Composable
fun ActiveSessionsDialog(
    profileName: String,
    activeSessions: List<com.adamoutler.ssh.network.ActiveSessionState>,
    onDismiss: () -> Unit,
    onResumeSession: (String) -> Unit,
    onStartNewSession: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
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
                            onResumeSession(session.sessionId)
                        }) {
                            Text("Resume Session ${index + 1} ($dateStr)")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onStartNewSession) { Text("Start New") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
