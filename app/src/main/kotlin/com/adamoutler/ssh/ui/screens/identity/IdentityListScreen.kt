package com.adamoutler.ssh.ui.screens.identity

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adamoutler.ssh.data.IdentityProfile
import com.adamoutler.ssh.ui.screens.IdentityViewModel

@Composable
fun IdentityListScreen(
    viewModel: IdentityViewModel = viewModel(),
    onAddIdentity: () -> Unit,
    onEditIdentity: (String) -> Unit,
    onBack: () -> Unit,
) {
    val identities by viewModel.identities.collectAsState()

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.loadIdentities()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    IdentityListScreenContent(
        identities = identities,
        onAddIdentity = onAddIdentity,
        onEditIdentity = onEditIdentity,
        onDeleteIdentity = { viewModel.deleteIdentity(it) },
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@com.adamoutler.ssh.ui.screens.Generated
@Composable
fun IdentityListScreenContent(
    identities: List<IdentityProfile>,
    onAddIdentity: () -> Unit,
    onEditIdentity: (String) -> Unit,
    onDeleteIdentity: (String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Identity Manager") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddIdentity) {
                Icon(Icons.Default.Add, contentDescription = "Add Identity")
            }
        },
    ) { padding ->
        if (identities.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("No identities found. Add one to get started.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(identities) { identity ->
                    IdentityItem(
                        identity = identity,
                        onClick = { onEditIdentity(identity.id) },
                        onDelete = { onDeleteIdentity(identity.id) },
                    )
                }
            }
        }
    }
}

@Composable
fun IdentityItem(
    identity: IdentityProfile,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(identity.name, style = MaterialTheme.typography.titleMedium)
                    Text(identity.username, style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}
