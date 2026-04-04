package com.adamoutler.ssh.ui.keys

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.adamoutler.ssh.crypto.SSHKeyGenerator

data class SshKeyDisplay(val id: String, val algorithm: String, val publicKeyBase64: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyManagementScreen() {
    // Security Mandate: Keys cannot be leaked via screenshots or Recent Apps.
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? ComponentActivity)?.window
        window?.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // In a real app, this state would come from a ViewModel and Repository.
    // For SSH-11 proof of concept, we hold a temporary state list.
    var keys by remember { mutableStateOf(listOf<SshKeyDisplay>()) }
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Key Management") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Generate New Key")
            }
        }
    ) { padding ->
        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("Generate New Key") },
                text = { Text("Select the algorithm for your new SSH Key.") },
                confirmButton = {
                    TextButton(onClick = {
                        val keyPair = SSHKeyGenerator.generateEd25519KeyPair()
                        val newKey = SshKeyDisplay(
                            id = "key-${keys.size + 1}",
                            algorithm = "Ed25519",
                            publicKeyBase64 = SSHKeyGenerator.encodePublicKey(keyPair)
                        )
                        keys = keys + newKey
                        showDialog = false
                    }) {
                        Text("ED25519")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        val keyPair = SSHKeyGenerator.generateRSAKeyPair()
                        val newKey = SshKeyDisplay(
                            id = "key-${keys.size + 1}",
                            algorithm = "RSA-4096",
                            publicKeyBase64 = SSHKeyGenerator.encodePublicKey(keyPair)
                        )
                        keys = keys + newKey
                        showDialog = false
                    }) {
                        Text("RSA-4096")
                    }
                }
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(keys) { key ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Algorithm: ${key.algorithm}", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = key.publicKeyBase64,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 3
                        )
                    }
                }
            }
        }
    }
}
