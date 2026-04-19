package com.adamoutler.ssh.ui.screens.connectionlist.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.adamoutler.ssh.data.ConnectionProfile

@Composable
fun ActiveSessionSelectorDialog(
    activeConnections: Set<String>,
    profiles: List<ConnectionProfile>,
    onSelectSession: (String) -> Unit,
    onStartNew: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Active Sessions") },
        text = {
            Column {
                Text("You have multiple active sessions. Select one to resume or start a new one.")
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn {
                    items(activeConnections.size) { index ->
                        val profileId = activeConnections.toList()[index]
                        val profile = profiles.find { it.id == profileId }
                        TextButton(onClick = { 
                            onSelectSession(profileId) 
                        }) {
                            Text(profile?.nickname ?: profileId)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onStartNew) {
                Text("Start New")
            }
        }
    )
}
