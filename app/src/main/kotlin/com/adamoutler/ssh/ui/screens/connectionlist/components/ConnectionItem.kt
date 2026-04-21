package com.adamoutler.ssh.ui.screens.connectionlist.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.adamoutler.ssh.data.ConnectionProfile

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConnectionItem(
    profile: ConnectionProfile,
    isActive: Boolean = false,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    elevation: Dp = 2.dp
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("ConnectionItem_${profile.id}")
            .combinedClickable(
                onClick = onClick,
                onLongClick = onEdit
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = profile.nickname, style = MaterialTheme.typography.titleMedium)
                    if (isActive) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Badge { Text("1") }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "${profile.username}@${profile.host}:${profile.port}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
