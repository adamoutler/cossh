package com.adamoutler.ssh.ui.screens.connectionlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.ui.screens.connectionlist.components.ConnectionListTopBar
import com.adamoutler.ssh.ui.screens.connectionlist.components.DraggableConnectionList
import com.adamoutler.ssh.ui.screens.connectionlist.components.SearchBar

@Composable
fun ConnectionListContent(
    profiles: List<ConnectionProfile>,
    searchQuery: String,
    activeConnections: Set<String> = emptySet(),
    onSearchQueryChange: (String) -> Unit,
    onAddConnection: () -> Unit,
    onEditConnection: (String) -> Unit,
    onConnect: (String) -> Unit,
    onMoveProfile: (Int, Int) -> Unit = { _, _ -> },
    onExportRequested: () -> Unit = {},
    onImportRequested: () -> Unit = {},
    initialMenuExpanded: Boolean = false
) {
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

            DraggableConnectionList(
                profiles = profiles,
                activeConnections = activeConnections,
                onMoveProfile = onMoveProfile,
                onConnect = onConnect,
                onEditConnection = onEditConnection
            )
        }
    }
}
