package com.adamoutler.ssh.ui.screens.connectionlist.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionListTopBar(
    onExportRequested: () -> Unit,
    onImportRequested: () -> Unit,
    modifier: Modifier = Modifier,
    initialMenuExpanded: Boolean = false
) {
    var menuExpanded by remember { mutableStateOf(initialMenuExpanded) }

    TopAppBar(
        title = { Text("CoSSH Connections") },
        actions = {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Export Backup") },
                    onClick = {
                        menuExpanded = false
                        onExportRequested()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Import Backup") },
                    onClick = {
                        menuExpanded = false
                        onImportRequested()
                    }
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = modifier
    )
}
