package com.adamoutler.ssh.ui.screens

import android.app.Activity
import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.adamoutler.ssh.BuildConfig
import com.adamoutler.ssh.billing.BillingManager
import com.adamoutler.ssh.sync.DriveSyncManager
import com.adamoutler.ssh.ui.components.GatedFeatureWrapper

@Composable
fun SettingsScreen(
    billingManager: BillingManager,
    driveSyncManager: DriveSyncManager,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val factory = remember {
        viewModelFactory {
            initializer {
                SettingsViewModel(
                    context.applicationContext as Application,
                    billingManager,
                    driveSyncManager,
                )
            }
        }
    }

    val viewModel: SettingsViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.showPassphraseDialog) {
        var passphrase by remember { mutableStateOf("") }
        SyncPassphraseDialog(
            passphrase = passphrase,
            onPassphraseChange = { passphrase = it },
            onDismiss = { viewModel.dismissPassphraseDialog() },
            onConfirm = { activity?.let { viewModel.savePassphraseAndSync(passphrase, it) } },
        )
    }

    SettingsScreenContent(
        isCloudSyncEnabled = uiState.isCloudSyncEnabled,
        isSyncing = uiState.isSyncing,
        defaultGroupName = uiState.defaultGroupName,
        isPassphraseSet = uiState.isPassphraseSet,
        onDefaultGroupNameChange = { viewModel.updateDefaultGroupName(it) },
        onPurchaseCloudSync = { activity?.let { viewModel.purchaseCloudSync(it) } },
        onAuthenticateGoogle = { activity?.let { viewModel.authenticateGoogle(it) } },
        onResetPassphrase = { viewModel.showPassphraseDialog() },
        onNavigateBack = onNavigateBack,
    )
}

@Composable
fun SyncPassphraseDialog(
    passphrase: String,
    onPassphraseChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.widthIn(max = 320.dp),
        onDismissRequest = onDismiss,
        title = { Text("Sync Passphrase") },
        text = {
            Column {
                Text("Enter a secure passphrase to encrypt your backups before they leave this device.")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = onPassphraseChange,
                    label = { Text("Passphrase") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(autoCorrect = false, keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = passphrase.isNotBlank(),
            ) {
                Text("Save & Sync")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
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
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(text = "General Settings", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = defaultGroupName,
                        onValueChange = onDefaultGroupNameChange,
                        label = { Text("Default Group Name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            GatedFeatureWrapper(
                isEnabled = BuildConfig.ENABLE_CLOUD_SYNC,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(text = "Cloud Sync & Backup", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Securely sync your encrypted profiles across devices using your hidden Google Drive App Data folder.",
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        if (!isCloudSyncEnabled) {
                            Button(
                                onClick = onPurchaseCloudSync,
                                modifier = Modifier.align(Alignment.End),
                            ) {
                                Text("Unlock Cloud Sync ($10.00)")
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Cloud Sync Enabled", style = MaterialTheme.typography.bodyLarge)
                                Switch(
                                    checked = true,
                                    onCheckedChange = { /* TODO Disable sync */ },
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
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
                                    enabled = !isSyncing,
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
}
