package com.adamoutler.ssh.ui.screens.connectionlist.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.Protocol

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConnectionItem(
    profile: ConnectionProfile,
    modifier: Modifier = Modifier,
    activeCount: Int = 0,
    isReordering: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onAddToHomeScreen: () -> Unit = {},
    initialMenuExpanded: Boolean = false,
    elevation: Dp = 2.dp,
) {
    var menuExpanded by remember { mutableStateOf(initialMenuExpanded) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("ConnectionItem_${profile.id}")
            .combinedClickable(
                onClick = { if (!isReordering) onClick() },
                onLongClick = { if (!isReordering) onEdit() },
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = if (profile.protocol == Protocol.TELNET) Icons.Filled.LockOpen else Icons.Filled.Lock,
                    contentDescription = if (profile.protocol == Protocol.TELNET) "Telnet Unencrypted" else "SSH Encrypted",
                    tint = if (profile.protocol == Protocol.TELNET) Color(0xFFE65100) else Color(0xFF388E3C),
                    modifier = Modifier.size(32.dp).padding(end = 8.dp),
                )
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = profile.nickname, style = MaterialTheme.typography.titleMedium)
                        if (activeCount > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Badge { Text(activeCount.toString()) }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${profile.username}@${profile.host}:${profile.port} (${profile.protocol.name})",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (isReordering) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Reorder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = dragHandleModifier
                        .padding(start = 16.dp)
                        .size(32.dp),
                )
            } else {
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.testTag("ConnectionMenuButton_${profile.id}"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Options for ${profile.nickname}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.testTag("ConnectionItemDropdown_${profile.id}"),
                    ) {
                        DropdownMenuItem(
                            text = { Text("Add to Home Screen") },
                            leadingIcon = {
                                Icon(Icons.Filled.Home, contentDescription = null)
                            },
                            onClick = {
                                menuExpanded = false
                                onAddToHomeScreen()
                            },
                            modifier = Modifier.testTag("AddToHomeScreen_${profile.id}"),
                        )
                        DropdownMenuItem(
                            text = { Text("Edit Connection") },
                            leadingIcon = {
                                Icon(Icons.Filled.Edit, contentDescription = null)
                            },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            },
                        )
                    }
                }
            }
        }
    }
}
