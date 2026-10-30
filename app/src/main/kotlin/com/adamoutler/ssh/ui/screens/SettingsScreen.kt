package com.adamoutler.ssh.ui.screens

import android.app.Activity
import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
    onNavigateToKnownHosts: () -> Unit = {},
    onNavigateToAuditLog: () -> Unit = {},
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

    if (uiState.showRestoreDialog) {
        RestorePassphraseDialog(
            onDismiss = { viewModel.dismissRestoreDialog() },
            onConfirm = { passChars ->
                viewModel.restoreFromCloud(passChars, activity)
            },
        )
    }

    if (uiState.showDisableSyncDialog) {
        DisableSyncConfirmationDialog(
            onDismiss = { viewModel.dismissDisableSyncDialog() },
            onConfirmDisable = { deleteRemote ->
                viewModel.disableCloudSync(deleteRemote)
            },
        )
    }

    SettingsScreenContent(
        isCloudSyncEnabled = uiState.isCloudSyncEnabled,
        isCloudSyncActive = uiState.isCloudSyncActive,
        isSyncing = uiState.isSyncing,
        isRestoring = uiState.isRestoring,
        restoreStatusMessage = uiState.restoreStatusMessage,
        defaultGroupName = uiState.defaultGroupName,
        isPassphraseSet = uiState.isPassphraseSet,
        onDefaultGroupNameChange = { viewModel.updateDefaultGroupName(it) },
        onPurchaseCloudSync = { activity?.let { viewModel.purchaseCloudSync(it) } },
        onAuthenticateGoogle = { activity?.let { viewModel.authenticateGoogle(it) } },
        onResetPassphrase = { viewModel.showPassphraseDialog() },
        onRestoreFromCloud = { viewModel.showRestoreDialog() },
        onToggleCloudSync = { isChecked ->
            if (!isChecked) {
                viewModel.showDisableSyncDialog()
            } else {
                viewModel.enableCloudSync()
            }
        },
        onDismissRestoreStatus = { viewModel.clearRestoreStatusMessage() },
        onNavigateBack = onNavigateBack,
        selectedColorTheme = selectedTheme,
        selectedFontChoice = selectedFont,
        onColorThemeChange = { viewModel.updateColorTheme(it) },
        onFontChoiceChange = { viewModel.updateFontChoice(it) },
        isAppLockEnabled = uiState.isAppLockEnabled,
        appLockTimeoutSeconds = uiState.appLockTimeoutSeconds,
        onAppLockEnabledChange = { viewModel.updateAppLockEnabled(it) },
        onAppLockTimeoutChange = { viewModel.updateAppLockTimeout(it) },
        globalFontSize = uiState.globalFontSize,
        onFontSizeChange = { viewModel.updateFontSize(it) },
        extraKeysPreset = uiState.extraKeysPreset,
        onExtraKeysPresetChange = { viewModel.updateExtraKeysPreset(it) },
        allowOsc52Clipboard = uiState.allowOsc52Clipboard,
        onAllowOsc52ClipboardChange = { viewModel.updateAllowOsc52Clipboard(it) },
        onNavigateToKnownHosts = onNavigateToKnownHosts,
        onNavigateToAuditLog = onNavigateToAuditLog,
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

@Composable
fun RestorePassphraseDialog(
    onDismiss: () -> Unit,
    onConfirm: (CharArray) -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    AlertDialog(
        modifier = Modifier.widthIn(max = 320.dp),
        onDismissRequest = onDismiss,
        title = { Text("Restore from Cloud") },
        text = {
            Column {
                Text("Enter your sync passphrase to decrypt your cloud backup.")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
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
                onClick = {
                    val chars = passphrase.toCharArray()
                    passphrase = ""
                    onConfirm(chars)
                },
                enabled = passphrase.isNotBlank(),
            ) {
                Text("Restore")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun DisableSyncConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirmDisable: (deleteRemote: Boolean) -> Unit,
) {
    AlertDialog(
        modifier = Modifier.widthIn(max = 360.dp),
        onDismissRequest = onDismiss,
        title = { Text("Disable Cloud Sync") },
        text = {
            Text("Disabling Cloud Sync will stop background backups and remove stored sync credentials from this device.\n\nWould you also like to delete your remote backup from Google Drive?")
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.End,
            ) {
                TextButton(onClick = { onConfirmDisable(true) }) {
                    Text("Disable & Delete Remote Backup", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = { onConfirmDisable(false) }) {
                    Text("Disable (Keep Cloud Data)")
                }
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
    isCloudSyncActive: Boolean = true,
    isRestoring: Boolean = false,
    restoreStatusMessage: String? = null,
    onToggleCloudSync: (Boolean) -> Unit = {},
    onRestoreFromCloud: () -> Unit = {},
    onDismissRestoreStatus: () -> Unit = {},
    selectedColorTheme: TerminalTheme = TerminalTheme.COBALT_BLUE,
    selectedFontChoice: TerminalFontChoice = TerminalFontChoice.DEFAULT_MONOSPACE,
    onColorThemeChange: (TerminalTheme) -> Unit = {},
    onFontChoiceChange: (TerminalFontChoice) -> Unit = {},
    isAppLockEnabled: Boolean = false,
    appLockTimeoutSeconds: Int = 0,
    onAppLockEnabledChange: (Boolean) -> Unit = {},
    onAppLockTimeoutChange: (Int) -> Unit = {},
    globalFontSize: Int = 14,
    onFontSizeChange: (Int) -> Unit = {},
    extraKeysPreset: com.adamoutler.ssh.ui.components.ExtraKeysPresetId = com.adamoutler.ssh.ui.components.ExtraKeysPresetId.DEFAULT,
    onExtraKeysPresetChange: (com.adamoutler.ssh.ui.components.ExtraKeysPresetId) -> Unit = {},
    allowOsc52Clipboard: Boolean = false,
    onAllowOsc52ClipboardChange: (Boolean) -> Unit = {},
    onNavigateToKnownHosts: () -> Unit = {},
    onNavigateToAuditLog: () -> Unit = {},
    scrollState: androidx.compose.foundation.ScrollState = rememberScrollState(),
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
                .verticalScroll(scrollState)
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Terminal Font Size: ${globalFontSize}sp", style = MaterialTheme.typography.bodyLarge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { if (globalFontSize > 8) onFontSizeChange(globalFontSize - 1) },
                                modifier = Modifier.testTag("DecreaseFontSizeButton"),
                            ) {
                                Text("-", style = MaterialTheme.typography.titleLarge)
                            }
                            Text(
                                "$globalFontSize",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.testTag("FontSizeLabel"),
                            )
                            IconButton(
                                onClick = { if (globalFontSize < 36) onFontSizeChange(globalFontSize + 1) },
                                modifier = Modifier.testTag("IncreaseFontSizeButton"),
                            ) {
                                Text("+", style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }

                    var isPresetExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = isPresetExpanded,
                        onExpandedChange = { isPresetExpanded = !isPresetExpanded },
                    ) {
                        OutlinedTextField(
                            value = extraKeysPreset.name,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Extra Keys Row Preset") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isPresetExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier.fillMaxWidth().menuAnchor().testTag("ExtraKeysPresetDropdown"),
                        )
                        ExposedDropdownMenu(
                            expanded = isPresetExpanded,
                            onDismissRequest = { isPresetExpanded = false },
                        ) {
                            com.adamoutler.ssh.ui.components.ExtraKeysPresetId.entries.forEach { presetOption ->
                                DropdownMenuItem(
                                    text = { Text(presetOption.name) },
                                    onClick = {
                                        onExtraKeysPresetChange(presetOption)
                                        isPresetExpanded = false
                                    },
                                    modifier = Modifier.testTag("ExtraKeysPresetItem_${presetOption.name}"),
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
                                    checked = isCloudSyncActive,
                                    onCheckedChange = onToggleCloudSync,
                                )
                            }

                            if (restoreStatusMessage != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = restoreStatusMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(onClick = onDismissRestoreStatus) {
                                        Text("Dismiss")
                                    }
                                }
                            }

                            if (isCloudSyncActive) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (isPassphraseSet) {
                                            TextButton(
                                                onClick = onResetPassphrase,
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                Text("Reset Passphrase", maxLines = 1)
                                            }
                                        }

                                        OutlinedButton(
                                            onClick = onRestoreFromCloud,
                                            enabled = !isSyncing && !isRestoring,
                                            modifier = if (isPassphraseSet) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                                        ) {
                                            Text(if (isRestoring) "Restoring..." else "Restore from Cloud", maxLines = 1)
                                        }
                                    }

                                    Button(
                                        onClick = onAuthenticateGoogle,
                                        enabled = !isSyncing && !isRestoring,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(if (isSyncing) "Authenticating..." else "Authenticate with Google")
                                    }
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

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Allow OSC 52 Clipboard Access", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "Allows remote shell sessions to copy text directly to your Android system clipboard.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = allowOsc52Clipboard,
                            onCheckedChange = onAllowOsc52ClipboardChange,
                            modifier = Modifier.testTag("Osc52ClipboardSwitch"),
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToKnownHosts() }
                            .testTag("KnownHostsSettingsRow"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Known Hosts", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "Inspect trusted server keys, fingerprints, and hashed host parity",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = onNavigateToKnownHosts,
                            modifier = Modifier.testTag("KnownHostsNavigateButton"),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Manage Known Hosts",
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToAuditLog() }
                            .testTag("SecurityAuditLogSettingsRow"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Security Audit Log", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "Inspect tamper-evident SHA-256 hash-chained security and authentication events",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = onNavigateToAuditLog,
                            modifier = Modifier.testTag("SecurityAuditLogNavigateButton"),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "View Security Audit Log",
                            )
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
