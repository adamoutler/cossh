package com.adamoutler.ssh.ui.screens

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.adamoutler.ssh.billing.BillingManager
import com.adamoutler.ssh.crypto.SettingsManager
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.sync.DriveSyncManager
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.adamoutler.ssh.sync.SyncWorker

@Composable
fun SettingsScreen(
    billingManager: BillingManager,
    driveSyncManager: DriveSyncManager,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val isCloudSyncEnabled by billingManager.isCloudSyncEnabled.collectAsState()
    val scope = rememberCoroutineScope()
    var isSyncing by remember { mutableStateOf(false) }
    val settingsManager = remember { SettingsManager(context) }
    val securityStorageManager = remember { SecurityStorageManager(context) }
    var defaultGroupName by remember { mutableStateOf(settingsManager.defaultGroupName) }
    var showPassphraseDialog by remember { mutableStateOf(false) }
    var isPassphraseSet by remember { mutableStateOf(securityStorageManager.getSyncPassphrase() != null) }

    if (showPassphraseDialog) {
        var passphrase by remember { mutableStateOf("") }
        AlertDialog(
            modifier = Modifier.widthIn(max = 320.dp),
            onDismissRequest = { showPassphraseDialog = false },
            title = { Text("Sync Passphrase") },
            text = {
                Column {
                    Text("Enter a secure passphrase to encrypt your backups before they leave this device.")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("Passphrase") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        securityStorageManager.saveSyncPassphrase(passphrase.toCharArray())
                        isPassphraseSet = true
                        showPassphraseDialog = false
                        scope.launch {
                            isSyncing = true
                            try {
                                activity?.let { driveSyncManager.authenticate(it) }
                                WorkManager.getInstance(context).enqueue(
                                    OneTimeWorkRequestBuilder<SyncWorker>().build()
                                )
                            } finally {
                                isSyncing = false
                            }
                        }
                    },
                    enabled = passphrase.isNotBlank()
                ) {
                    Text("Save & Sync")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPassphraseDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    SettingsScreenContent(
        isCloudSyncEnabled = isCloudSyncEnabled,
        isSyncing = isSyncing,
        defaultGroupName = defaultGroupName,
        isPassphraseSet = isPassphraseSet,
        onDefaultGroupNameChange = {
            defaultGroupName = it
            settingsManager.defaultGroupName = if (it.isBlank()) "Uncategorized" else it
        },
        onPurchaseCloudSync = {
            activity?.let { billingManager.purchaseCloudSync(it) }
        },
        onAuthenticateGoogle = {
            if (!isPassphraseSet) {
                showPassphraseDialog = true
            } else {
                scope.launch {
                    isSyncing = true
                    try {
                        activity?.let { driveSyncManager.authenticate(it) }
                        WorkManager.getInstance(context).enqueue(
                            OneTimeWorkRequestBuilder<SyncWorker>().build()
                        )
                    } finally {
                        isSyncing = false
                    }
                }
            }
        },
        onResetPassphrase = {
            showPassphraseDialog = true
        },
        onNavigateBack = onNavigateBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    isCloudSyncEnabled: Boolean,
    isSyncing: Boolean,
    defaultGroupName: String,
    isPassphraseSet: Boolean,
    onDefaultGroupNameChange: (String) -> Unit,
    onPurchaseCloudSync: () -> Unit,
    onAuthenticateGoogle: () -> Unit,
    onResetPassphrase: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "General Settings", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = defaultGroupName,
                        onValueChange = onDefaultGroupNameChange,
                        label = { Text("Default Group Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "Cloud Sync & Backup", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Securely sync your encrypted profiles across devices using your hidden Google Drive App Data folder.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (!isCloudSyncEnabled) {
                        Button(
                            onClick = onPurchaseCloudSync,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Unlock Cloud Sync ($10.00)")
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cloud Sync Enabled", style = MaterialTheme.typography.bodyLarge)
                            Switch(
                                checked = true,
                                onCheckedChange = { /* TODO Disable sync */ }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isPassphraseSet) {
                                TextButton(onClick = onResetPassphrase) {
                                    Text("Reset Passphrase")
                                }
                            } else {
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            
                            Button(
                                onClick = onAuthenticateGoogle,
                                enabled = !isSyncing
                            ) {
                                Text(if (isSyncing) "Authenticating..." else "Authenticate with Google")
                            }
                        }
                    }
                }
            }
        }
    }
}
