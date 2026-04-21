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
import com.adamoutler.ssh.ui.screens.connectionlist.components.GroupedConnectionList
import com.adamoutler.ssh.ui.screens.connectionlist.components.SearchBar
import com.adamoutler.ssh.ui.screens.connectionlist.components.MoveToFolderBottomSheet

@Composable
fun ConnectionListContent(
    groupedProfiles: Map<String?, List<ConnectionProfile>>,
    searchQuery: String,
    activeConnections: Set<String> = emptySet(),
    onSearchQueryChange: (String) -> Unit,
    onAddConnection: () -> Unit,
    onEditConnection: (String) -> Unit,
    onDeleteConnection: (String) -> Unit,
    onConnect: (String) -> Unit,
    onMoveToFolder: (String, String?) -> Unit = { _, _ -> },
    onExportRequested: () -> Unit = {},
    onImportRequested: () -> Unit = {},
    initialMenuExpanded: Boolean = false
) {
    var profileIdMovingToFolder by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            ConnectionListTopBar(
                onExportRequested = onExportRequested,
                onImportRequested = onImportRequested,
                initialMenuExpanded = initialMenuExpanded
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddConnection) {
                Icon(Icons.Filled.Add, contentDescription = "Add Connection")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SearchBar(
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                modifier = Modifier.padding(16.dp)
            )

            GroupedConnectionList(
                groupedProfiles = groupedProfiles,
                activeConnections = activeConnections,
                onConnect = onConnect,
                onEditConnection = onEditConnection,
                onDeleteConnection = onDeleteConnection,
                onMoveToFolder = { profileIdMovingToFolder = it }
            )
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
            onDismiss = { profileIdMovingToFolder = null }
        )
    }
}
