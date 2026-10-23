package com.adamoutler.ssh.ui.screens.connectionlist.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

enum class ConflictResolutionStrategy {
    SKIP_EXISTING,
    RENAME_WITH_SUFFIX,
    OVERWRITE,
}

@Composable
fun OpenSshConflictDialog(
    conflictingNames: List<String>,
    onResolve: (ConflictResolutionStrategy) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedStrategy by remember { mutableStateOf(ConflictResolutionStrategy.RENAME_WITH_SUFFIX) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("openssh_conflict_dialog"),
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
            )
        },
        title = {
            Text("Host Conflicts Detected")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${conflictingNames.size} host(s) conflict with existing profiles:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = conflictingNames.take(4).joinToString(", ") + if (conflictingNames.size > 4) "..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedStrategy == ConflictResolutionStrategy.RENAME_WITH_SUFFIX,
                            onClick = { selectedStrategy = ConflictResolutionStrategy.RENAME_WITH_SUFFIX },
                        )
                        Text("Rename with Suffix (e.g. ' (Imported)')", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedStrategy == ConflictResolutionStrategy.SKIP_EXISTING,
                            onClick = { selectedStrategy = ConflictResolutionStrategy.SKIP_EXISTING },
                        )
                        Text("Skip Existing", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedStrategy == ConflictResolutionStrategy.OVERWRITE,
                            onClick = { selectedStrategy = ConflictResolutionStrategy.OVERWRITE },
                        )
                        Text("Overwrite Existing", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onResolve(selectedStrategy) },
                modifier = Modifier.testTag("openssh_conflict_confirm"),
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun OpenSshImportSummaryDialog(
    importedCount: Int,
    skippedCount: Int,
    sanitizedDirectives: List<String>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("openssh_summary_dialog"),
        icon = {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text("OpenSSH Config Imported")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Successfully imported $importedCount profile(s).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (skippedCount > 0) {
                    Text(
                        text = "Skipped $skippedCount wildcard/duplicate entry(s).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (sanitizedDirectives.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Neutralized Directives (${sanitizedDirectives.size}):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    LazyColumn(
                        modifier = Modifier
                            .heightIn(max = 120.dp)
                            .fillMaxWidth(),
                    ) {
                        items(sanitizedDirectives) { directive ->
                            Text(
                                text = "• $directive",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("openssh_summary_ok"),
            ) {
                Text("OK")
            }
        },
    )
}
