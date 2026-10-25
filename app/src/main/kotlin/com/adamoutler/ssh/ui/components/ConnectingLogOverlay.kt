package com.adamoutler.ssh.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamoutler.ssh.network.ConnectionLogEntry
import com.adamoutler.ssh.network.ConnectionLogLevel
import com.adamoutler.ssh.network.ConnectionMilestone

@Composable
fun ConnectingLogOverlay(
    profileNickname: String,
    host: String,
    port: Int,
    logs: List<ConnectionLogEntry>,
    onCopyDiagnostics: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val view = LocalView.current

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 280.dp, max = 460.dp)
                .testTag("ConnectingLogOverlayCard"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F19)),
            border = BorderStroke(1.dp, Color(0xFF1E293B)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp)
                                .testTag("ConnectingProgressIndicator"),
                            strokeWidth = 2.5.dp,
                            color = Color(0xFF90C2F9),
                        )
                        Column {
                            Text(
                                text = profileNickname,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Text(
                                text = "$host:$port • Establishing secure shell...",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8),
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            val transcript = logs.joinToString(separator = "\n") { it.toFormattedLine() }
                            onCopyDiagnostics(transcript)
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("CopyDiagnosticsButton")
                            .semantics { contentDescription = "Copy Connection Diagnostics" },
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Diagnostics",
                            tint = Color(0xFF90C2F9),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))

                // Streaming Monospace Log Window
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF030712), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF111827), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    if (logs.isEmpty()) {
                        Text(
                            text = "Initializing connection subsystem...",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF64748B),
                            modifier = Modifier.align(Alignment.Center),
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("ConnectingLogsLazyColumn"),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(logs, key = { it.id }) { entry ->
                                LogStreamEntryItem(entry = entry)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LogStreamEntryItem(entry: ConnectionLogEntry) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = entry.formattedTimestamp(),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = Color(0xFF64748B),
            modifier = Modifier.padding(top = 1.dp),
        )

        MilestoneBadge(milestone = entry.milestone)

        val messageColor = when (entry.level) {
            ConnectionLogLevel.ERROR -> Color(0xFFFCA5A5)
            ConnectionLogLevel.WARN -> Color(0xFFFDE68A)
            ConnectionLogLevel.DEBUG -> Color(0xFF94A3B8)
            ConnectionLogLevel.INFO -> Color(0xFFE2E8F0)
        }

        Text(
            text = entry.message,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = messageColor,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun MilestoneBadge(milestone: ConnectionMilestone) {
    val (badgeBg, badgeText, badgeBorder) = when (milestone) {
        ConnectionMilestone.DNS -> Triple(Color(0x1A38BDF8), Color(0xFF38BDF8), Color(0x3338BDF8))
        ConnectionMilestone.TCP -> Triple(Color(0x1A60A5FA), Color(0xFF60A5FA), Color(0x3360A5FA))
        ConnectionMilestone.SSH -> Triple(Color(0x1A818CF8), Color(0xFF818CF8), Color(0x33818CF8))
        ConnectionMilestone.KEX -> Triple(Color(0x1AA78BFA), Color(0xFFA78BFA), Color(0x33A78BFA))
        ConnectionMilestone.HOSTKEY -> Triple(Color(0x1A22D3EE), Color(0xFF22D3EE), Color(0x3322D3EE))
        ConnectionMilestone.AUTH -> Triple(Color(0x1AC084FC), Color(0xFFC084FC), Color(0x33C084FC))
        ConnectionMilestone.MFA -> Triple(Color(0x1AFBBF24), Color(0xFFFBBF24), Color(0x33FBBF24))
        ConnectionMilestone.CHANNEL -> Triple(Color(0x1A2DD4BF), Color(0xFF2DD4BF), Color(0x332DD4BF))
        ConnectionMilestone.READY -> Triple(Color(0x1A34D399), Color(0xFF34D399), Color(0x3334D399))
        ConnectionMilestone.ERROR -> Triple(Color(0x1AF87171), Color(0xFFF87171), Color(0x33F87171))
    }

    Box(
        modifier = Modifier
            .background(badgeBg, RoundedCornerShape(3.dp))
            .border(0.5.dp, badgeBorder, RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(
            text = milestone.tag,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = badgeText,
        )
    }
}
