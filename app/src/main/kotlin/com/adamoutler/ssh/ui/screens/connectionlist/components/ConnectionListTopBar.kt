package com.adamoutler.ssh.ui.screens.connectionlist.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionListTopBar(
    onExportRequested: () -> Unit,
    onImportRequested: () -> Unit,
    onSettingsRequested: () -> Unit,
    onManageIdentitiesRequested: () -> Unit,
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
                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                DropdownMenuItem(
                    modifier = Modifier.testTag("ManageIdentitiesMenu"),
                    text = { Text("Manage Identities") },
                    onClick = {
                        menuExpanded = false
                        onManageIdentitiesRequested()
                    },
                    trailingIcon = {
                        IconButton(onClick = { uriHandler.openUri("https://github.com/adamoutler/ssh/wiki") }) {
                            Icon(Icons.Outlined.Info, contentDescription = "Learn more about Manage Identities", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                )
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
                DropdownMenuItem(
                    text = { Text("Settings") },
                    onClick = {
                        menuExpanded = false
                        onSettingsRequested()
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
