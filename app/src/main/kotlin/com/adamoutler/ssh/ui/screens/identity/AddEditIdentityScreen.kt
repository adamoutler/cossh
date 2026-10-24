package com.adamoutler.ssh.ui.screens.identity

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.IdentityProfile
import com.adamoutler.ssh.ui.events.UiEvent
import com.adamoutler.ssh.ui.events.UiEventBus
import com.adamoutler.ssh.ui.screens.IdentityFormState
import com.adamoutler.ssh.ui.screens.IdentityViewModel
import com.adamoutler.ssh.ui.screens.identity.components.EncryptedKeyPassphraseModal

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

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bytes = stream.readBytes()
                    viewModel.importKeyFromBytes(bytes)
                }
            } catch (e: Exception) {
                UiEventBus.publish(UiEvent.ShowSnackbar("Failed to read key file: ${e.message}"))
            }
        }
    }

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
        onGenerateECDSA = { viewModel.generateECDSAKeyPair() },
        onGenerateRSA = { viewModel.generateRSAKeyPair() },
        onGenerateStrongBox = { viewModel.generateStrongBoxKeyPair() },
        onImportFromFile = {
            filePickerLauncher.launch(
                arrayOf("*/*", "text/plain", "application/x-pem-file", "application/pkcs8", "application/octet-stream"),
            )
        },
        onUnlockPendingKey = { passphrase -> viewModel.unlockPendingKey(passphrase) },
        onCancelPendingUnlock = { viewModel.cancelPendingKeyUnlock() },
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
                securityTier = uiState.securityTier,
            )
            viewModel.saveIdentity(identity)
            viewModel.resetState()
            onBack()
        },
        onBack = {
            viewModel.resetState()
            onBack()
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@com.adamoutler.ssh.ui.screens.Generated
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
    onGenerateECDSA: () -> Unit = {},
    onGenerateRSA: () -> Unit,
    onGenerateStrongBox: () -> Unit = {},
    onImportFromFile: () -> Unit = {},
    onUnlockPendingKey: (CharArray) -> Unit = {},
    onCancelPendingUnlock: () -> Unit = {},
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
                    keyboardOptions = KeyboardOptions(autoCorrect = false, keyboardType = KeyboardType.Password),
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
                    onClick = onGenerateECDSA,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gen ECDSA")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onGenerateRSA,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gen RSA-4096")
                }

                FilledTonalButton(
                    onClick = onImportFromFile,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import File")
                }
            }

            Button(
                onClick = onGenerateStrongBox,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Gen StrongBox / Hardware EC Key 🛡️💎")
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
                    keyboardOptions = KeyboardOptions(autoCorrect = false, keyboardType = KeyboardType.Password),
                )
            }

            if (uiState.publicKey.isNotEmpty()) {
                var showAttestationSheet by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    com.adamoutler.ssh.ui.components.SecurityTierBadge(
                        securityTier = uiState.securityTier,
                        attestationRecord = uiState.attestationRecord,
                    )
                    TextButton(onClick = { showAttestationSheet = true }) {
                        Text("View Attestation")
                    }
                }

                if (showAttestationSheet) {
                    com.adamoutler.ssh.ui.components.KeyAttestationBottomSheet(
                        attestationRecord = uiState.attestationRecord,
                        securityTier = uiState.securityTier,
                        onDismiss = { showAttestationSheet = false },
                    )
                }

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

    if (uiState.pendingEncryptedKeyBytes != null) {
        EncryptedKeyPassphraseModal(
            keyFormatSummary = uiState.pendingCipherSummary ?: "Encrypted Key",
            errorMessage = uiState.passphraseError,
            onConfirm = onUnlockPendingKey,
            onDismiss = onCancelPendingUnlock,
        )
    }
}
