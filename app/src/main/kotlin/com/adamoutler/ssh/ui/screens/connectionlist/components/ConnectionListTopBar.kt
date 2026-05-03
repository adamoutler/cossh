package com.adamoutler.ssh.ui.screens.connectionlist.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionListTopBar(
    onExportRequested: () -> Unit,
    onImportRequested: () -> Unit,
    onSettingsRequested: () -> Unit,
    onManageIdentitiesRequested: () -> Unit,
    modifier: Modifier = Modifier,
    onReorderRequested: () -> Unit = {},
    onReorderDone: () -> Unit = {},
    isReordering: Boolean = false,
    initialMenuExpanded: Boolean = false,
) {
    var menuExpanded by remember { mutableStateOf(initialMenuExpanded) }

    TopAppBar(
        title = { Text(if (isReordering) "Reorder Connections" else "CoSSH Connections") },
        actions = {
            if (isReordering) {
                IconButton(onClick = onReorderDone) {
                    Icon(Icons.Filled.Check, contentDescription = "Done Reordering")
                }
            } else {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Reorder Connections") },
                        onClick = {
                            menuExpanded = false
                            onReorderRequested()
                        },
                    )
                    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                    DropdownMenuItem(
                        modifier = Modifier.testTag("ManageIdentitiesMenu"),
                        text = { Text("Manage Identities") },
                        onClick = {
                            menuExpanded = false
                            onManageIdentitiesRequested()
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { uriHandler.openUri("https://github.com/adamoutler/ssh/wiki") },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clearAndSetSemantics {
                                        contentDescription = "Learn more about Manage Identities. Opens external browser."
                                        role = Role.Button
                                    },
                            ) {
                                Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Export Backup") },
                        onClick = {
                            menuExpanded = false
                            onExportRequested()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Import Backup") },
                        onClick = {
                            menuExpanded = false
                            onImportRequested()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        onClick = {
                            menuExpanded = false
                            onSettingsRequested()
                        },
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = modifier,
    )
}
