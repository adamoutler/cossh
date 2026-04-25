package com.adamoutler.ssh.ui.screens.identity

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
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
import com.adamoutler.ssh.crypto.SSHKeyGenerator
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.IdentityProfile
import com.adamoutler.ssh.ui.screens.IdentityViewModel
import com.adamoutler.ssh.network.SshConnectionManager
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.ui.events.UiEvent
import com.adamoutler.ssh.ui.events.UiEventBus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditIdentityScreen(
    identityId: String?,
    viewModel: IdentityViewModel = viewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    var passwordVisible by remember { mutableStateOf(false) }
    var showInjectDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(identityId) {
        viewModel.loadIdentityIfNeeded(identityId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (identityId == null) "Add Identity" else "Edit Identity") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.resetState()
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val passBytes = if (uiState.isPasswordLocked) {
                            uiState.originalPassword
                        } else if (uiState.password.isNotEmpty()) {
                            uiState.password.toByteArray(Charsets.UTF_8)
                        } else null

                        val identity = IdentityProfile(
                            id = identityId ?: java.util.UUID.randomUUID().toString(),
                            name = uiState.name,
                            username = uiState.username,
                            password = passBytes,
                            publicKey = if (uiState.publicKey.isNotEmpty()) uiState.publicKey else null,
                            privateKey = uiState.privateKey,
                            authType = uiState.authType
                        )
                        viewModel.saveIdentity(identity)
                        viewModel.resetState()
                        onBack()
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = { newName -> viewModel.updateState { it.copy(name = newName) } },
                label = { Text("Identity Name (e.g. My Home Server)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = uiState.username,
                onValueChange = { newUsername -> viewModel.updateState { it.copy(username = newUsername) } },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth()
            )

            if (uiState.isPasswordLocked) {
                OutlinedTextField(
                    value = "••••••••",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Password (optional)") },
                    trailingIcon = {
                        IconButton(onClick = { 
                            viewModel.updateState { it.copy(isPasswordLocked = false, password = "") }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Edit Password",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = { newPass -> viewModel.updateState { it.copy(password = newPass) } },
                    label = { Text("Password (optional)") },
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
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider()

            Text("SSH Key Management", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val keyPair = SSHKeyGenerator.generateEd25519KeyPair()
                        viewModel.updateState { 
                            it.copy(
                                publicKey = SSHKeyGenerator.encodePublicKey(keyPair),
                                privateKey = SSHKeyGenerator.encodePrivateKey(keyPair),
                                authType = AuthType.KEY
                            ) 
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gen Ed25519")
                }

                Button(
                    onClick = {
                        val keyPair = SSHKeyGenerator.generateRSAKeyPair()
                        viewModel.updateState { 
                            it.copy(
                                publicKey = SSHKeyGenerator.encodePublicKey(keyPair),
                                privateKey = SSHKeyGenerator.encodePrivateKey(keyPair),
                                authType = AuthType.KEY
                            ) 
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gen RSA-4096")
                }
            }

            Button(
                onClick = { viewModel.updateState { it.copy(manualKeyEntry = !it.manualKeyEntry) } },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (uiState.manualKeyEntry) "Hide Manual Key Entry" else "Enter Private Key Manually")
            }

            if (uiState.manualKeyEntry) {
                OutlinedTextField(
                    value = uiState.manualPrivKey,
                    onValueChange = { newKey -> 
                        viewModel.updateState { 
                            it.copy(
                                manualPrivKey = newKey,
                                privateKey = if (newKey.isNotEmpty()) newKey.toByteArray(Charsets.UTF_8) else null,
                                authType = if (newKey.isNotEmpty()) AuthType.KEY else it.authType
                            ) 
                        }
                    },
                    label = { Text("Paste Private Key (PEM format)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 5
                )
            }

            if (uiState.publicKey.isNotEmpty()) {
                OutlinedTextField(
                    value = uiState.publicKey,
                    onValueChange = { newKey -> viewModel.updateState { it.copy(publicKey = newKey) } },
                    label = { Text("Public Key") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 5,
                    readOnly = true
                )
                
                Button(
                    onClick = { showInjectDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Inject to Server (ssh-copy-id)")
                }

                Text(
                    "Private key is stored securely and will not be displayed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    if (showInjectDialog) {
        InjectKeyDialog(
            onDismiss = { showInjectDialog = false },
            onInject = { host, port, tempPassword ->
                showInjectDialog = false
                coroutineScope.launch {
                    UiEventBus.publish(UiEvent.ShowSnackbar("Injecting key..."))
                    val manager = SshConnectionManager(context = context)
                    val tempProfile = ConnectionProfile(
                        id = "temp",
                        nickname = "Temp",
                        host = host,
                        port = port,
                        username = uiState.username,
                        authType = AuthType.PASSWORD,
                        password = tempPassword.toByteArray()
                    )
                    val success = manager.injectPublicKey(tempProfile, uiState.publicKey)
                    if (success) {
                        UiEventBus.publish(UiEvent.ShowSnackbar("Key injected successfully!"))
                    } else {
                        UiEventBus.publish(UiEvent.ShowSnackbar("Failed to inject key. Check logs."))
                    }
                }
            }
        )
    }
}
