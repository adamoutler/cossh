package com.adamoutler.ssh.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicReference

@Composable
fun ConnectionFailedDialog(
    errorMessage: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connection Failed") },
        text = { Text("Error: $errorMessage") },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
    )
}

@Composable
fun KeepAliveDialog(
    onDismiss: () -> Unit,
    onKeepAlive: () -> Unit,
    onTerminate: () -> Unit,
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
        },
    )
}

@Composable
fun TerminateConfirmDialog(
    onDismiss: () -> Unit,
    onTerminate: () -> Unit,
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
        },
    )
}

@Composable
fun SessionDisconnectedDialog(
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Session Disconnected") },
        text = { Text("The SSH session has ended or the connection was lost.") },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
    )
}

@Composable
fun AuthPromptDialog(
    requireUsername: Boolean,
    isRetry: Boolean,
    onConfirm: (com.adamoutler.ssh.network.AuthCredentials) -> Unit,
    onDismiss: () -> Unit,
) {
    val usernameState = remember { mutableStateOf("") }
    val passwordBuffer = remember { AtomicReference(CharArray(0)) }

    AlertDialog(
        onDismissRequest = {
            passwordBuffer.get().fill('\u0000')
            onDismiss()
        },
        title = { Text(if (isRetry) "Authentication Failed" else "Credentials Required") },
        text = {
            Column {
                if (isRetry) {
                    Text(
                        text = "Please try again.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                } else {
                    Text(
                        text = "Enter your credentials to connect.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                if (requireUsername) {
                    OutlinedTextField(
                        value = usernameState.value,
                        onValueChange = { usernameState.value = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                SecurePasswordEditText(
                    hint = "Password",
                    onPasswordChanged = {
                        passwordBuffer.get().fill('\u0000')
                        passwordBuffer.set(it)
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val pass = passwordBuffer.get()
                    onConfirm(com.adamoutler.ssh.network.AuthCredentials(usernameState.value, pass.clone()))
                    pass.fill('\u0000')
                },
            ) { Text("Connect") }
        },
        dismissButton = {
            TextButton(onClick = {
                passwordBuffer.get().fill('\u0000')
                onDismiss()
            }) { Text("Cancel") }
        },
    )
}
