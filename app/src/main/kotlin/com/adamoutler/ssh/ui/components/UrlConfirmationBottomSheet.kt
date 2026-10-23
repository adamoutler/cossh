package com.adamoutler.ssh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.view.WindowManager
import com.adamoutler.ssh.util.SanitizedUrl

/**
 * Material 3 Confirmation Bottom Sheet displayed when a URL is tapped in the terminal.
 *
 * Enforces:
 * - Anti-phishing host domain isolation and emphasis
 * - Warnings for insecure plain HTTP, direct IP addresses, and IDN Punycode homograph domains
 * - FLAG_SECURE window masking while active
 * - TalkBack accessibility labels
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UrlConfirmationBottomSheet(
    sanitizedUrl: SanitizedUrl,
    onOpenBrowser: () -> Unit,
    onCopyLink: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Enforce FLAG_SECURE on the containing window while the sheet is active
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    val colorScheme = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = colorScheme.surfaceContainerLow,
        modifier = modifier.semantics { paneTitle = "External Link Confirmation Dialog" },
    ) {
        UrlConfirmationContent(
            sanitizedUrl = sanitizedUrl,
            onOpenBrowser = onOpenBrowser,
            onCopyLink = onCopyLink,
            onDismiss = onDismiss
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UrlConfirmationContent(
    sanitizedUrl: SanitizedUrl,
    onOpenBrowser: () -> Unit,
    onCopyLink: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    val isAlert = !sanitizedUrl.isSecure || sanitizedUrl.isDirectIp || sanitizedUrl.isPunycode
    val warningColor = when {
        sanitizedUrl.isPunycode -> Color(0xFFEF4444) // Red alert for homographs
        !sanitizedUrl.isSecure -> Color(0xFFEAB308) // Amber warning for cleartext HTTP
        sanitizedUrl.isDirectIp -> Color(0xFFF97316) // Orange warning for direct IP
        else -> colorScheme.primary
    }

    Column(
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .fillMaxWidth()
    ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Outlined.Security,
                    contentDescription = null,
                    tint = warningColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "External Link Confirmation",
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close link confirmation"
                    )
                }
            }

            if (isAlert) {
                val warningText = when {
                    sanitizedUrl.isPunycode -> "Security Alert: Domain uses internationalized lookalike characters (Punycode: ${sanitizedUrl.punycodeHost})"
                    !sanitizedUrl.isSecure && sanitizedUrl.isDirectIp -> "Warning: Insecure plain HTTP transport on direct IP address."
                    !sanitizedUrl.isSecure -> "Warning: Insecure plaintext HTTP transport. Data sent across this connection is unencrypted."
                    sanitizedUrl.isDirectIp -> "Notice: Direct IP address host (No SSL certificate parity)."
                    else -> ""
                }

                Surface(
                    color = warningColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = warningColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = warningText,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurface
                        )
                    }
                }
            }

            OutlinedCard(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = colorScheme.surfaceContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Destination host: ${sanitizedUrl.host}, protocol: ${sanitizedUrl.scheme}."
                    }
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "DESTINATION HOST",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${sanitizedUrl.scheme}://",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = sanitizedUrl.host,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = colorScheme.onSurface
                        )
                        if (sanitizedUrl.port != -1) {
                            Text(
                                text = ":${sanitizedUrl.port}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.tertiary
                            )
                        }
                    }

                    val pathAndQuery = sanitizedUrl.uri.path.orEmpty() +
                        (sanitizedUrl.uri.query?.let { "?$it" } ?: "")
                    if (pathAndQuery.isNotEmpty()) {
                        HorizontalDivider(
                            color = colorScheme.outlineVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        Text(
                            text = "PATH & PARAMETERS",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = pathAndQuery,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onCopyLink,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Copy Link", style = MaterialTheme.typography.labelLarge)
                }

                Button(
                    onClick = onOpenBrowser,
                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.OpenInBrowser,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Open Link", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
}
