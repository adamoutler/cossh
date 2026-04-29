package com.adamoutler.ssh.ui.screens.connectionlist.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.adamoutler.ssh.data.ConnectionProfile

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GroupedConnectionList(
    groupedProfiles: Map<String?, List<ConnectionProfile>>,
    activeConnectionCounts: Map<String, Int>,
    onConnect: (String) -> Unit,
    onEditConnection: (String) -> Unit,
    onDeleteConnection: (String) -> Unit,
    onMoveToFolder: (String) -> Unit,
    modifier: Modifier = Modifier,
    defaultGroupName: String = com.adamoutler.ssh.crypto.SettingsManager(LocalContext.current).defaultGroupName
) {
    val context = LocalContext.current
    var profileToDelete by remember { mutableStateOf<ConnectionProfile?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val showHeaders = groupedProfiles.size > 1 || groupedProfiles.keys.firstOrNull() != null
        
        groupedProfiles.forEach { (folderId, profiles) ->
            if (showHeaders) {
                stickyHeader {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = folderId ?: defaultGroupName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(profiles, key = { it.id }) { profile ->
                val activeCount = activeConnectionCounts[profile.id] ?: 0
                if (com.adamoutler.ssh.network.ConnectionStateRepository.isHeadlessTest) {
                    ConnectionItem(
                        profile = profile,
                        activeCount = activeCount,
                        onClick = { onConnect(profile.id) },
                        onEdit = { onEditConnection(profile.id) }
                    )
                } else {
                    val currentConfig = androidx.compose.ui.platform.LocalViewConfiguration.current
                    val customConfig = remember(currentConfig) {
                        object : androidx.compose.ui.platform.ViewConfiguration by currentConfig {
                            override val touchSlop: Float
                                get() = currentConfig.touchSlop * 2.5f
                        }
                    }

                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.ui.platform.LocalViewConfiguration provides customConfig
                    ) {
                        val dismissState = rememberSwipeToDismissBoxState()
                        
                        LaunchedEffect(dismissState.currentValue) {
                            if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                                profileToDelete = profile
                                dismissState.reset()
                            } else if (dismissState.currentValue == SwipeToDismissBoxValue.StartToEnd) {
                                onMoveToFolder(profile.id)
                                dismissState.reset()
                            }
                        }

                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                val color = when (dismissState.dismissDirection) {
                                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primary
                                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                                    else -> Color.Transparent
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                        .background(color, shape = MaterialTheme.shapes.medium),
                                    contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                                ) {
                                    if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "Move",
                                            modifier = Modifier.padding(start = 16.dp),
                                            tint = Color.White
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            modifier = Modifier.padding(end = 16.dp),
                                            tint = Color.White
                                        )
                                    }
                                }
                            },
                            content = {
                                ConnectionItem(
                                    profile = profile,
                                    activeCount = activeCount,
                                    onClick = { onConnect(profile.id) },
                                    onEdit = { onEditConnection(profile.id) }
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    if (profileToDelete != null) {
        AlertDialog(
            onDismissRequest = { profileToDelete = null },
            title = { Text("Delete Connection") },
            text = { Text("Are you sure you want to delete '${profileToDelete?.nickname}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        profileToDelete?.id?.let { onDeleteConnection(it) }
                        profileToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { profileToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
