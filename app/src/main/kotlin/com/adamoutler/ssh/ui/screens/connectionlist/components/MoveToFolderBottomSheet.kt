package com.adamoutler.ssh.ui.screens.connectionlist.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveToFolderBottomSheet(
    folders: List<String?>,
    onFolderSelected: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var newFolderName by remember { mutableStateOf("") }
    var isAddingNewFolder by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text("Move to Folder", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))

            if (isAddingNewFolder) {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("New Folder Name") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = {
                            if (newFolderName.isNotEmpty()) {
                                onFolderSelected(newFolderName)
                            }
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Create")
                        }
                    }
                )
                TextButton(onClick = { isAddingNewFolder = false }) {
                    Text("Cancel")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        ListItem(
                            headlineContent = { Text("New Folder...") },
                            leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                            modifier = Modifier.clickable { isAddingNewFolder = true }
                        )
                    }
                    items(folders) { folderId ->
                        ListItem(
                            headlineContent = { Text(folderId ?: com.adamoutler.ssh.crypto.SettingsManager(androidx.compose.ui.platform.LocalContext.current).defaultGroupName) },
                            leadingContent = { Icon(Icons.Default.List, contentDescription = null) },
                            modifier = Modifier.clickable { onFolderSelected(folderId) }
                        )
                    }
                }
            }
        }
    }
}
