package com.adamoutler.ssh.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.adamoutler.ssh.crypto.certificate.OpenSshCertType
import com.adamoutler.ssh.crypto.certificate.OpenSshCertificate
import com.adamoutler.ssh.crypto.certificate.OpenSshCertificateValidator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SshCertificateBottomSheet(
    certificate: OpenSshCertificate,
    onDismiss: () -> Unit,
) {
    val currentEpoch = System.currentTimeMillis() / 1000L
    val isNotYetValid = currentEpoch < certificate.validAfterSeconds
    val isExpired = certificate.validBeforeSeconds != -1L &&
        java.lang.Long.compareUnsigned(currentEpoch, certificate.validBeforeSeconds) > 0
    val isValid = !isNotYetValid && !isExpired

    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)
    val validAfterStr = dateFormat.format(Date(certificate.validAfterSeconds * 1000L))
    val validBeforeStr = if (certificate.validBeforeSeconds == -1L || certificate.validBeforeSeconds == Long.MAX_VALUE) {
        "Forever (Infinity)"
    } else {
        dateFormat.format(Date(certificate.validBeforeSeconds * 1000L))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.CardMembership,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column {
                    Text(
                        text = if (certificate.certType == OpenSshCertType.HOST) "OpenSSH Host Certificate" else "OpenSSH User Certificate",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = certificate.keyId.ifEmpty { "(No Key ID)" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            // Validity Chip
            SuggestionChip(
                onClick = {},
                label = {
                    Text(
                        text = when {
                            isValid -> "Valid & Active Certificate"
                            isNotYetValid -> "Not Yet Active"
                            else -> "Expired Certificate"
                        },
                    )
                },
                icon = {
                    Icon(
                        imageVector = if (isValid) Icons.Default.Check else Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = if (isValid) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    labelColor = if (isValid) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                    iconContentColor = if (isValid) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                ),
            )

            HorizontalDivider()

            CertificateDetailRow(label = "Serial Number", value = "${certificate.serial}")
            CertificateDetailRow(
                label = "Valid Principals",
                value = if (certificate.validPrincipals.isEmpty()) {
                    if (certificate.certType == OpenSshCertType.HOST) "Any Host (*)" else "None Specified"
                } else {
                    certificate.validPrincipals.joinToString(", ")
                },
            )
            CertificateDetailRow(label = "Valid After", value = validAfterStr)
            CertificateDetailRow(label = "Valid Before", value = validBeforeStr)

            val caFingerprint = OpenSshCertificateValidator.computeFingerprint(certificate.caPublicKey)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Signing CA Fingerprint (${certificate.signatureAlgorithm})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    text = caFingerprint,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }

            if (certificate.criticalOptions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Critical Options",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    for ((opt, value) in certificate.criticalOptions) {
                        Text(
                            text = "$opt = $value",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        )
                    }
                }
            }

            if (certificate.extensions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Extensions",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Text(
                        text = certificate.extensions.keys.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Dismiss")
            }
        }
    }
}

@Composable
private fun CertificateDetailRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
