package com.adamoutler.ssh.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adamoutler.ssh.data.AuthType

@Composable
fun AddEditProfileScreen(
    profileId: String? = null,
    viewModel: AddEditProfileViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    var nickname by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("22") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var authType by remember { mutableStateOf(AuthType.PASSWORD) }
    var keyReference by remember { mutableStateOf("") }
    val availableKeys = viewModel.getAvailableKeys()

    LaunchedEffect(profileId) {
        if (profileId != null) {
            viewModel.getProfile(profileId)?.let { profile ->
                nickname = profile.nickname
                host = profile.host
                port = profile.port.toString()
                username = profile.username
                authType = profile.authType
                password = profile.password?.toString(Charsets.UTF_8) ?: ""
                keyReference = profile.sshKeyPasswordReferenceId ?: ""
            }
        }
    }

    AddEditProfileScreenContent(
        profileId = profileId,
        nickname = nickname,
        onNicknameChange = { nickname = it },
        host = host,
        onHostChange = { host = it },
        port = port,
        onPortChange = { port = it },
        username = username,
        onUsernameChange = { username = it },
        password = password,
        onPasswordChange = { password = it },
        authType = authType,
        onAuthTypeChange = { authType = it },
        availableKeys = availableKeys,
        keyReference = keyReference,
        onKeyReferenceChange = { keyReference = it },
        onSave = {
            val passBytes = if (authType == AuthType.PASSWORD && password.isNotEmpty()) {
                password.toByteArray(Charsets.UTF_8)
            } else null
            val keyRef = if (authType == AuthType.KEY) keyReference else null

            viewModel.saveProfile(
                id = profileId,
                nickname = nickname,
                host = host,
                port = port,
                username = username,
                authType = authType,
                password = passBytes,
                keyReference = keyRef
            )
            onNavigateBack()
        },
        onNavigateBack = onNavigateBack
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
    authType: AuthType,
    onAuthTypeChange: (AuthType) -> Unit,
    availableKeys: List<String>,
    keyReference: String,
    @Suppress("UNUSED_PARAMETER") onKeyReferenceChange: (String) -> Unit,
    onSave: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var isDropdownExpanded by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

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
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = host,
                onValueChange = onHostChange,
                label = { Text("Host (IP or Domain)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = port,
                onValueChange = onPortChange,
                label = { Text("Port") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = authType == AuthType.PASSWORD,
                    onClick = { onAuthTypeChange(AuthType.PASSWORD) },
                    label = { Text("Password") }
                )
                FilterChip(
                    selected = authType == AuthType.KEY,
                    onClick = { onAuthTypeChange(AuthType.KEY) },
                    label = { Text("SSH Key") }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (authType == AuthType.PASSWORD) {
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("Password") },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { passwordVisible = !passwordVisible }) {
                            Text(text = if (passwordVisible) "Hide" else "Show")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                ExposedDropdownMenuBox(
                    expanded = isDropdownExpanded,
                    onExpandedChange = { isDropdownExpanded = !isDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = keyReference.ifEmpty { "Select SSH Key" },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("SSH Key") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = isDropdownExpanded,
                        onDismissRequest = { isDropdownExpanded = false }
                    ) {
                        if (availableKeys.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No keys available") },
                                onClick = {
                                    isDropdownExpanded = false
                                },
                                enabled = false
                            )
                        } else {
                            availableKeys.forEach { key ->
                                DropdownMenuItem(
                                    text = { Text(key) },
                                    onClick = {
                                        onKeyReferenceChange(key)
                                        isDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}