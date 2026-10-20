package com.adamoutler.ssh.ui.screens

import android.app.Activity
import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.adamoutler.ssh.BuildConfig
import com.adamoutler.ssh.billing.BillingManager
import com.adamoutler.ssh.sync.DriveSyncManager
import com.adamoutler.ssh.ui.components.GatedFeatureWrapper
import com.adamoutler.ssh.ui.theme.TerminalFontChoice
import com.adamoutler.ssh.ui.theme.TerminalTheme

@Composable
fun SettingsScreen(
    billingManager: BillingManager,
    driveSyncManager: DriveSyncManager,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val factory = remember {
        viewModelFactory {
            initializer {
                SettingsViewModel(
                    context.applicationContext as Application,
                    billingManager,
                    driveSyncManager,
                )
            }
        }
    }

    val viewModel: SettingsViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsState()

    val selectedTheme = remember(uiState.globalColorTheme) {
        TerminalTheme.fromId(uiState.globalColorTheme)
    }
    val selectedFont = remember(uiState.globalFontChoice) {
        TerminalFontChoice.fromId(uiState.globalFontChoice)
    }

    if (uiState.showPassphraseDialog) {
        var passphrase by remember { mutableStateOf("") }
        SyncPassphraseDialog(
            passphrase = passphrase,
            onPassphraseChange = { passphrase = it },
            onDismiss = { viewModel.dismissPassphraseDialog() },
            onConfirm = { activity?.let { viewModel.savePassphraseAndSync(passphrase, it) } },
        )
    }

    SettingsScreenContent(
        isCloudSyncEnabled = uiState.isCloudSyncEnabled,
        isSyncing = uiState.isSyncing,
        defaultGroupName = uiState.defaultGroupName,
        isPassphraseSet = uiState.isPassphraseSet,
        onDefaultGroupNameChange = { viewModel.updateDefaultGroupName(it) },
        onPurchaseCloudSync = { activity?.let { viewModel.purchaseCloudSync(it) } },
        onAuthenticateGoogle = { activity?.let { viewModel.authenticateGoogle(it) } },
        onResetPassphrase = { viewModel.showPassphraseDialog() },
        onNavigateBack = onNavigateBack,
        selectedColorTheme = selectedTheme,
        selectedFontChoice = selectedFont,
        onColorThemeChange = { viewModel.updateColorTheme(it) },
        onFontChoiceChange = { viewModel.updateFontChoice(it) },
        isAppLockEnabled = uiState.isAppLockEnabled,
        appLockTimeoutSeconds = uiState.appLockTimeoutSeconds,
        onAppLockEnabledChange = { viewModel.updateAppLockEnabled(it) },
        onAppLockTimeoutChange = { viewModel.updateAppLockTimeout(it) },
    )
}

@Composable
fun SyncPassphraseDialog(
    passphrase: String,
    onPassphraseChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.widthIn(max = 320.dp),
        onDismissRequest = onDismiss,
        title = { Text("Sync Passphrase") },
        text = {
            Column {
                Text("Enter a secure passphrase to encrypt your backups before they leave this device.")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = onPassphraseChange,
                    label = { Text("Passphrase") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(autoCorrect = false, keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = passphrase.isNotBlank(),
            ) {
                Text("Save & Sync")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    isCloudSyncEnabled: Boolean,
    isSyncing: Boolean,
    defaultGroupName: String,
    isPassphraseSet: Boolean,
    onDefaultGroupNameChange: (String) -> Unit,
    onPurchaseCloudSync: () -> Unit,
    onAuthenticateGoogle: () -> Unit,
    onResetPassphrase: () -> Unit,
    onNavigateBack: () -> Unit,
    selectedColorTheme: TerminalTheme = TerminalTheme.COBALT_BLUE,
    selectedFontChoice: TerminalFontChoice = TerminalFontChoice.DEFAULT_MONOSPACE,
    onColorThemeChange: (TerminalTheme) -> Unit = {},
    onFontChoiceChange: (TerminalFontChoice) -> Unit = {},
    isAppLockEnabled: Boolean = false,
    appLockTimeoutSeconds: Int = 0,
    onAppLockEnabledChange: (Boolean) -> Unit = {},
    onAppLockTimeoutChange: (Int) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(text = "General Settings", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = defaultGroupName,
                        onValueChange = onDefaultGroupNameChange,
                        label = { Text("Default Group Name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(text = "Terminal Appearance", style = MaterialTheme.typography.titleMedium)

                    var isThemeExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = isThemeExpanded,
                        onExpandedChange = { isThemeExpanded = !isThemeExpanded },
                    ) {
                        OutlinedTextField(
                            value = selectedColorTheme.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Color Theme") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isThemeExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                        )
                        ExposedDropdownMenu(
                            expanded = isThemeExpanded,
                            onDismissRequest = { isThemeExpanded = false },
                        ) {
                            TerminalTheme.entries.forEach { themeOption ->
                                DropdownMenuItem(
                                    text = { Text(themeOption.displayName) },
                                    onClick = {
                                        onColorThemeChange(themeOption)
                                        isThemeExpanded = false
                                    },
                                )
                            }
                        }
                    }

                    var isFontExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = isFontExpanded,
                        onExpandedChange = { isFontExpanded = !isFontExpanded },
                    ) {
                        OutlinedTextField(
                            value = selectedFontChoice.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Monospace Font") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isFontExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                        )
                        ExposedDropdownMenu(
                            expanded = isFontExpanded,
                            onDismissRequest = { isFontExpanded = false },
                        ) {
                            TerminalFontChoice.entries.forEach { fontOption ->
                                DropdownMenuItem(
                                    text = { Text(fontOption.displayName) },
                                    onClick = {
                                        onFontChoiceChange(fontOption)
                                        isFontExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }

            TerminalPreviewCard(
                theme = selectedColorTheme,
                fontChoice = selectedFontChoice,
            )

            GatedFeatureWrapper(
                isEnabled = BuildConfig.ENABLE_CLOUD_SYNC,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(text = "Cloud Sync & Backup", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Securely sync your encrypted profiles across devices using your hidden Google Drive App Data folder.",
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        if (!isCloudSyncEnabled) {
                            Button(
                                onClick = onPurchaseCloudSync,
                                modifier = Modifier.align(Alignment.End),
                            ) {
                                Text("Unlock Cloud Sync ($10.00)")
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Cloud Sync Enabled", style = MaterialTheme.typography.bodyLarge)
                                Switch(
                                    checked = true,
                                    onCheckedChange = { /* TODO Disable sync */ },
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (isPassphraseSet) {
                                    TextButton(onClick = onResetPassphrase) {
                                        Text("Reset Passphrase")
                                    }
                                } else {
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                Button(
                                    onClick = onAuthenticateGoogle,
                                    enabled = !isSyncing,
                                ) {
                                    Text(if (isSyncing) "Authenticating..." else "Authenticate with Google")
                                }
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(text = "App Security & Biometrics", style = MaterialTheme.typography.titleMedium)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Biometric App Lock", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "Require biometric authentication to unlock CoSSH",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = isAppLockEnabled,
                            onCheckedChange = onAppLockEnabledChange,
                        )
                    }

                    if (isAppLockEnabled) {
                        val timeoutOptions = listOf(
                            0 to "Immediately",
                            30 to "After 30 seconds",
                            60 to "After 1 minute",
                            300 to "After 5 minutes",
                        )
                        var isTimeoutExpanded by remember { mutableStateOf(false) }
                        val currentTimeoutLabel = timeoutOptions.firstOrNull { it.first == appLockTimeoutSeconds }?.second ?: "Immediately"

                        ExposedDropdownMenuBox(
                            expanded = isTimeoutExpanded,
                            onExpandedChange = { isTimeoutExpanded = !isTimeoutExpanded },
                        ) {
                            OutlinedTextField(
                                value = currentTimeoutLabel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Lock Timeout") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isTimeoutExpanded) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                            )
                            ExposedDropdownMenu(
                                expanded = isTimeoutExpanded,
                                onDismissRequest = { isTimeoutExpanded = false },
                            ) {
                                timeoutOptions.forEach { (seconds, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            onAppLockTimeoutChange(seconds)
                                            isTimeoutExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TerminalPreviewCard(
    theme: TerminalTheme,
    fontChoice: TerminalFontChoice,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Terminal Appearance Preview",
                style = MaterialTheme.typography.titleMedium,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = Color(theme.background),
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(12.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "user@cossh:~$ ",
                            color = Color(theme.ansiColors[2]),
                            fontFamily = fontChoice.composeFontFamily,
                            fontSize = 13.sp,
                        )
                        Text(
                            text = "uname -srm",
                            color = Color(theme.foreground),
                            fontFamily = fontChoice.composeFontFamily,
                            fontSize = 13.sp,
                        )
                    }
                    Text(
                        text = "Linux 6.6.0-cossh aarch64",
                        color = Color(theme.foreground),
                        fontFamily = fontChoice.composeFontFamily,
                        fontSize = 13.sp,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "user@cossh:~$ ",
                            color = Color(theme.ansiColors[6]),
                            fontFamily = fontChoice.composeFontFamily,
                            fontSize = 13.sp,
                        )
                        Text(
                            text = "echo \"Hardened Security\"",
                            color = Color(theme.foreground),
                            fontFamily = fontChoice.composeFontFamily,
                            fontSize = 13.sp,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Hardened Security ",
                            color = Color(theme.foreground),
                            fontFamily = fontChoice.composeFontFamily,
                            fontSize = 13.sp,
                        )
                        Box(
                            modifier = Modifier
                                .width(8.dp)
                                .height(14.dp)
                                .background(Color(theme.cursor)),
                        )
                    }
                }
            }
        }
    }
}
