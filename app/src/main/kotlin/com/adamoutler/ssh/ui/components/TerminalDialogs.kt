package com.adamoutler.ssh.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun ConnectionFailedDialog(
    errorMessage: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connection Failed") },
        text = { Text("Error: $errorMessage") },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    )
}

@Composable
fun KeepAliveDialog(
    onDismiss: () -> Unit,
    onKeepAlive: () -> Unit,
    onTerminate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Keep Session Alive?") },
        text = { Text("Do you want to keep this SSH session running in the background or terminate it?") },
        confirmButton = {
            TextButton(onClick = onKeepAlive) { Text("Keep Alive") }
        },
        dismissButton = {
            TextButton(onClick = onTerminate) { Text("Terminate") }
        }
    )
}

@Composable
fun TerminateConfirmDialog(
    onDismiss: () -> Unit,
    onTerminate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Terminate Connection?") },
        text = { Text("Are you sure you want to terminate this SSH session? All running processes will be killed.") },
        confirmButton = {
            TextButton(onClick = onTerminate) { Text("Terminate") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SessionDisconnectedDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Session Disconnected") },
        text = { Text("The SSH session has ended or the connection was lost.") },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    )
}
