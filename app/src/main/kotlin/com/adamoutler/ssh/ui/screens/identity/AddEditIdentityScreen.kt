package com.adamoutler.ssh.ui.screens.identity

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.IdentityProfile
import com.adamoutler.ssh.ui.screens.IdentityFormState
import com.adamoutler.ssh.ui.screens.IdentityViewModel

@Composable
fun AddEditIdentityScreen(
    identityId: String?,
    viewModel: IdentityViewModel = viewModel(),
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    var passwordVisible by remember { mutableStateOf(false) }
    var showInjectDialog by remember { mutableStateOf(false) }

    LaunchedEffect(identityId) {
        viewModel.loadIdentityIfNeeded(identityId)
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val profiles = remember { com.adamoutler.ssh.crypto.SecurityStorageManager(context).getAllProfiles() }

    AddEditIdentityScreenContent(
        identityId = identityId,
        uiState = uiState,
        passwordVisible = passwordVisible,
        onPasswordVisibleChange = { passwordVisible = it },
        showInjectDialog = showInjectDialog,
        onShowInjectDialogChange = { showInjectDialog = it },
        profiles = profiles,
        onNameChange = { newName -> viewModel.updateState { it.copy(name = newName) } },
        onUsernameChange = { newUsername -> viewModel.updateState { it.copy(username = newUsername) } },
        onPasswordChange = { newPass -> viewModel.updateState { it.copy(password = newPass) } },
        onPasswordLockedChange = { locked -> viewModel.updateState { it.copy(isPasswordLocked = locked, password = if (!locked) "" else it.password) } },
        onManualKeyEntryChange = { manual -> viewModel.updateState { it.copy(manualKeyEntry = manual) } },
        onManualPrivKeyChange = { newKey ->
            viewModel.updateState {
                it.copy(
                    manualPrivKey = newKey,
                    privateKey = if (newKey.isNotEmpty()) newKey.toByteArray(Charsets.UTF_8) else null,
                    authType = if (newKey.isNotEmpty()) AuthType.KEY else it.authType,
                )
            }
        },
        onPublicKeyChange = { newKey -> viewModel.updateState { it.copy(publicKey = newKey) } },
        onGenerateEd25519 = { viewModel.generateEd25519KeyPair() },
        onGenerateRSA = { viewModel.generateRSAKeyPair() },
        onInjectPublicKey = { host, port, tempPassword -> viewModel.injectPublicKey(host, port, tempPassword) },
        onSave = {
            val passBytes = if (uiState.isPasswordLocked) {
                uiState.originalPassword
            } else if (uiState.password.isNotEmpty()) {
                uiState.password.toByteArray(Charsets.UTF_8)
            } else {
                null
            }

            val identity = IdentityProfile(
                id = identityId ?: java.util.UUID.randomUUID().toString(),
                name = uiState.name,
                username = uiState.username,
                password = passBytes,
                publicKey = if (uiState.publicKey.isNotEmpty()) uiState.publicKey else null,
                privateKey = uiState.privateKey,
                authType = uiState.authType,
            )
            viewModel.saveIdentity(identity)
            viewModel.resetState()
            onBack()
        },
        onBack = {
            viewModel.resetState()
            onBack()
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditIdentityScreenContent(
    identityId: String?,
    uiState: IdentityFormState,
    passwordVisible: Boolean,
    onPasswordVisibleChange: (Boolean) -> Unit,
    showInjectDialog: Boolean,
    onShowInjectDialogChange: (Boolean) -> Unit,
    profiles: List<ConnectionProfile>,
    onNameChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordLockedChange: (Boolean) -> Unit,
    onManualKeyEntryChange: (Boolean) -> Unit,
    onManualPrivKeyChange: (String) -> Unit,
    onPublicKeyChange: (String) -> Unit,
    onGenerateEd25519: () -> Unit,
    onGenerateRSA: () -> Unit,
    onInjectPublicKey: (String, Int, String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (identityId == null) "Add Identity" else "Edit Identity") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onSave) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = onNameChange,
                label = { Text("Identity Name (e.g. My Home Server)") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = uiState.username,
                onValueChange = onUsernameChange,
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(),
            )

            if (uiState.isPasswordLocked) {
                OutlinedTextField(
                    value = "••••••••",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Password (optional)") },
                    trailingIcon = {
                        IconButton(onClick = { onPasswordLockedChange(false) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Edit Password",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = onPasswordChange,
                    label = { Text("Password (optional)") },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { onPasswordVisibleChange(!passwordVisible) }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            HorizontalDivider()

            Text("SSH Key Management", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onGenerateEd25519,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gen Ed25519")
                }

                Button(
                    onClick = onGenerateRSA,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gen RSA-4096")
                }
            }

            Button(
                onClick = { onManualKeyEntryChange(!uiState.manualKeyEntry) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (uiState.manualKeyEntry) "Hide Manual Key Entry" else "Enter Private Key Manually")
            }

            if (uiState.manualKeyEntry) {
                OutlinedTextField(
                    value = uiState.manualPrivKey,
                    onValueChange = onManualPrivKeyChange,
                    label = { Text("Paste Private Key (PEM format)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 5,
                )
            }

            if (uiState.publicKey.isNotEmpty()) {
                OutlinedTextField(
                    value = uiState.publicKey,
                    onValueChange = onPublicKeyChange,
                    label = { Text("Public Key") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 5,
                    readOnly = true,
                )

                Button(
                    onClick = { onShowInjectDialogChange(true) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Inject to Server (ssh-copy-id)")
                }

                Text(
                    "Private key is stored securely and will not be displayed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }

    if (showInjectDialog) {
        InjectKeyDialog(
            onDismiss = { onShowInjectDialogChange(false) },
            onInject = { host, port, tempPassword ->
                onShowInjectDialogChange(false)
                onInjectPublicKey(host, port, tempPassword)
            },
            profiles = profiles,
        )
    }
}
