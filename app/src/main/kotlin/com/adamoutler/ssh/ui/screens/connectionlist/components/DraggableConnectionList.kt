package com.adamoutler.ssh.ui.screens.connectionlist.components

import android.util.Log
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.network.SshService

@Composable
fun DraggableConnectionList(
    profiles: List<ConnectionProfile>,
    activeConnectionCounts: Map<String, Int>,
    onMoveProfile: (Int, Int) -> Unit,
    onConnect: (String) -> Unit,
    onEditConnection: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                            offset.y.toInt() in it.offset..(it.offset + it.size)
                        }
                        if (itemInfo != null) {
                            draggedItemIndex = itemInfo.index
                            dragOffset = 0f
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffset += dragAmount.y
                        
                        val draggedIndex = draggedItemIndex ?: return@detectDragGesturesAfterLongPress
                        val draggedItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggedIndex }
                        
                        if (draggedItem != null) {
                            val currentCenter = draggedItem.offset + dragOffset + (draggedItem.size / 2)
                            val targetItem = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                it.index != draggedIndex && currentCenter.toInt() in it.offset..(it.offset + it.size)
                            }
                            
                            if (targetItem != null) {
                                onMoveProfile(draggedIndex, targetItem.index)
                                draggedItemIndex = targetItem.index
                                dragOffset = 0f
                            }
                        }
                    },
                    onDragEnd = {
                        draggedItemIndex = null
                        dragOffset = 0f
                    },
                    onDragCancel = {
                        draggedItemIndex = null
                        dragOffset = 0f
                    }
                )
            }
    ) {
        itemsIndexed(profiles, key = { _, profile -> profile.id }) { index, profile ->
            val isDragging = index == draggedItemIndex
            val translationY = if (isDragging) dragOffset else 0f
            
            Box(
                modifier = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer { this.translationY = translationY }
            ) {
                ConnectionItem(
                    profile = profile,
                    activeCount = activeConnectionCounts[profile.id] ?: 0,
                    elevation = if (isDragging) 8.dp else 2.dp,
                    onClick = {
                        Log.d("ConnectionListScreen", "Connecting to \${profile.nickname} (\${profile.host})")
                        val intent = android.content.Intent(context, SshService::class.java).apply {
                            action = SshService.ACTION_START
                            putExtra(SshService.EXTRA_PROFILE_ID, profile.id)
                        }
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }
                        onConnect(profile.id)
                    },
                    onEdit = { onEditConnection(profile.id) }
                )
            }
        }
    }
}
