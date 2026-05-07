package com.adamoutler.ssh.ui.screens.connectionlist.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.ui.screens.ConnectionListItem

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GroupedConnectionList(
    flatItems: List<ConnectionListItem>,
    activeConnectionCounts: Map<String, Int>,
    modifier: Modifier = Modifier,
    isReordering: Boolean = false,
    onMoveProfile: (Int, Int) -> Unit,
    onConnect: (String) -> Unit,
    onEditConnection: (String) -> Unit,
    onDeleteConnection: (String) -> Unit,
    onMoveToFolder: (String) -> Unit,
) {
    var profileToDelete by remember { mutableStateOf<ConnectionProfile?>(null) }

    val listState = rememberLazyListState()
    var draggedItemKey by remember { mutableStateOf<Any?>(null) }
    var dragOffset by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(
            count = flatItems.size,
            key = { index ->
                when (val item = flatItems[index]) {
                    is ConnectionListItem.Header -> "header_${item.folderId ?: "default"}"
                    is ConnectionListItem.Profile -> item.profile.id
                }
            },
        ) { index ->
            when (val item = flatItems[index]) {
                is ConnectionListItem.Header -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                is ConnectionListItem.Profile -> {
                    val profile = item.profile
                    val activeCount = activeConnectionCounts[profile.id] ?: 0

                    val isDragging = profile.id == draggedItemKey
                    val translationY = if (isDragging) dragOffset else 0f

                    val dragModifier = Modifier.pointerInput(isReordering) {
                        if (!isReordering) return@pointerInput
                        detectDragGestures(
                            onDragStart = { _ ->
                                draggedItemKey = profile.id
                                dragOffset = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset += dragAmount.y
                                val draggedItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggedItemKey }
                                if (draggedItem != null) {
                                    val currentCenter = draggedItem.offset + dragOffset + (draggedItem.size / 2)
                                    val targetItem = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                        it.key != draggedItemKey && currentCenter.toInt() in it.offset..(it.offset + it.size)
                                    }
                                    if (targetItem != null) {
                                        val targetGlobalIndex = targetItem.index
                                        val sourceGlobalIndex = draggedItem.index
                                        onMoveProfile(sourceGlobalIndex, targetGlobalIndex)
                                        dragOffset = 0f
                                    }
                                }
                            },
                            onDragEnd = {
                                draggedItemKey = null
                                dragOffset = 0f
                            },
                            onDragCancel = {
                                draggedItemKey = null
                                dragOffset = 0f
                            },
                        )
                    }

                    Box(
                        modifier = Modifier
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer { this.translationY = translationY },
                    ) {
                        if (isReordering || com.adamoutler.ssh.network.ConnectionStateRepository.isHeadlessTest) {
                            ConnectionItem(
                                profile = profile,
                                activeCount = activeCount,
                                isReordering = isReordering,
                                dragHandleModifier = dragModifier,
                                elevation = if (isDragging) 8.dp else 2.dp,
                                onClick = { onConnect(profile.id) },
                                onEdit = { onEditConnection(profile.id) },
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
                                androidx.compose.ui.platform.LocalViewConfiguration provides customConfig,
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
                                            contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd,
                                        ) {
                                            if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.ArrowForward,
                                                    contentDescription = "Move",
                                                    modifier = Modifier.padding(start = 16.dp),
                                                    tint = Color.White,
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Delete",
                                                    modifier = Modifier.padding(end = 16.dp),
                                                    tint = Color.White,
                                                )
                                            }
                                        }
                                    },
                                    content = {
                                        ConnectionItem(
                                            profile = profile,
                                            activeCount = activeCount,
                                            isReordering = false,
                                            elevation = 2.dp,
                                            onClick = { onConnect(profile.id) },
                                            onEdit = { onEditConnection(profile.id) },
                                        )
                                    },
                                )
                            }
                        }
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
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { profileToDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}
