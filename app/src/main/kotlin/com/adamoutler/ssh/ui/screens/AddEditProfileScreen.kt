package com.adamoutler.ssh.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.IdentityProfile
import com.adamoutler.ssh.data.PortForwardConfig
import com.adamoutler.ssh.data.PortForwardType

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
        portForwardsText = uiState.portForwardsText,
        onPortForwardsTextChange = { newPF -> viewModel.updateState { it.copy(portForwardsText = newPF) } },
        onSave = {
            val selectedIdent = identities.find { it.id == uiState.identityId }
            val finalUsername = if (selectedIdent != null) selectedIdent.username else uiState.username
            val finalAuthType = if (selectedIdent != null) selectedIdent.authType else uiState.authType
            
            // If using an identity, we don't store inline credentials in the profile
            val passBytes = if (uiState.identityId == null && finalAuthType == AuthType.PASSWORD) {
                if (uiState.isPasswordLocked) {
                    uiState.originalPassword
                } else if (uiState.password.isNotEmpty()) {
                    uiState.password.toByteArray(Charsets.UTF_8)
                } else null
            } else null
            val keyRef = if (uiState.identityId == null && finalAuthType == AuthType.KEY) uiState.keyReference else null

            val parsedEnvVars = uiState.envVarsText.split(",")
                .map { it.trim() }
                .filter { it.contains("=") }
                .associate { 
                    val parts = it.split("=", limit = 2)
                    parts[0] to parts[1]
                }
            
            val parsedPortForwards = uiState.portForwardsText.split(",")
                .map { it.trim() }
                .filter { it.contains(":") }
                .mapNotNull {
                    val parts = it.split(":")
                    if (parts.size >= 4) {
                        val type = if (parts[0].uppercase() == "L") PortForwardType.LOCAL else PortForwardType.REMOTE
                        val localPort = parts[1].toIntOrNull() ?: return@mapNotNull null
                        val remoteHost = parts[2]
                        val remotePort = parts[3].toIntOrNull() ?: return@mapNotNull null
                        PortForwardConfig(type, localPort, remoteHost, remotePort)
                    } else null
                }

            viewModel.saveProfile(
                id = profileId,
                nickname = uiState.nickname,
                host = uiState.host,
                port = uiState.port,
                username = finalUsername,
                authType = finalAuthType,
                password = passBytes,
                keyReference = keyRef,
                identityId = uiState.identityId,
                envVars = parsedEnvVars,
                portForwards = parsedPortForwards
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
    portForwardsText: String,
    onPortForwardsTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onNavigateBack: () -> Unit,
    defaultPasswordVisible: Boolean = false
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
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
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

            if (identityId == null) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth().testTag("UsernameInput")
                )
                Spacer(modifier = Modifier.height(8.dp))

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

                if (authType == AuthType.PASSWORD) {
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
                } else {
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
            OutlinedTextField(
                value = portForwardsText,
                onValueChange = onPortForwardsTextChange,
                label = { Text("Port Forwards (L:8080:host:80,R:9090:host:90)") },
                modifier = Modifier.fillMaxWidth().testTag("PortForwardsInput")
            )

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}