package com.adamoutler.ssh.ui.screens.identity

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var publicKey by remember { mutableStateOf("") }
    var privateKey by remember { mutableStateOf<ByteArray?>(null) }
    var authType by remember { mutableStateOf(AuthType.PASSWORD) }
    var passwordVisible by remember { mutableStateOf(false) }
    
    var showInjectDialog by remember { mutableStateOf(false) }
    var manualKeyEntry by remember { mutableStateOf(false) }
    var manualPrivKey by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(identityId) {
        if (identityId != null) {
            viewModel.getIdentity(identityId)?.let {
                name = it.name
                username = it.username
                publicKey = it.publicKey ?: ""
                privateKey = it.privateKey
                authType = it.authType
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (identityId == null) "Add Identity" else "Edit Identity") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val identity = IdentityProfile(
                            id = identityId ?: java.util.UUID.randomUUID().toString(),
                            name = name,
                            username = username,
                            password = if (password.isNotEmpty()) password.toByteArray() else null,
                            publicKey = if (publicKey.isNotEmpty()) publicKey else null,
                            privateKey = privateKey,
                            authType = authType
                        )
                        viewModel.saveIdentity(identity)
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
                value = name,
                onValueChange = { name = it },
                label = { Text("Identity Name (e.g. My Home Server)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password (optional)") },
                visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { passwordVisible = !passwordVisible }) {
                        Text(text = if (passwordVisible) "Hide" else "Show")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Divider()

            Text("SSH Key Management", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val keyPair = SSHKeyGenerator.generateEd25519KeyPair()
                        publicKey = SSHKeyGenerator.encodePublicKey(keyPair)
                        privateKey = SSHKeyGenerator.encodePrivateKey(keyPair)
                        authType = AuthType.KEY
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
                        publicKey = SSHKeyGenerator.encodePublicKey(keyPair)
                        privateKey = SSHKeyGenerator.encodePrivateKey(keyPair)
                        authType = AuthType.KEY
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gen RSA-4096")
                }
            }

            Button(
                onClick = { manualKeyEntry = !manualKeyEntry },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (manualKeyEntry) "Hide Manual Key Entry" else "Enter Private Key Manually")
            }

            if (manualKeyEntry) {
                OutlinedTextField(
                    value = manualPrivKey,
                    onValueChange = { 
                        manualPrivKey = it
                        privateKey = if (it.isNotEmpty()) it.toByteArray() else null
                        if (it.isNotEmpty()) authType = AuthType.KEY
                    },
                    label = { Text("Paste Private Key (PEM format)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 5
                )
            }

            if (publicKey.isNotEmpty()) {
                OutlinedTextField(
                    value = publicKey,
                    onValueChange = { publicKey = it },
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
                        username = username,
                        authType = AuthType.PASSWORD,
                        password = tempPassword.toByteArray()
                    )
                    val success = manager.injectPublicKey(tempProfile, publicKey)
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
