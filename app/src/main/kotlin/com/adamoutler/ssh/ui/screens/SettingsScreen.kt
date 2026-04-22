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
import com.adamoutler.ssh.sync.DriveSyncManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
                    Text(text = "Cloud Sync & Backup", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Securely sync your encrypted profiles across devices using your hidden Google Drive App Data folder.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (!isCloudSyncEnabled) {
                        Button(
                            onClick = {
                                activity?.let { billingManager.purchaseCloudSync(it) }
                            },
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
                        
                        Button(
                            onClick = {
                                scope.launch {
                                    isSyncing = true
                                    activity?.let { driveSyncManager.authenticate(it) }
                                    isSyncing = false
                                }
                            },
                            modifier = Modifier.align(Alignment.End),
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
