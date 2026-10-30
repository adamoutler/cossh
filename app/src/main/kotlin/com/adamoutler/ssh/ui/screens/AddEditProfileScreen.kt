package com.adamoutler.ssh.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
    onManageIdentities: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    var identities by remember { mutableStateOf(viewModel.getIdentities()) }
    var availableProfiles by remember { mutableStateOf(viewModel.getAllProfiles()) }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                identities = viewModel.getIdentities()
                availableProfiles = viewModel.getAllProfiles()
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
        onHostChange = { newHost ->
            viewModel.updateState {
                it.copy(
                    host = newHost,
                    hostError = if (it.hostError != null && viewModel.validateHost(newHost)) null else it.hostError,
                )
            }
        },
        port = uiState.port,
        onPortChange = { newPort ->
            viewModel.updateState {
                it.copy(
                    port = newPort,
                    portError = if (it.portError != null && viewModel.validatePort(newPort)) null else it.portError,
                )
            }
        },
        protocol = uiState.protocol,
        onProtocolChange = { newProto -> viewModel.setProtocol(newProto) },
        username = uiState.username,
        onUsernameChange = { newUsername -> viewModel.updateState { it.copy(username = newUsername) } },
        password = uiState.password,
        onPasswordChange = { newPass -> viewModel.updateState { it.copy(password = newPass) } },
        isPasswordLocked = uiState.isPasswordLocked,
        onPasswordLockedChange = { locked -> viewModel.updateState { it.copy(isPasswordLocked = locked) } },
        authType = uiState.authType,
        onAuthTypeChange = { newAuth ->
            viewModel.updateState {
                it.copy(
                    authType = newAuth,
                    identityId = if (newAuth == AuthType.PASSWORD) null else it.identityId,
                )
            }
        },
        availableKeys = emptyList(),
        keyReference = uiState.keyReference,
        onKeyReferenceChange = { newKey -> viewModel.updateState { it.copy(keyReference = newKey) } },
        identities = identities,
        identityId = uiState.identityId,
        onIdentityChange = { newId ->
            viewModel.updateState {
                it.copy(
                    identityId = newId,
                    authType = if (newId != null) AuthType.KEY else it.authType,
                )
            }
        },
        onManageIdentities = onManageIdentities,
        envVarsText = uiState.envVarsText,
        onEnvVarsTextChange = { newEnv -> viewModel.updateState { it.copy(envVarsText = newEnv) } },
        portForwards = uiState.portForwards,
        onPortForwardsChange = { newPF -> viewModel.updateState { it.copy(portForwards = newPF) } },
        commandSnippets = uiState.commandSnippets,
        onCommandSnippetsChange = { newSnippets -> viewModel.updateState { it.copy(commandSnippets = newSnippets) } },
        initialDirectory = uiState.initialDirectory,
        onInitialDirectoryChange = { newInitialDirectory -> viewModel.updateState { it.copy(initialDirectory = newInitialDirectory) } },
        terminalInputState = uiState.terminalInputState,
        onTerminalInputStateChange = { newState -> viewModel.updateState { it.copy(terminalInputState = newState) } },
        keepScreenOnMode = uiState.keepScreenOnMode,
        onKeepScreenOnModeChange = { newMode -> viewModel.updateState { it.copy(keepScreenOnMode = newMode) } },
        useLocalDns = uiState.useLocalDns,
        onUseLocalDnsChange = { newValue -> viewModel.updateState { it.copy(useLocalDns = newValue) } },
        hostError = uiState.hostError,
        portError = uiState.portError,
        keepAliveIntervalSeconds = uiState.keepAliveIntervalSeconds,
        onKeepAliveIntervalSecondsChange = { seconds -> viewModel.updateState { it.copy(keepAliveIntervalSeconds = seconds) } },
        autoReconnect = uiState.autoReconnect,
        onAutoReconnectChange = { auto -> viewModel.updateState { it.copy(autoReconnect = auto) } },
        maxReconnectAttempts = uiState.maxReconnectAttempts,
        onMaxReconnectAttemptsChange = { max -> viewModel.updateState { it.copy(maxReconnectAttempts = max) } },
        jumpHostProfileId = uiState.jumpHostProfileId,
        onJumpHostProfileIdChange = { jumpId -> viewModel.updateState { it.copy(jumpHostProfileId = jumpId) } },
        wolConfig = uiState.wolConfig,
        onWolConfigChange = { wol -> viewModel.updateState { it.copy(wolConfig = wol) } },
        portKnockingConfig = uiState.portKnockingConfig,
        onPortKnockingConfigChange = { knock -> viewModel.updateState { it.copy(portKnockingConfig = knock) } },
        folderId = uiState.folderId,
        onFolderIdChange = { newFolder -> viewModel.updateFolderId(newFolder) },
        availableProfiles = availableProfiles,
        onSave = {
            if (viewModel.saveProfile(profileId)) {
                onNavigateBack()
            }
        },
        onNavigateBack = {
            viewModel.resetState()
            onNavigateBack()
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("UNUSED_PARAMETER")
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
    availableKeys: List<String> = emptyList(),
    keyReference: String = "",
    onKeyReferenceChange: (String) -> Unit = {},
    identities: List<IdentityProfile>,
    identityId: String?,
    onIdentityChange: (String?) -> Unit,
    onManageIdentities: () -> Unit,
    envVarsText: String,
    onEnvVarsTextChange: (String) -> Unit,
    portForwards: List<PortForwardConfig>,
    onPortForwardsChange: (List<PortForwardConfig>) -> Unit,
    commandSnippets: List<com.adamoutler.ssh.data.CommandSnippet>,
    onCommandSnippetsChange: (List<com.adamoutler.ssh.data.CommandSnippet>) -> Unit,
    initialDirectory: String,
    onInitialDirectoryChange: (String) -> Unit,
    terminalInputState: Int,
    onTerminalInputStateChange: (Int) -> Unit,
    keepScreenOnMode: com.adamoutler.ssh.data.KeepScreenOnMode,
    onKeepScreenOnModeChange: (com.adamoutler.ssh.data.KeepScreenOnMode) -> Unit,
    useLocalDns: Boolean,
    onUseLocalDnsChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onNavigateBack: () -> Unit,
    scrollState: androidx.compose.foundation.ScrollState = androidx.compose.foundation.rememberScrollState(),
    defaultPasswordVisible: Boolean = false,
    defaultShowAddDialog: Boolean = false,
    defaultShowAddSnippetDialog: Boolean = false,
    defaultPortForwardType: PortForwardType = PortForwardType.LOCAL,
    hostError: String? = null,
    portError: String? = null,
    keepAliveIntervalSeconds: Int = 15,
    onKeepAliveIntervalSecondsChange: (Int) -> Unit = {},
    autoReconnect: Boolean = true,
    onAutoReconnectChange: (Boolean) -> Unit = {},
    maxReconnectAttempts: Int = 5,
    onMaxReconnectAttemptsChange: (Int) -> Unit = {},
    jumpHostProfileId: String? = null,
    onJumpHostProfileIdChange: (String?) -> Unit = {},
    availableProfiles: List<com.adamoutler.ssh.data.ConnectionProfile> = emptyList(),
    wolConfig: com.adamoutler.ssh.data.WolConfig? = null,
    onWolConfigChange: (com.adamoutler.ssh.data.WolConfig?) -> Unit = {},
    portKnockingConfig: com.adamoutler.ssh.data.PortKnockingConfig? = null,
    onPortKnockingConfigChange: (com.adamoutler.ssh.data.PortKnockingConfig?) -> Unit = {},
    folderId: String? = null,
    onFolderIdChange: (String?) -> Unit = {},
) {
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
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(scrollState),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = protocol == Protocol.SSH,
                    onClick = { onProtocolChange(Protocol.SSH) },
                    label = { Text("SSH") },
                    modifier = Modifier.testTag("ProtocolSSH"),
                )
                FilterChip(
                    selected = protocol == Protocol.TELNET,
                    onClick = { onProtocolChange(Protocol.TELNET) },
                    label = { Text("Telnet") },
                    modifier = Modifier.testTag("ProtocolTelnet"),
                )
            }

            if (protocol == Protocol.TELNET) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = "Warning", tint = Color(0xFFE65100))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "⚠️ **Warning: Insecure Protocol.** Telnet transmits all data, including passwords, in cleartext. Use only on trusted local networks.",
                            color = Color(0xFFE65100),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = nickname,
                onValueChange = onNicknameChange,
                label = { Text("Nickname") },
                modifier = Modifier.fillMaxWidth().testTag("NicknameInput"),
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = folderId ?: "",
                onValueChange = { onFolderIdChange(it.ifBlank { null }) },
                label = { Text("Folder / Group (Optional)") },
                modifier = Modifier.fillMaxWidth().testTag("FolderInput"),
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = host,
                onValueChange = onHostChange,
                label = { Text("Host (IP or Domain)") },
                isError = hostError != null,
                supportingText = if (hostError != null) {
                    { Text(hostError) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth().testTag("HostInput"),
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = port,
                onValueChange = onPortChange,
                label = { Text("Port") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = portError != null,
                supportingText = if (portError != null) {
                    { Text(portError) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth().testTag("PortInput"),
            )

            if (protocol == Protocol.SSH) {
                Spacer(modifier = Modifier.height(8.dp))
                com.adamoutler.ssh.ui.components.JumpHostSelector(
                    currentProfileId = profileId,
                    selectedJumpHostId = jumpHostProfileId,
                    availableProfiles = availableProfiles,
                    targetHost = host,
                    onJumpHostSelected = onJumpHostProfileIdChange,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text("Authentication", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            if (protocol == Protocol.SSH) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = authType == AuthType.PASSWORD,
                        onClick = {
                            onAuthTypeChange(AuthType.PASSWORD)
                            onIdentityChange(null)
                        },
                        label = { Text("Password") },
                        modifier = Modifier.testTag("AuthTypePassword"),
                    )
                    FilterChip(
                        selected = authType == AuthType.KEY,
                        onClick = {
                            onAuthTypeChange(AuthType.KEY)
                        },
                        label = { Text("SSH Key (Identity)") },
                        modifier = Modifier.testTag("AuthTypeKey"),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (authType == AuthType.PASSWORD || protocol == Protocol.TELNET) {
                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth().testTag("UsernameInput"),
                )
                Spacer(modifier = Modifier.height(8.dp))

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
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("PasswordInputLocked"),
                    )
                } else {
                    OutlinedTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        label = { Text("Password") },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(autoCorrect = false, keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("PasswordInput"),
                    )
                }
            } else if (protocol == Protocol.SSH) {
                ExposedDropdownMenuBox(
                    expanded = isIdentityDropdownExpanded,
                    onExpandedChange = { isIdentityDropdownExpanded = !isIdentityDropdownExpanded },
                ) {
                    OutlinedTextField(
                        value = selectedIdentity?.name ?: "Select an Identity Profile",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Identity Profile") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isIdentityDropdownExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth().testTag("IdentityDropdown"),
                    )
                    ExposedDropdownMenu(
                        expanded = isIdentityDropdownExpanded,
                        onDismissRequest = { isIdentityDropdownExpanded = false },
                    ) {
                        if (identities.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No Identity Profiles found") },
                                onClick = { isIdentityDropdownExpanded = false },
                                enabled = false,
                            )
                        } else {
                            identities.forEach { identity ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(identity.name, style = MaterialTheme.typography.bodyMedium)
                                            Text(identity.username, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                    onClick = {
                                        onIdentityChange(identity.id)
                                        isIdentityDropdownExpanded = false
                                    },
                                )
                            }
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Create / Manage Identities...") },
                            onClick = {
                                onManageIdentities()
                                isIdentityDropdownExpanded = false
                            },
                            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                            modifier = Modifier.testTag("ManageIdentitiesDropdownAction"),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (selectedIdentity != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("SelectedIdentityCard"),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(selectedIdentity.name, style = MaterialTheme.typography.titleMedium)
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text("Keystore MasterKey", style = MaterialTheme.typography.labelSmall) },
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Username: ${selectedIdentity.username}", style = MaterialTheme.typography.bodyMedium)
                            val keySnippet = selectedIdentity.publicKey?.take(15) ?: "ed25519"
                            Text("Key Auth: $keySnippet...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = onManageIdentities,
                                modifier = Modifier.align(Alignment.End),
                            ) {
                                Text("Manage Identity")
                            }
                        }
                    }
                } else if (identities.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("EmptyIdentitiesPromptCard"),
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                                Text("No Identity Profiles Found", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                            Text(
                                "SSH Key authentication requires an Identity Profile containing your Keystore-backed keypair.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Button(
                                onClick = onManageIdentities,
                                modifier = Modifier.testTag("CreateIdentityPromptButton"),
                            ) {
                                Text("Create / Manage Identities")
                            }
                        }
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("NoIdentitySelectedCard"),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                "Select an Identity Profile above to use SSH key authentication.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text("Advanced Configuration", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                    Text("Use Local Network DNS", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Overrides secure Private DNS to resolve local hostnames. Queries may be unencrypted and visible to the network.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.Switch(
                    checked = useLocalDns,
                    onCheckedChange = onUseLocalDnsChange,
                    modifier = Modifier.testTag("UseLocalDnsToggle"),
                )
            }

            if (protocol == Protocol.SSH) {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeaderWithInfo(title = "SSH Keepalive", topic = "SSH Keepalive")
                val keepAlivePresets = listOf(0, 15, 30, 60)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().testTag("KeepAliveSegmentedRow")) {
                    keepAlivePresets.forEachIndexed { index, seconds ->
                        SegmentedButton(
                            selected = keepAliveIntervalSeconds == seconds,
                            onClick = { onKeepAliveIntervalSecondsChange(seconds) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = keepAlivePresets.size),
                            modifier = Modifier.testTag("KeepAlive_${seconds}s"),
                        ) {
                            Text(if (seconds == 0) "Off" else "${seconds}s")
                        }
                    }
                }
                Text(
                    text = "Sends encrypted SSH ping packets to prevent middleboxes and carrier NATs from dropping idle sessions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, start = 16.dp, end = 16.dp),
                )

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("Auto-Reconnect", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Automatically reconnects session on network drops with exponential backoff (up to $maxReconnectAttempts attempts).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = autoReconnect,
                        onCheckedChange = onAutoReconnectChange,
                        modifier = Modifier.testTag("AutoReconnectToggle"),
                    )
                }

                if (autoReconnect) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Max Retries: $maxReconnectAttempts", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = { onMaxReconnectAttemptsChange((maxReconnectAttempts - 1).coerceAtLeast(1)) },
                                modifier = Modifier.testTag("DecrementReconnectAttempts"),
                            ) {
                                Text("-")
                            }
                            FilledTonalButton(
                                onClick = { onMaxReconnectAttemptsChange((maxReconnectAttempts + 1).coerceAtMost(10)) },
                                modifier = Modifier.testTag("IncrementReconnectAttempts"),
                            ) {
                                Text("+")
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            com.adamoutler.ssh.ui.components.PreConnectActionsCard(
                wolConfig = wolConfig,
                onWolConfigChange = onWolConfigChange,
                portKnockingConfig = portKnockingConfig,
                onPortKnockingConfigChange = onPortKnockingConfigChange,
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = initialDirectory,
                onValueChange = onInitialDirectoryChange,
                label = { Text("Initial Directory (CWD)") },
                placeholder = { Text("e.g. /home/user") },
                modifier = Modifier.fillMaxWidth().testTag("InitialDirectoryInput"),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    autoCorrect = false,
                ),
                supportingText = {
                    Text(
                        text = "Executes 'cd' upon connection. Note: This hides the server's Message of the Day (MOTD).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )

            Spacer(modifier = Modifier.height(16.dp))
            SectionHeaderWithInfo(title = "Default Terminal View", topic = "Terminal Options")
            var isTerminalViewDropdownExpanded by remember { mutableStateOf(false) }
            val terminalViewOptions = listOf("Standard", "Keyboard Only", "Keyboard & Extra Keys")
            ExposedDropdownMenuBox(
                expanded = isTerminalViewDropdownExpanded,
                onExpandedChange = { isTerminalViewDropdownExpanded = !isTerminalViewDropdownExpanded },
            ) {
                OutlinedTextField(
                    value = terminalViewOptions.getOrNull(terminalInputState) ?: "Standard",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Default Input Mode") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isTerminalViewDropdownExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(
                    expanded = isTerminalViewDropdownExpanded,
                    onDismissRequest = { isTerminalViewDropdownExpanded = false },
                ) {
                    terminalViewOptions.forEachIndexed { index, option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                onTerminalInputStateChange(index)
                                isTerminalViewDropdownExpanded = false
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            SectionHeaderWithInfo(title = "Keep Screen On Mode", topic = "Terminal Options")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val modes = com.adamoutler.ssh.data.KeepScreenOnMode.values()
                modes.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = keepScreenOnMode == mode,
                        onClick = { onKeepScreenOnModeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                    ) {
                        Text(
                            when (mode) {
                                com.adamoutler.ssh.data.KeepScreenOnMode.SYSTEM_DEFAULT -> "System"
                                com.adamoutler.ssh.data.KeepScreenOnMode.SMART_AWAKE -> "Smart"
                                com.adamoutler.ssh.data.KeepScreenOnMode.ALWAYS_ON -> "Always On"
                            },
                        )
                    }
                }
            }
            Text(
                text = "Smart Awake keeps the screen on while data flows. Both Smart and Always On will pause if the device is placed face-down or in a pocket.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, start = 16.dp, end = 16.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))
            SectionHeaderWithInfo(title = "Environment Variables", topic = "Environment Variables")
            OutlinedTextField(
                value = envVarsText,
                onValueChange = onEnvVarsTextChange,
                label = { Text("VAR=val,...") },
                modifier = Modifier.fillMaxWidth().testTag("EnvVarsInput"),
            )
            Spacer(modifier = Modifier.height(8.dp))
            SectionHeaderWithInfo(title = "Port Forwarding", topic = "Port Forwarding")
            Spacer(modifier = Modifier.height(8.dp))

            portForwards.forEach { pf ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (pf.type) {
                                    PortForwardType.LOCAL -> "Local Forward"
                                    PortForwardType.REMOTE -> "Remote Forward"
                                    PortForwardType.DYNAMIC -> "Dynamic Proxy"
                                },
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = if (pf.type == PortForwardType.DYNAMIC) "Local Port: ${pf.localPort} ➔ SOCKS5" else "${pf.localPort} ➔ ${pf.remoteHost}:${pf.remotePort}",
                                style = MaterialTheme.typography.bodyMedium,
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
                var type by remember { mutableStateOf(defaultPortForwardType) }
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
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                                ) {
                                    Text("Local")
                                }
                                SegmentedButton(
                                    selected = type == PortForwardType.REMOTE,
                                    onClick = { type = PortForwardType.REMOTE },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                                ) {
                                    Text("Remote")
                                }
                                SegmentedButton(
                                    selected = type == PortForwardType.DYNAMIC,
                                    onClick = { type = PortForwardType.DYNAMIC },
                                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                                ) {
                                    Text("Dynamic")
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            val helpText = when (type) {
                                PortForwardType.LOCAL -> "Forwards a local port on this device to a remote destination."
                                PortForwardType.REMOTE -> "Forwards a remote port on the server to this local device."
                                PortForwardType.DYNAMIC -> "Opens a local SOCKS5 proxy that dynamically forwards traffic over the secure connection."
                            }
                            androidx.compose.animation.AnimatedContent(
                                targetState = helpText,
                                label = "PortForwardingHelpAnimation",
                                modifier = Modifier.semantics {
                                    liveRegion = LiveRegionMode.Polite
                                },
                            ) { targetText ->
                                Text(
                                    text = targetText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))

                            androidx.compose.animation.AnimatedVisibility(visible = type == PortForwardType.DYNAMIC) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Warning, contentDescription = "Warning", tint = MaterialTheme.colorScheme.error)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "⚠️ Warning: This turns your device into a proxy. Ensure the port is not exposed to untrusted networks.",
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = localPort,
                                onValueChange = { localPort = it },
                                label = { Text("Local Port") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                            )

                            androidx.compose.animation.AnimatedVisibility(visible = type != PortForwardType.DYNAMIC) {
                                Column {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = remoteHost,
                                        onValueChange = { remoteHost = it },
                                        label = { Text("Remote Host") },
                                        singleLine = true,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = remotePort,
                                        onValueChange = { remotePort = it },
                                        label = { Text("Remote Port") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val lp = localPort.toIntOrNull()
                            val rp = remotePort.toIntOrNull() ?: 0
                            val rh = if (type == PortForwardType.DYNAMIC) "" else remoteHost

                            if (lp != null && (type == PortForwardType.DYNAMIC || (rp > 0 && rh.isNotBlank()))) {
                                val newList = portForwards.toMutableList()
                                newList.add(PortForwardConfig(type, lp, rh, rp))
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
                    },
                )
            }

            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Port Forward")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Port Forward")
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            CommandSnippetsSection(
                commandSnippets = commandSnippets,
                onCommandSnippetsChange = onCommandSnippetsChange,
                defaultShowAddSnippetDialog = defaultShowAddSnippetDialog,
            )
        }
    }
}

@Composable
fun CommandSnippetsSection(
    commandSnippets: List<com.adamoutler.ssh.data.CommandSnippet>,
    onCommandSnippetsChange: (List<com.adamoutler.ssh.data.CommandSnippet>) -> Unit,
    modifier: Modifier = Modifier,
    defaultShowAddSnippetDialog: Boolean = false,
) {
    Column(modifier = modifier) {
        SectionHeaderWithInfo(title = "Command Snippets", topic = "Command Snippets")
        Spacer(modifier = Modifier.height(8.dp))

        commandSnippets.forEach { snippet ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Row(
                    modifier = Modifier.padding(8.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = snippet.name,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = snippet.command,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (snippet.autoSend) {
                                Badge(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                                    Text("Auto-Send", modifier = Modifier.padding(horizontal = 4.dp))
                                }
                            }
                            if (snippet.requireAuth) {
                                Badge(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) {
                                    Text("Confirmation Required", modifier = Modifier.padding(horizontal = 4.dp))
                                }
                            }
                        }
                    }
                    IconButton(onClick = {
                        val newList = commandSnippets.toMutableList()
                        newList.remove(snippet)
                        onCommandSnippetsChange(newList)
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete Snippet")
                    }
                }
            }
        }

        var showAddSnippetDialog by remember { mutableStateOf(defaultShowAddSnippetDialog) }

        if (showAddSnippetDialog) {
            var snippetName by remember { mutableStateOf("") }
            var snippetCommand by remember { mutableStateOf("") }
            var autoSend by remember { mutableStateOf(false) }
            var requireAuth by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { showAddSnippetDialog = false },
                title = { Text("Add Command Snippet") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = snippetName,
                            onValueChange = { snippetName = it },
                            label = { Text("Name") },
                            placeholder = { Text("e.g., Restart Nginx") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = snippetCommand,
                            onValueChange = { snippetCommand = it },
                            label = { Text("Command") },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 200.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Execute Immediately", style = MaterialTheme.typography.bodyMedium)
                                Text("Appends Enter key automatically", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            androidx.compose.material3.Switch(checked = autoSend, onCheckedChange = { autoSend = it })
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Require Confirmation", style = MaterialTheme.typography.bodyMedium)
                                Text("Requires confirmation dialog before injecting", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            androidx.compose.material3.Switch(checked = requireAuth, onCheckedChange = { requireAuth = it })
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (snippetName.isNotBlank() && snippetCommand.isNotBlank()) {
                            val newList = commandSnippets.toMutableList()
                            newList.add(com.adamoutler.ssh.data.CommandSnippet(java.util.UUID.randomUUID().toString(), snippetName, snippetCommand, autoSend, requireAuth))
                            onCommandSnippetsChange(newList)
                            showAddSnippetDialog = false
                        }
                    }) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddSnippetDialog = false }) {
                        Text("Cancel")
                    }
                },
            )
        }

        Button(
            onClick = { showAddSnippetDialog = true },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add Snippet")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Snippet")
        }
    }
}

@Composable
fun SectionHeaderWithInfo(title: String, topic: String, modifier: Modifier = Modifier) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        IconButton(
            onClick = { uriHandler.openUri("https://github.com/adamoutler/ssh/wiki/${topic.replace(" ", "-")}") },
            modifier = Modifier
                .size(48.dp)
                .clearAndSetSemantics {
                    contentDescription = "Learn more about $topic. Opens external browser."
                    role = Role.Button
                },
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
