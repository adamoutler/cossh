package com.adamoutler.ssh.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun TelnetInsecureWarning(
    modifier: Modifier = Modifier,
    nickname: String? = null,
    isOverlay: Boolean = false
) {
    val backgroundColor = if (isOverlay) Color(0xFFE65100) else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isOverlay) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val textStyle = if (isOverlay) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleMedium
    val textContent = if (isOverlay) "Telnet: Unencrypted Connection" else "${nickname ?: "Host"} (Telnet: Unencrypted)"
    val verticalPad = if (isOverlay) 8.dp else 12.dp

    Surface(
        color = backgroundColor,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = verticalPad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Insecure Protocol Warning",
                tint = if (isOverlay) Color.White else Color(0xFFE65100),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = textContent,
                color = textColor,
                style = textStyle,
            )
        }
    }
}
