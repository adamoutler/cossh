package com.adamoutler.ssh.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.adamoutler.ssh.data.KnockProtocol
import com.adamoutler.ssh.data.PortKnockStep
import com.adamoutler.ssh.data.PortKnockingConfig
import com.adamoutler.ssh.data.WolConfig
import com.adamoutler.ssh.network.WolManager

@Composable
fun PreConnectActionsCard(
    wolConfig: WolConfig?,
    onWolConfigChange: (WolConfig?) -> Unit,
    portKnockingConfig: PortKnockingConfig?,
    onPortKnockingConfigChange: (PortKnockingConfig?) -> Unit,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }

    val wolEnabled = wolConfig != null
    val knockEnabled = portKnockingConfig != null

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("PreConnectActionsCard"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .semantics {
                        contentDescription = if (isExpanded) {
                            "Collapse Pre-Connect Actions"
                        } else {
                            "Expand Pre-Connect Actions"
                        }
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pre-Connect Actions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = when {
                            wolEnabled && knockEnabled -> "Wake-on-LAN and Port Knocking enabled"
                            wolEnabled -> "Wake-on-LAN enabled (${wolConfig?.macAddress})"
                            knockEnabled -> "Port Knocking enabled (${portKnockingConfig?.steps?.size ?: 0} steps)"
                            else -> "Wake-on-LAN & Port Knocking (Disabled)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    // --- Wake-on-LAN Section ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Wake-on-LAN (WOL)",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Broadcast magic packet before establishing connection",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = wolEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    onWolConfigChange(WolConfig(macAddress = ""))
                                } else {
                                    onWolConfigChange(null)
                                }
                            },
                            modifier = Modifier.semantics {
                                contentDescription = "Enable Wake-on-LAN toggle"
                            },
                        )
                    }

                    if (wolEnabled && wolConfig != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val isMacValid = WolManager.isValidMacAddress(wolConfig.macAddress)
                        OutlinedTextField(
                            value = wolConfig.macAddress,
                            onValueChange = { newMac ->
                                onWolConfigChange(wolConfig.copy(macAddress = newMac.trim()))
                            },
                            label = { Text("Target MAC Address") },
                            placeholder = { Text("00:11:22:33:44:55") },
                            isError = wolConfig.macAddress.isNotBlank() && !isMacValid,
                            supportingText = {
                                if (wolConfig.macAddress.isNotBlank() && !isMacValid) {
                                    Text("Invalid MAC format (e.g. 00:11:22:33:44:55)", color = MaterialTheme.colorScheme.error)
                                } else {
                                    Text("Standard 6-octet hardware MAC address")
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("WolMacInput"),
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = wolConfig.broadcastIp,
                                onValueChange = { newIp ->
                                    onWolConfigChange(wolConfig.copy(broadcastIp = newIp.trim()))
                                },
                                label = { Text("Broadcast IP") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = wolConfig.port.toString(),
                                onValueChange = { newPort ->
                                    newPort.toIntOrNull()?.let { p ->
                                        onWolConfigChange(wolConfig.copy(port = p))
                                    }
                                },
                                label = { Text("Port") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.width(90.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    // --- Port Knocking Section ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Port Knocking",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Execute sequence of TCP/UDP probes to open firewall",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = knockEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    onPortKnockingConfigChange(PortKnockingConfig())
                                } else {
                                    onPortKnockingConfigChange(null)
                                }
                            },
                            modifier = Modifier.semantics {
                                contentDescription = "Enable Port Knocking toggle"
                            },
                        )
                    }

                    if (knockEnabled && portKnockingConfig != null) {
                        Spacer(modifier = Modifier.height(8.dp))

                        // Current Steps List
                        portKnockingConfig.steps.forEachIndexed { index, step ->
                            Surface(
                                tonalElevation = 2.dp,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Step #${index + 1}: ${step.protocol} ${step.port}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    IconButton(onClick = {
                                        val updated = portKnockingConfig.steps.toMutableList()
                                        updated.removeAt(index)
                                        onPortKnockingConfigChange(portKnockingConfig.copy(steps = updated))
                                    }) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = "Remove knock step ${index + 1}",
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }

                        // Add Step Controls
                        var newStepPort by remember { mutableStateOf("") }
                        var newStepProto by remember { mutableStateOf(KnockProtocol.TCP) }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = newStepPort,
                                onValueChange = { newStepPort = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Port") },
                                placeholder = { Text("7000") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )

                            FilterChip(
                                selected = newStepProto == KnockProtocol.TCP,
                                onClick = { newStepProto = KnockProtocol.TCP },
                                label = { Text("TCP") },
                            )
                            FilterChip(
                                selected = newStepProto == KnockProtocol.UDP,
                                onClick = { newStepProto = KnockProtocol.UDP },
                                label = { Text("UDP") },
                            )

                            IconButton(
                                onClick = {
                                    val portInt = newStepPort.toIntOrNull()
                                    if (portInt != null && portInt in 1..65535) {
                                        val updated = portKnockingConfig.steps + PortKnockStep(portInt, newStepProto)
                                        onPortKnockingConfigChange(portKnockingConfig.copy(steps = updated))
                                        newStepPort = ""
                                    }
                                },
                                enabled = newStepPort.toIntOrNull() != null,
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Add knock step")
                            }
                        }
                    }
                }
            }
        }
    }
}
