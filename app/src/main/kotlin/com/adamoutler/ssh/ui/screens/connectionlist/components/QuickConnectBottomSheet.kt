package com.adamoutler.ssh.ui.screens.connectionlist.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.EphemeralProfileStore
import com.adamoutler.ssh.data.IdentityProfile
import com.adamoutler.ssh.data.Protocol
import com.adamoutler.ssh.util.QuickConnectParser
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickConnectBottomSheet(
    identities: List<IdentityProfile> = emptyList(),
    onConnect: (profile: ConnectionProfile, isEphemeral: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("quick_connect_sheet"),
    ) {
        QuickConnectContent(
            identities = identities,
            onConnect = onConnect,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickConnectContent(
    identities: List<IdentityProfile> = emptyList(),
    initialAddress: String = "",
    initialProtocol: Protocol = Protocol.SSH,
    initialPort: String = "22",
    initialErrorMessage: String? = null,
    onConnect: (profile: ConnectionProfile, isEphemeral: Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    var addressInput by remember { mutableStateOf(initialAddress) }
    var selectedProtocol by remember { mutableStateOf(initialProtocol) }
    var portInput by remember { mutableStateOf(initialPort) }
    var selectedAuthType by remember { mutableStateOf(AuthType.PASSWORD) }
    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var selectedIdentityId by remember { mutableStateOf(identities.firstOrNull()?.id) }
    var errorMessage by remember { mutableStateOf(initialErrorMessage) }

    fun submit(isEphemeral: Boolean) {
        val parsedResult = QuickConnectParser.parse(addressInput, selectedProtocol)
        if (parsedResult.isFailure) {
            errorMessage = parsedResult.exceptionOrNull()?.message ?: "Invalid address"
            return
        }

        val target = parsedResult.getOrThrow()
        val finalPort = portInput.toIntOrNull() ?: target.port
        if (finalPort !in 1..65535) {
            errorMessage = "Port must be between 1 and 65535"
            return
        }

        val profileId = if (isEphemeral) "ephemeral_${UUID.randomUUID()}" else UUID.randomUUID().toString()
        val displayName = if (target.username.isNotEmpty()) "${target.username}@${target.host}" else target.host

        val rawPasswordBytes = if (selectedAuthType == AuthType.PASSWORD && passwordInput.isNotEmpty()) {
            val bytes = passwordInput.toByteArray(Charsets.UTF_8)
            bytes
        } else {
            null
        }

        val profile = ConnectionProfile(
            id = profileId,
            nickname = displayName,
            host = target.host,
            port = finalPort,
            protocol = target.protocol,
            username = target.username,
            authType = selectedAuthType,
            identityId = if (selectedAuthType == AuthType.KEY) selectedIdentityId else null,
            password = rawPasswordBytes,
            isEphemeral = isEphemeral,
        )

        if (isEphemeral) {
            EphemeralProfileStore.put(profile)
        }

        onConnect(profile, isEphemeral)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Quick Connect",
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            Text(
                text = "Connect directly to a remote host without permanent storage.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Protocol selector
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedProtocol == Protocol.SSH,
                    onClick = {
                        selectedProtocol = Protocol.SSH
                        if (portInput == "23") portInput = "22"
                    },
                    label = { Text("SSH (22)") },
                )
                FilterChip(
                    selected = selectedProtocol == Protocol.TELNET,
                    onClick = {
                        selectedProtocol = Protocol.TELNET
                        if (portInput == "22") portInput = "23"
                    },
                    label = { Text("Telnet (23)") },
                )
            }

            // Target address field
            OutlinedTextField(
                value = addressInput,
                onValueChange = {
                    addressInput = it
                    errorMessage = null
                },
                label = { Text("Target Address") },
                placeholder = { Text("user@hostname or 192.168.1.1") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("quick_connect_address")
                    .semantics {
                        contentDescription = "Target address input field"
                    },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(
                    onGo = { submit(isEphemeral = true) },
                ),
                isError = errorMessage != null,
            )

            // Port field
            OutlinedTextField(
                value = portInput,
                onValueChange = { portInput = it },
                label = { Text("Port") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("quick_connect_port")
                    .semantics {
                        contentDescription = "Port input field"
                    },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
            )

            if (selectedProtocol == Protocol.SSH) {
                // Auth Type selector
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedAuthType == AuthType.PASSWORD,
                        onClick = { selectedAuthType = AuthType.PASSWORD },
                        label = { Text("Password") },
                    )
                    FilterChip(
                        selected = selectedAuthType == AuthType.KEY,
                        onClick = { selectedAuthType = AuthType.KEY },
                        label = { Text("Identity Key") },
                    )
                }

                if (selectedAuthType == AuthType.PASSWORD) {
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Password (optional)") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quick_connect_password")
                            .semantics {
                                contentDescription = "Password input field"
                            },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                    )
                } else if (identities.isNotEmpty()) {
                    var expanded by remember { mutableStateOf(false) }
                    val currentIdentity = identities.find { it.id == selectedIdentityId } ?: identities.first()

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                    ) {
                        OutlinedTextField(
                            value = currentIdentity.name,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select Identity") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            identities.forEach { identity ->
                                DropdownMenuItem(
                                    text = { Text(identity.name) },
                                    onClick = {
                                        selectedIdentityId = identity.id
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Assertive
                    },
                )
            }

            // Dual action buttons: Connect (Ephemeral) vs Save & Connect (Permanent)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { submit(isEphemeral = false) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_connect_save_button"),
                ) {
                    Text("Save & Connect")
                }
                Button(
                    onClick = { submit(isEphemeral = true) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_connect_button"),
                ) {
                    Text("Connect")
                }
            }
        }
}
