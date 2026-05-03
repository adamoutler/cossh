package com.adamoutler.ssh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.Protocol

@Composable
fun TerminalOverlayButtons(
    onBackground: () -> Unit,
    onTerminate: () -> Unit,
    profile: ConnectionProfile?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (profile?.protocol == Protocol.TELNET) {
            TelnetInsecureWarning(nickname = profile.nickname, isOverlay = true)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = onBackground,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), shape = CircleShape),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Background Session",
                    tint = Color.White,
                )
            }

            IconButton(
                onClick = onTerminate,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), shape = CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Terminate Session",
                    tint = Color.White,
                )
            }
        }
    }
}
