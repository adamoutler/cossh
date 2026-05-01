package com.adamoutler.ssh.ui.screens.connectionlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.ui.screens.connectionlist.components.ConnectionListTopBar
import com.adamoutler.ssh.ui.screens.connectionlist.components.DraggableConnectionList
import com.adamoutler.ssh.ui.screens.connectionlist.components.GroupedConnectionList
import com.adamoutler.ssh.ui.screens.connectionlist.components.MoveToFolderBottomSheet
import com.adamoutler.ssh.ui.screens.connectionlist.components.SearchBar

@Composable
fun ConnectionListContent(
    groupedProfiles: Map<String?, List<ConnectionProfile>>,
    profiles: List<ConnectionProfile>,
    searchQuery: String,
    activeConnectionCounts: Map<String, Int> = emptyMap(),
    onSearchQueryChange: (String) -> Unit,
    onAddConnection: () -> Unit,
    onEditConnection: (String) -> Unit,
    onDeleteConnection: (String) -> Unit,
    onConnect: (String) -> Unit,
    onMoveProfile: (Int, Int) -> Unit = { _, _ -> },
    onMoveToFolder: (String, String?) -> Unit = { _, _ -> },
    onExportRequested: () -> Unit = {},
    onImportRequested: () -> Unit = {},
    onSettingsRequested: () -> Unit = {},
    onManageIdentitiesRequested: () -> Unit = {},
    initialMenuExpanded: Boolean = false,
    isReorderingPreview: Boolean = false,
    defaultGroupName: String = com.adamoutler.ssh.crypto.SettingsManager(androidx.compose.ui.platform.LocalContext.current).defaultGroupName,
) {
    var profileIdMovingToFolder by remember { mutableStateOf<String?>(null) }
    var isReordering by remember { mutableStateOf(isReorderingPreview) }

    Scaffold(
        topBar = {
            ConnectionListTopBar(
                onExportRequested = onExportRequested,
                onImportRequested = onImportRequested,
                onSettingsRequested = onSettingsRequested,
                onManageIdentitiesRequested = onManageIdentitiesRequested,
                onReorderRequested = { isReordering = true },
                onReorderDone = { isReordering = false },
                isReordering = isReordering,
                initialMenuExpanded = initialMenuExpanded,
            )
        },
        floatingActionButton = {
            if (!isReordering) {
                FloatingActionButton(onClick = onAddConnection) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Connection")
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (!isReordering) {
                SearchBar(
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    modifier = Modifier.padding(16.dp),
                )

                GroupedConnectionList(
                    groupedProfiles = groupedProfiles,
                    activeConnectionCounts = activeConnectionCounts,
                    onConnect = onConnect,
                    onEditConnection = onEditConnection,
                    onDeleteConnection = onDeleteConnection,
                    onMoveToFolder = { profileId -> profileIdMovingToFolder = profileId },
                    defaultGroupName = defaultGroupName,
                )
            } else {
                DraggableConnectionList(
                    profiles = profiles,
                    activeConnectionCounts = activeConnectionCounts,
                    onMoveProfile = onMoveProfile,
                    onConnect = {}, // disable connecting while reordering
                    onEditConnection = {}, // disable editing while reordering
                )
            }
        }
    }

    if (profileIdMovingToFolder != null) {
        val folders = (groupedProfiles.keys.toList() + null).distinct()
        MoveToFolderBottomSheet(
            folders = folders,
            onFolderSelected = { folderId ->
                onMoveToFolder(profileIdMovingToFolder!!, folderId)
                profileIdMovingToFolder = null
            },
            onDismiss = { profileIdMovingToFolder = null },
        )
    }
}
