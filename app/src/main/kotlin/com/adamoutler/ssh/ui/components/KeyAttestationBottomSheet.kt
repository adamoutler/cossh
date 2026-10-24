package com.adamoutler.ssh.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.adamoutler.ssh.crypto.strongbox.AttestationSecurityLevel
import com.adamoutler.ssh.crypto.strongbox.KeyAttestationRecord
import java.util.Base64

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyAttestationBottomSheet(
    attestationRecord: KeyAttestationRecord?,
    securityTier: String?,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        KeyAttestationContent(
            attestationRecord = attestationRecord,
            securityTier = securityTier,
            onDismiss = onDismiss,
        )
    }
}

@Composable
fun KeyAttestationContent(
    attestationRecord: KeyAttestationRecord?,
    securityTier: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
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
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Hardware Key Attestation",
                style = MaterialTheme.typography.titleLarge,
            )
        }

        // Security tier badge
        SecurityTierBadge(securityTier = securityTier, attestationRecord = attestationRecord)

        HorizontalDivider()

        if (attestationRecord != null) {
            AttestationRow(
                label = "Attestation Level",
                value = formatSecurityLevel(attestationRecord.attestationSecurityLevel),
            )
            AttestationRow(
                label = "Keymaster Level",
                value = formatSecurityLevel(attestationRecord.keymasterSecurityLevel),
            )
            AttestationRow(
                label = "Attestation Version",
                value = "v${attestationRecord.attestationVersion}",
            )
            AttestationRow(
                label = "Keymaster Version",
                value = "v${attestationRecord.keymasterVersion}",
            )

            if (attestationRecord.attestationChallenge.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Attestation Challenge (SHA-256 / Base64)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Text(
                        text = Base64.getEncoder().encodeToString(attestationRecord.attestationChallenge),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }

            if (attestationRecord.uniqueId != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Device Unique ID",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Text(
                        text = Base64.getEncoder().encodeToString(attestationRecord.uniqueId),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.Info, contentDescription = null)
                    Text(
                        text = "No cryptographic attestation certificate chain was attached to this key. It is protected by local keystore or software encryption.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Close")
        }
    }
}

@Composable
fun SecurityTierBadge(
    securityTier: String?,
    modifier: Modifier = Modifier,
    attestationRecord: KeyAttestationRecord? = null,
) {
    val tier = when {
        attestationRecord?.isStrongBox == true || securityTier == "STRONGBOX" -> "StrongBox Hardware-Backed 🛡️💎"
        attestationRecord?.isHardwareBacked == true || securityTier == "TEE" -> "TEE Keystore 🔒"
        else -> "Imported Software Key ⚠️"
    }

    val (containerColor, contentColor) = when {
        tier.contains("StrongBox") -> Pair(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        tier.contains("TEE") -> Pair(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        else -> Pair(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }

    SuggestionChip(
        onClick = {},
        label = { Text(tier, style = MaterialTheme.typography.labelMedium) },
        icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = containerColor,
            labelColor = contentColor,
            iconContentColor = contentColor,
        ),
        modifier = modifier,
    )
}

@Composable
private fun AttestationRow(
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

private fun formatSecurityLevel(level: AttestationSecurityLevel): String = when (level) {
    AttestationSecurityLevel.STRONGBOX -> "StrongBox (Dedicated Security Chip)"
    AttestationSecurityLevel.TRUSTED_ENVIRONMENT -> "TEE (Trusted Execution Environment)"
    AttestationSecurityLevel.SOFTWARE -> "Software Emulated"
    AttestationSecurityLevel.UNKNOWN -> "Unknown"
}
