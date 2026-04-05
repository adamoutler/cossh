package com.adamoutler.ssh.ui.screens

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adamoutler.ssh.data.ConnectionProfile

@Composable
fun ConnectionListScreen(
    viewModel: ConnectionListViewModel = viewModel(),
    onAddConnection: () -> Unit,
    onEditConnection: (String) -> Unit
) {
    val profiles by viewModel.profiles.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadProfiles()
    }

    ConnectionListScreenContent(
        profiles = profiles,
        searchQuery = searchQuery,
        onSearchQueryChange = { viewModel.updateSearchQuery(it) },
        onAddConnection = onAddConnection,
        onEditConnection = onEditConnection
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionListScreenContent(
    profiles: List<ConnectionProfile>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onAddConnection: () -> Unit,
    onEditConnection: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CoSSH Connections") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
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
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search connections...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                singleLine = true
            )

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(profiles) { profile ->
                    ConnectionItem(
                        profile = profile,
                        onClick = {
                            Log.d("ConnectionListScreen", "Connecting to ${profile.nickname} (${profile.host})")
                            // Mock connection log trace
                        },
                        onEdit = { onEditConnection(profile.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun ConnectionItem(profile: ConnectionProfile, onClick: () -> Unit, onEdit: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = profile.nickname, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "${profile.username}@${profile.host}:${profile.port}", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onEdit) {
                Text("Edit")
            }
        }
    }
}