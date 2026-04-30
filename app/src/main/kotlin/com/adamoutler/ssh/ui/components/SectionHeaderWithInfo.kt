package com.adamoutler.ssh.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

@Composable
fun SectionHeaderWithInfo(
    title: String,
    infoUri: String,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        IconButton(
            onClick = { uriHandler.openUri(infoUri) },
            modifier = Modifier.semantics {
                contentDescription = "Learn more about $title. Opens external browser."
                role = Role.Button
            }
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null, // Handled by semantics above
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}