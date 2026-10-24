package com.adamoutler.ssh.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adamoutler.ssh.network.ConnectionState

/**
 * Encapsulates the UI state of an individual session tab in the terminal tab bar.
 */
data class SessionTabItem(
    val sessionId: String,
    val profileId: String,
    val nickname: String,
    val isTelnet: Boolean,
    val connectionState: ConnectionState,
    val isSelected: Boolean,
)

/**
 * Compact Material 3 horizontal tab strip for multi-session switching in TerminalScreen.
 *
 * Enforces:
 * - Auto-hide when session count <= 1
 * - Clear protocol visual differentiation (Cobalt Terminal for SSH, Amber Warning for Telnet)
 * - Real-time connection status dot indicator
 * - Individual tab close action and new session connect launcher
 * - TalkBack accessibility semantics
 */
@Composable
fun TerminalSessionTabBar(
    sessions: List<SessionTabItem>,
    onSelectSession: (String) -> Unit,
    onCloseSession: (String) -> Unit,
    onNewSession: () -> Unit,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = sessions.size > 1,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        val selectedIndex = sessions.indexOfFirst { it.isSelected }.coerceAtLeast(0)
        val colorScheme = MaterialTheme.colorScheme

        Surface(
            color = backgroundColor,
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .semantics { contentDescription = "Terminal Session Tabs" },
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedIndex,
                edgePadding = 8.dp,
                containerColor = backgroundColor,
                indicator = {},
                divider = {},
            ) {
                sessions.forEach { session ->
                    val statusColor = when (session.connectionState) {
                        is ConnectionState.Connected -> Color(0xFF22C55E)

                        // Emerald Green
                        is ConnectionState.Connecting,
                        is ConnectionState.Reconnecting,
                        -> Color(0xFFEAB308)

                        // Amber
                        is ConnectionState.Error,
                        is ConnectionState.Disconnected,
                        is ConnectionState.Disconnecting,
                        is ConnectionState.Terminated,
                        -> Color(0xFFEF4444) // Crimson Red
                    }

                    val statusText = when (session.connectionState) {
                        is ConnectionState.Connected -> "Connected"
                        is ConnectionState.Connecting -> "Connecting"
                        is ConnectionState.Reconnecting -> "Reconnecting"
                        is ConnectionState.Error -> "Error"
                        else -> "Disconnected"
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (session.isSelected) colorScheme.surfaceContainerHigh else Color.Transparent,
                        border = if (session.isSelected) BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.6f)) else null,
                        modifier = Modifier
                            .padding(vertical = 3.dp, horizontal = 2.dp)
                            .clickable { onSelectSession(session.sessionId) }
                            .semantics {
                                role = Role.Tab
                                selected = session.isSelected
                                contentDescription = "Session ${session.nickname}, $statusText, ${if (session.isTelnet) "Telnet" else "SSH"}. Double tap to activate tab."
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (session.isTelnet) {
                                Icon(
                                    imageVector = Icons.Outlined.WarningAmber,
                                    contentDescription = "Insecure Telnet Protocol",
                                    tint = Color(0xFFEAB308),
                                    modifier = Modifier.size(13.dp),
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Terminal,
                                    contentDescription = "Encrypted SSH Protocol",
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(13.dp),
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Text(
                                text = session.nickname,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (session.isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 110.dp),
                                color = if (session.isSelected) colorScheme.onSurface else colorScheme.onSurfaceVariant,
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(color = statusColor, shape = CircleShape),
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            IconButton(
                                onClick = { onCloseSession(session.sessionId) },
                                modifier = Modifier
                                    .size(18.dp)
                                    .semantics {
                                        contentDescription = "Disconnect and close session ${session.nickname}"
                                    },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(11.dp),
                                    tint = colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // Add Session / Connect Button
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .padding(vertical = 3.dp, horizontal = 4.dp)
                        .clickable { onNewSession() }
                        .semantics {
                            role = Role.Button
                            contentDescription = "Open new connection session"
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Connect",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
