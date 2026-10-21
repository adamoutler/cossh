package com.adamoutler.ssh.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.Protocol

object JumpHostGraphValidator {
    /**
     * Traverses the proxy jump chain starting from [selectedJumpHostId].
     * Returns true if traversing reaches [currentProfileId] or encounters a cycle.
     */
    fun hasJumpHostCycle(
        currentProfileId: String?,
        selectedJumpHostId: String?,
        profiles: Map<String, ConnectionProfile>,
    ): Boolean {
        if (selectedJumpHostId == null) return false
        if (currentProfileId != null && selectedJumpHostId == currentProfileId) return true

        val visited = mutableSetOf<String>()
        if (currentProfileId != null) {
            visited.add(currentProfileId)
        }

        var nextId: String? = selectedJumpHostId
        while (nextId != null) {
            if (!visited.add(nextId)) {
                return true
            }
            val nextProfile = profiles[nextId] ?: break
            nextId = nextProfile.jumpHostProfileId
        }
        return false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JumpHostSelector(
    currentProfileId: String?,
    selectedJumpHostId: String?,
    availableProfiles: List<ConnectionProfile>,
    targetHost: String,
    onJumpHostSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    val profilesMap = remember(availableProfiles) {
        availableProfiles.associateBy { it.id }
    }

    val selectedProfile = remember(selectedJumpHostId, profilesMap) {
        selectedJumpHostId?.let { profilesMap[it] }
    }

    // Filter candidate profiles: must be SSH protocol, and cannot be the current profile itself
    val candidateProfiles = remember(availableProfiles, currentProfileId) {
        availableProfiles.filter { it.protocol == Protocol.SSH && it.id != currentProfileId }
    }

    val selectedText = when {
        selectedProfile != null -> "${selectedProfile.nickname.ifEmpty { selectedProfile.host }} (${selectedProfile.host}:${selectedProfile.port})"
        else -> "Direct Connection (No Jump Host)"
    }

    Column(modifier = modifier.fillMaxWidth()) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth().testTag("JumpHostDropdown"),
        ) {
            OutlinedTextField(
                value = selectedText,
                onValueChange = {},
                readOnly = true,
                label = { Text("Jump Host (ProxyJump)") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Direct Connection (No Jump Host)") },
                    onClick = {
                        onJumpHostSelected(null)
                        expanded = false
                    },
                    modifier = Modifier.testTag("JumpHostOption_None"),
                )

                candidateProfiles.forEach { profile ->
                    val isCycle = JumpHostGraphValidator.hasJumpHostCycle(
                        currentProfileId = currentProfileId,
                        selectedJumpHostId = profile.id,
                        profiles = profilesMap,
                    )
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${profile.nickname.ifEmpty { profile.host }} (${profile.host}:${profile.port})",
                                    color = if (isCycle) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                )
                                if (isCycle) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "(Cycle Detected)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        },
                        enabled = !isCycle,
                        onClick = {
                            if (!isCycle) {
                                onJumpHostSelected(profile.id)
                                expanded = false
                            }
                        },
                        modifier = Modifier.testTag("JumpHostOption_${profile.id}"),
                    )
                }
            }
        }

        if (selectedProfile != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("JumpHostBreadcrumbCard"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = "Encrypted Route",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Tunnel Route:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Device ➔ ${selectedProfile.nickname.ifEmpty { selectedProfile.host }} ➔ ${targetHost.ifEmpty { "Target Host" }}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Traffic to the target host is forwarded via an authenticated direct-tcpip channel on the bastion host.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
