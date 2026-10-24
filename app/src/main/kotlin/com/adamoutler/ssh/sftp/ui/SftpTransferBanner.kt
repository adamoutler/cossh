package com.adamoutler.ssh.sftp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adamoutler.ssh.sftp.model.SftpTransferState
import com.adamoutler.ssh.sftp.transfer.SftpTransferItem

@Composable
fun SftpTransferBanner(
    transfer: SftpTransferItem?,
    onCancel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = transfer != null,
        modifier = modifier,
    ) {
        if (transfer != null) {
            Surface(
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("SftpTransferBanner"),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val state = transfer.state
                    val progressFraction = when (state) {
                        is SftpTransferState.Transferring -> (state.percent / 100f).coerceIn(0f, 1f)
                        is SftpTransferState.Completed -> 1f
                        else -> 0f
                    }

                    if (state is SftpTransferState.Transferring) {
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .semantics {
                                    contentDescription = "Transfer progress ${state.percent.toInt()} percent"
                                },
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    } else if (state is SftpTransferState.Idle) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = (if (transfer.isUpload) "Uploading: " else "Downloading: ") + transfer.filename,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )

                            val subtitleText = when (state) {
                                is SftpTransferState.Transferring -> {
                                    val speedKb = state.speedBytesPerSec / 1024.0
                                    val speedStr = if (speedKb >= 1024) "%.1f MB/s".format(speedKb / 1024.0) else "%.0f KB/s".format(speedKb)
                                    val percentStr = "%.0f%%".format(state.percent)
                                    "$speedStr • $percentStr"
                                }
                                is SftpTransferState.Completed -> "Transfer complete"
                                is SftpTransferState.Failed -> "Transfer failed: ${state.throwable.message}"
                                SftpTransferState.Idle -> "Preparing transfer..."
                            }

                            Text(
                                text = subtitleText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        IconButton(
                            onClick = { onCancel(transfer.id) },
                            modifier = Modifier
                                .semantics {
                                    contentDescription = "Cancel transfer for ${transfer.filename}"
                                }
                                .testTag("CancelTransferButton"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Cancel",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}
