package com.adamoutler.ssh.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties

@Composable
fun KeystoreInvalidatedDialog(
    onConfirmReset: () -> Unit,
    onDismissApp: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Empty to make it non-cancelable by tapping outside */ },
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Security Warning",
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = "Security Reset Required",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = "A change to your device's security settings (such as a new fingerprint) has locked your secure storage.\n\nTo protect your data, your secure storage must be reset. All saved data will be wiped.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirmReset,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Wipe Storage & Reset")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissApp) {
                Text("Close App")
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    )
}