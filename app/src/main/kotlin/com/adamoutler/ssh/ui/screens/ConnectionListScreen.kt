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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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

    var showExportPasswordDialog by remember { mutableStateOf<Uri?>(null) }
    var showImportPasswordDialog by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            showExportPasswordDialog = uri
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            showImportPasswordDialog = uri
        }
    }

    val context = LocalContext.current

    if (showExportPasswordDialog != null) {
        var password by remember { mutableStateOf("") }
        var confirmPassword by remember { mutableStateOf("") }
        var passwordVisible by remember { mutableStateOf(false) }
        
        val isPasswordStrong = password.length >= 8
        val doPasswordsMatch = password == confirmPassword && password.isNotEmpty()
        val isFormValid = isPasswordStrong && doPasswordsMatch

        AlertDialog(
            onDismissRequest = { 
                showExportPasswordDialog = null
                password = ""
                confirmPassword = ""
            },
            title = { Text("Secure Backup", color = MaterialTheme.colorScheme.primary) },
            text = {
                Column {
                    Text(
                        "Create a strong password to encrypt your connection profiles. You will need this to restore your backup.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Backup Password") },
                        singleLine = true,
                        isError = password.isNotEmpty() && !isPasswordStrong,
                        supportingText = if (password.isNotEmpty() && !isPasswordStrong) {
                            { Text("Password must be at least 8 characters") }
                        } else null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next
                        ),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm Password") },
                        singleLine = true,
                        isError = confirmPassword.isNotEmpty() && !doPasswordsMatch,
                        supportingText = if (confirmPassword.isNotEmpty() && !doPasswordsMatch) {
                            { Text("Passwords do not match") }
                        } else null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = showExportPasswordDialog!!
                        val charArray = password.toCharArray()
                        
                        password = ""
                        confirmPassword = ""
                        
                        viewModel.exportBackup(uri, charArray) { success ->
                            charArray.fill('\u0000')
                            if (success) {
                                Toast.makeText(context, "Export successful", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                        showExportPasswordDialog = null
                    },
                    enabled = isFormValid
                ) { 
                    Text("Export Encrypted Backup") 
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showExportPasswordDialog = null
                    password = ""
                    confirmPassword = ""
                }) { Text("Cancel") }
            }
        )
    }

    if (showImportPasswordDialog != null) {
        var password by remember { mutableStateOf("") }
        var passwordVisible by remember { mutableStateOf(false) }
        
        AlertDialog(
            onDismissRequest = { 
                showImportPasswordDialog = null
                password = ""
            },
            title = { Text("Import Backup", color = MaterialTheme.colorScheme.primary) },
            text = {
                Column {
                    Text(
                        "Enter the password used to encrypt this backup.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = showImportPasswordDialog!!
                        val charArray = password.toCharArray()
                        
                        password = ""
                        
                        viewModel.importBackup(uri, charArray) { success ->
                            charArray.fill('\u0000')
                            if (success) {
                                Toast.makeText(context, "Import successful", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Import failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                        showImportPasswordDialog = null
                    },
                    enabled = password.isNotEmpty()
                ) { Text("Import Backup") }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showImportPasswordDialog = null
                    password = ""
                }) { Text("Cancel") }
            }
        )
    }

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
                        ConnectionStateRepository.clearConnectionState(profileIdToConnect!!)
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
