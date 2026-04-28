package com.adamoutler.ssh.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.IdentityProfile
import com.adamoutler.ssh.data.PortForwardConfig
import com.adamoutler.ssh.data.PortForwardType
import com.adamoutler.ssh.data.Protocol

@Composable
fun AddEditProfileScreen(
    profileId: String? = null,
    viewModel: AddEditProfileViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onManageIdentities: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    var availableKeys by remember { mutableStateOf(viewModel.getAvailableKeys()) }
    var identities by remember { mutableStateOf(viewModel.getIdentities()) }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                availableKeys = viewModel.getAvailableKeys()
                identities = viewModel.getIdentities()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(profileId) {
        viewModel.loadProfileIfNeeded(profileId)
    }

    AddEditProfileScreenContent(
        profileId = profileId,
        nickname = uiState.nickname,
        onNicknameChange = { newName -> viewModel.updateState { it.copy(nickname = newName) } },
        host = uiState.host,
        onHostChange = { newHost -> viewModel.updateState { it.copy(host = newHost) } },
        port = uiState.port,
        onPortChange = { newPort -> viewModel.updateState { it.copy(port = newPort) } },
        protocol = uiState.protocol,
        onProtocolChange = { newProto -> 
            viewModel.updateState { 
                val newPort = if (newProto == Protocol.TELNET && it.port == "22") "23" else if (newProto == Protocol.SSH && it.port == "23") "22" else it.port
                it.copy(protocol = newProto, port = newPort, authType = if (newProto == Protocol.TELNET) AuthType.PASSWORD else it.authType) 
            } 
        },
        username = uiState.username,
        onUsernameChange = { newUsername -> viewModel.updateState { it.copy(username = newUsername) } },
        password = uiState.password,
        onPasswordChange = { newPass -> viewModel.updateState { it.copy(password = newPass) } },
        isPasswordLocked = uiState.isPasswordLocked,
        onPasswordLockedChange = { locked -> viewModel.updateState { it.copy(isPasswordLocked = locked) } },
        authType = uiState.authType,
        onAuthTypeChange = { newAuth -> viewModel.updateState { it.copy(authType = newAuth) } },
        availableKeys = availableKeys,
        keyReference = uiState.keyReference,
        onKeyReferenceChange = { newKey -> viewModel.updateState { it.copy(keyReference = newKey) } },
        identities = identities,
        identityId = uiState.identityId,
        onIdentityChange = { newId -> viewModel.updateState { it.copy(identityId = newId) } },
        onManageIdentities = onManageIdentities,
        envVarsText = uiState.envVarsText,
        onEnvVarsTextChange = { newEnv -> viewModel.updateState { it.copy(envVarsText = newEnv) } },
        portForwards = uiState.portForwards,
        onPortForwardsChange = { newPF -> viewModel.updateState { it.copy(portForwards = newPF) } },
        onSave = {
            val selectedIdent = identities.find { it.id == uiState.identityId }
            val finalUsername = if (selectedIdent != null && uiState.protocol == Protocol.SSH) selectedIdent.username else uiState.username
            val finalAuthType = if (selectedIdent != null && uiState.protocol == Protocol.SSH) selectedIdent.authType else uiState.authType
            
            val passBytes = if ((uiState.identityId == null || uiState.protocol == Protocol.TELNET) && finalAuthType == AuthType.PASSWORD) {
                if (uiState.isPasswordLocked) {
                    uiState.originalPassword
                } else if (uiState.password.isNotEmpty()) {
                    uiState.password.toByteArray(Charsets.UTF_8)
                } else null
            } else null
            val keyRef = if ((uiState.identityId == null || uiState.protocol == Protocol.TELNET) && finalAuthType == AuthType.KEY) uiState.keyReference else null

            val parsedEnvVars = uiState.envVarsText.split(",")
                .map { it.trim() }
                .filter { it.contains("=") }
                .associate { 
                    val parts = it.split("=", limit = 2)
                    parts[0] to parts[1]
                }
            
            viewModel.saveProfile(
                id = profileId,
                nickname = uiState.nickname,
                host = uiState.host,
                port = uiState.port,
                protocol = uiState.protocol,
                username = finalUsername,
                authType = finalAuthType,
                password = passBytes,
                keyReference = keyRef,
                identityId = if (uiState.protocol == Protocol.SSH) uiState.identityId else null,
                envVars = parsedEnvVars,
                portForwards = uiState.portForwards
            )
            onNavigateBack()
        },
        onNavigateBack = {
            viewModel.resetState()
            onNavigateBack()
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditProfileScreenContent(
    profileId: String?,
    nickname: String,
    onNicknameChange: (String) -> Unit,
    host: String,
    onHostChange: (String) -> Unit,
    port: String,
    onPortChange: (String) -> Unit,
    protocol: Protocol,
    onProtocolChange: (Protocol) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    isPasswordLocked: Boolean,
    onPasswordLockedChange: (Boolean) -> Unit,
    authType: AuthType,
    onAuthTypeChange: (AuthType) -> Unit,
    availableKeys: List<String>,
    keyReference: String,
    onKeyReferenceChange: (String) -> Unit,
    identities: List<IdentityProfile>,
    identityId: String?,
    onIdentityChange: (String?) -> Unit,
    onManageIdentities: () -> Unit,
    envVarsText: String,
    onEnvVarsTextChange: (String) -> Unit,
    portForwards: List<PortForwardConfig>,
    onPortForwardsChange: (List<PortForwardConfig>) -> Unit,
    onSave: () -> Unit,
    onNavigateBack: () -> Unit,
    defaultPasswordVisible: Boolean = false,
    defaultShowAddDialog: Boolean = false
) {
    var isKeyDropdownExpanded by remember { mutableStateOf(false) }
    var isIdentityDropdownExpanded by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(defaultPasswordVisible) }

    val selectedIdentity = identities.find { it.id == identityId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (profileId == null) "Add Connection" else "Edit Connection") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onSave) {
                        Icon(Icons.Filled.Check, contentDescription = "Save Profile")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = protocol == Protocol.SSH,
                    onClick = { onProtocolChange(Protocol.SSH) },
                    label = { Text("SSH") },
                    modifier = Modifier.testTag("ProtocolSSH")
                )
                FilterChip(
                    selected = protocol == Protocol.TELNET,
                    onClick = { onProtocolChange(Protocol.TELNET) },
                    label = { Text("Telnet") },
                    modifier = Modifier.testTag("ProtocolTelnet")
                )
            }

            if (protocol == Protocol.TELNET) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = "Warning", tint = Color(0xFFE65100))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "⚠️ **Warning: Insecure Protocol.** Telnet transmits all data, including passwords, in cleartext. Use only on trusted local networks.",
                            color = Color(0xFFE65100),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = nickname,
                onValueChange = onNicknameChange,
                label = { Text("Nickname") },
                modifier = Modifier.fillMaxWidth().testTag("NicknameInput")
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = host,
                onValueChange = onHostChange,
                label = { Text("Host (IP or Domain)") },
                modifier = Modifier.fillMaxWidth().testTag("HostInput")
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = port,
                onValueChange = onPortChange,
                label = { Text("Port") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag("PortInput")
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text("Authentication", style = MaterialTheme.typography.titleMedium)

            if (protocol == Protocol.SSH) {
                ExposedDropdownMenuBox(
                    expanded = isIdentityDropdownExpanded,
                    onExpandedChange = { isIdentityDropdownExpanded = !isIdentityDropdownExpanded },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    OutlinedTextField(
                        value = selectedIdentity?.name ?: "None (Use Inline Credentials)",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Use Identity") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isIdentityDropdownExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth().testTag("IdentityDropdown")
                    )
                    ExposedDropdownMenu(
                        expanded = isIdentityDropdownExpanded,
                        onDismissRequest = { isIdentityDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("None (Use Inline Credentials)") },
                            onClick = {
                                onIdentityChange(null)
                                isIdentityDropdownExpanded = false
                            }
                        )
                        identities.forEach { identity ->
                            DropdownMenuItem(
                                text = { Text(identity.name) },
                                onClick = {
                                    onIdentityChange(identity.id)
                                    isIdentityDropdownExpanded = false
                                }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Manage Identities...") },
                            onClick = {
                                onManageIdentities()
                                isIdentityDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            if (identityId == null || protocol == Protocol.TELNET) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth().testTag("UsernameInput")
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (protocol == Protocol.SSH) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = authType == AuthType.PASSWORD,
                            onClick = { onAuthTypeChange(AuthType.PASSWORD) },
                            label = { Text("Password") },
                            modifier = Modifier.testTag("AuthTypePassword")
                        )
                        FilterChip(
                            selected = authType == AuthType.KEY,
                            onClick = { onAuthTypeChange(AuthType.KEY) },
                            label = { Text("SSH Key") },
                            modifier = Modifier.testTag("AuthTypeKey")
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (authType == AuthType.PASSWORD || protocol == Protocol.TELNET) {
                    if (isPasswordLocked) {
                        OutlinedTextField(
                            value = "••••••••",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Password") },
                            trailingIcon = {
                                IconButton(onClick = { 
                                    onPasswordLockedChange(false)
                                    onPasswordChange("")
                                }) {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = "Edit Password",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("PasswordInputLocked")
                        )
                    } else {
                        OutlinedTextField(
                            value = password,
                            onValueChange = onPasswordChange,
                            label = { Text("Password") },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("PasswordInput")
                        )
                    }
                } else if (protocol == Protocol.SSH) {
                    ExposedDropdownMenuBox(
                        expanded = isKeyDropdownExpanded,
                        onExpandedChange = { isKeyDropdownExpanded = !isKeyDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = keyReference.ifEmpty { "Select SSH Key" },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("SSH Key") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isKeyDropdownExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth().testTag("KeyDropdown")
                        )
                        ExposedDropdownMenu(
                            expanded = isKeyDropdownExpanded,
                            onDismissRequest = { isKeyDropdownExpanded = false }
                        ) {
                            if (availableKeys.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No keys available") },
                                    onClick = {
                                        isKeyDropdownExpanded = false
                                    },
                                    enabled = false
                                )
                            } else {
                                availableKeys.forEach { key ->
                                    DropdownMenuItem(
                                        text = { Text(key) },
                                        onClick = {
                                            onKeyReferenceChange(key)
                                            isKeyDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.testTag("SelectedIdentityCard")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Using credentials from identity:", style = MaterialTheme.typography.labelSmall)
                        Text(selectedIdentity?.name ?: "", style = MaterialTheme.typography.titleMedium)
                        Text("Username: ${selectedIdentity?.username}", style = MaterialTheme.typography.bodySmall)
                        val authMethodDisplay = when {
                            selectedIdentity?.authType == AuthType.KEY && selectedIdentity.password != null -> "Key & Password"
                            else -> selectedIdentity?.authType?.name ?: "Unknown"
                        }
                        Text("Auth Method: $authMethodDisplay", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text("Advanced Configuration", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = envVarsText,
                onValueChange = onEnvVarsTextChange,
                label = { Text("Environment Variables (VAR=val,...)") },
                modifier = Modifier.fillMaxWidth().testTag("EnvVarsInput")
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Port Forwarding", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            
            portForwards.forEach { pf ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (pf.type == PortForwardType.LOCAL) "Local Forward" else "Remote Forward",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "${pf.localPort} ➔ ${pf.remoteHost}:${pf.remotePort}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        IconButton(onClick = {
                            val newList = portForwards.toMutableList()
                            newList.remove(pf)
                            onPortForwardsChange(newList)
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete Port Forward")
                        }
                    }
                }
            }
            
            var showAddDialog by remember { mutableStateOf(defaultShowAddDialog) }
            
            if (showAddDialog) {
                var type by remember { mutableStateOf(PortForwardType.LOCAL) }
                var localPort by remember { mutableStateOf("") }
                var remoteHost by remember { mutableStateOf("") }
                var remotePort by remember { mutableStateOf("") }

                AlertDialog(
                    onDismissRequest = { showAddDialog = false },
                    title = { Text("Add Port Forward") },
                    text = {
                        Column {
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                SegmentedButton(
                                    selected = type == PortForwardType.LOCAL,
                                    onClick = { type = PortForwardType.LOCAL },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                                ) {
                                    Text("Local")
                                }
                                SegmentedButton(
                                    selected = type == PortForwardType.REMOTE,
                                    onClick = { type = PortForwardType.REMOTE },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                                ) {
                                    Text("Remote")
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = localPort,
                                onValueChange = { localPort = it },
                                label = { Text("Local Port") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = remoteHost,
                                onValueChange = { remoteHost = it },
                                label = { Text("Remote Host") },
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = remotePort,
                                onValueChange = { remotePort = it },
                                label = { Text("Remote Port") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val lp = localPort.toIntOrNull()
                            val rp = remotePort.toIntOrNull()
                            if (lp != null && rp != null && remoteHost.isNotBlank()) {
                                val newList = portForwards.toMutableList()
                                newList.add(PortForwardConfig(type, lp, remoteHost, rp))
                                onPortForwardsChange(newList)
                                showAddDialog = false
                            }
                        }) {
                            Text("Add")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Port Forward")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Port Forward")
            }
        }
    }
}
