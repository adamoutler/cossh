package com.adamoutler.ssh.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.adamoutler.ssh.billing.BillingManager
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.sync.DriveSyncManager
import com.adamoutler.ssh.ui.components.KeystoreInvalidatedDialog
import com.adamoutler.ssh.ui.components.TerminalScreen
import com.adamoutler.ssh.ui.keys.KeyManagementScreen
import com.adamoutler.ssh.ui.screens.AddEditProfileScreen
import com.adamoutler.ssh.ui.screens.ConnectionListScreen
import com.adamoutler.ssh.ui.screens.SettingsScreen
import com.adamoutler.ssh.ui.screens.identity.AddEditIdentityScreen
import com.adamoutler.ssh.ui.screens.identity.IdentityListScreen

@Composable
fun AppNavigation(
    appLockManager: com.adamoutler.ssh.security.AppLockManager? = null,
    activity: androidx.activity.ComponentActivity? = androidx.compose.ui.platform.LocalContext.current as? androidx.activity.ComponentActivity,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val billingManager = remember { BillingManager(context) }
    val driveSyncManager = remember { DriveSyncManager(context) }
    val securityStorageManager = remember { SecurityStorageManager(context) }
    val lockManager = remember(appLockManager) {
        appLockManager ?: com.adamoutler.ssh.security.AppLockManager.getInstance(context.applicationContext)
    }

    val isLocked by lockManager.isLocked.collectAsState()
    var showKeystoreDialog by remember { mutableStateOf(false) }

    androidx.compose.runtime.DisposableEffect(activity, isLocked) {
        val listener = androidx.core.util.Consumer<android.content.Intent> { intent ->
            if (lockManager.isLocked.value) {
                lockManager.pendingResumeIntent.value = intent
            } else {
                IntentSanitizer.sanitizeAndNavigate(
                    intent = intent,
                    navController = navController,
                    activity = activity,
                    securityStorageManager = securityStorageManager,
                )
            }
        }
        activity?.addOnNewIntentListener(listener)
        onDispose {
            activity?.removeOnNewIntentListener(listener)
        }
    }

    androidx.compose.runtime.LaunchedEffect(isLocked) {
        if (!isLocked) {
            val pendingIntent = lockManager.pendingResumeIntent.value
            if (pendingIntent != null) {
                lockManager.pendingResumeIntent.value = null
                IntentSanitizer.sanitizeAndNavigate(
                    intent = pendingIntent,
                    navController = navController,
                    activity = activity,
                    securityStorageManager = securityStorageManager,
                )
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (lockManager.isLocked.value) {
            if (activity?.intent != null) {
                lockManager.pendingResumeIntent.value = activity.intent
            }
        } else {
            IntentSanitizer.sanitizeAndNavigate(
                intent = activity?.intent,
                navController = navController,
                activity = activity,
                securityStorageManager = securityStorageManager,
            )
        }

        com.adamoutler.ssh.ui.events.UiEventBus.events.collect { event ->
            if (event is com.adamoutler.ssh.ui.events.UiEvent.Navigate) {
                navController.navigate(event.route) {
                    launchSingleTop = true
                }
            } else if (event is com.adamoutler.ssh.ui.events.UiEvent.NavigateUp) {
                navController.navigateUp()
            } else if (event is com.adamoutler.ssh.ui.events.UiEvent.ShowKeystoreInvalidatedDialog) {
                showKeystoreDialog = true
            }
        }
    }

    if (showKeystoreDialog) {
        KeystoreInvalidatedDialog(
            onConfirmReset = {
                securityStorageManager.resetInvalidatedKeys()
                com.adamoutler.ssh.crypto.IdentityStorageManager(context).resetInvalidatedKeys()
                showKeystoreDialog = false
            },
            onDismissApp = {
                activity?.finishAffinity()
            },
        )
    }

    NavHost(navController = navController, startDestination = "connectionList") {
        composable("connectionList") {
            ConnectionListScreen(
                onAddConnection = { navController.navigate("addEditProfile") },
                onEditConnection = { profileId -> navController.navigate("addEditProfile?profileId=$profileId") },
                onConnect = { profileId, sessionId ->
                    if (sessionId != null) {
                        navController.navigate("terminal?profileId=$profileId&sessionId=$sessionId")
                    } else {
                        navController.navigate("terminal?profileId=$profileId")
                    }
                },
                onSettingsRequested = { navController.navigate("settings") },
                onManageIdentitiesRequested = { navController.navigate("identityList") },
            )
        }
        composable("settings") {
            SettingsScreen(
                billingManager = billingManager,
                driveSyncManager = driveSyncManager,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToKnownHosts = { navController.navigate("knownHosts") },
                onNavigateToAuditLog = { navController.navigate("securityAuditLog") },
            )
        }
        composable("knownHosts") {
            com.adamoutler.ssh.ui.screens.knownhosts.KnownHostsScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable("securityAuditLog") {
            com.adamoutler.ssh.ui.screens.audit.SecurityAuditLogScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "addEditProfile?profileId={profileId}",
            arguments = listOf(
                navArgument("profileId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getString("profileId")
            AddEditProfileScreen(
                profileId = profileId,
                onNavigateBack = { navController.popBackStack() },
                onManageIdentities = { navController.navigate("identityList") },
            )
        }
        composable(
            route = "terminal?profileId={profileId}&sessionId={sessionId}",
            arguments = listOf(
                navArgument("profileId") { type = NavType.StringType },
                navArgument("sessionId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getString("profileId") ?: ""
            val sessionId = backStackEntry.arguments?.getString("sessionId")

            val activityContext = LocalContext.current as androidx.activity.ComponentActivity
            val terminalViewModel: com.adamoutler.ssh.ui.screens.TerminalViewModel = androidx.lifecycle.viewmodel.compose.viewModel(activityContext)

            TerminalScreen(
                profileId = profileId,
                sessionId = sessionId,
                terminalViewModel = terminalViewModel,
                onNavigateBack = {
                    // Prevent double-pop race condition which causes a black screen
                    // when the terminal is terminated and popBackStack is called twice.
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    }
                },
            )
        }
        composable("identityList") {
            IdentityListScreen(
                onAddIdentity = { navController.navigate("addEditIdentity") },
                onEditIdentity = { identityId -> navController.navigate("addEditIdentity?identityId=$identityId") },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "addEditIdentity?identityId={identityId}",
            arguments = listOf(
                navArgument("identityId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val identityId = backStackEntry.arguments?.getString("identityId")
            AddEditIdentityScreen(
                identityId = identityId,
                onBack = { navController.popBackStack() },
            )
        }
        composable("keyManagement") {
            KeyManagementScreen()
        }
        composable(
            route = "sftpBrowser?profileId={profileId}",
            arguments = listOf(
                navArgument("profileId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getString("profileId") ?: ""
            val profile = remember(profileId) { securityStorageManager.getProfile(profileId) }
            val nickname = profile?.nickname ?: profile?.host ?: "SFTP"
            val activeSshClient = com.adamoutler.ssh.network.SshService.getActiveSshClient(profileId)
            val adapter: com.adamoutler.ssh.sftp.SftpClientAdapter = remember(activeSshClient) {
                if (activeSshClient != null && activeSshClient.isConnected && activeSshClient.isAuthenticated) {
                    try {
                        com.adamoutler.ssh.sftp.SshjSftpClientAdapter(activeSshClient.newSFTPClient())
                    } catch (_: Exception) {
                        com.adamoutler.ssh.sftp.FakeSftpClientAdapter()
                    }
                } else {
                    com.adamoutler.ssh.sftp.FakeSftpClientAdapter()
                }
            }
            val sftpManager = remember(adapter) { com.adamoutler.ssh.sftp.SftpClientManager(adapter) }
            val sftpViewModel = remember(sftpManager) { com.adamoutler.ssh.sftp.ui.SftpBrowserViewModel(sftpManager) }

            com.adamoutler.ssh.sftp.ui.SftpBrowserScreen(
                profileNickname = nickname,
                viewModel = sftpViewModel,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }

    HostKeyPromptDialog()
}

@Composable
fun HostKeyPromptDialog() {
    val promptRequest by com.adamoutler.ssh.network.ConnectionStateRepository.promptRequest.collectAsState()

    promptRequest?.let { request ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { com.adamoutler.ssh.network.ConnectionStateRepository.resolvePrompt(false) },
            title = {
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Filled.Warning,
                        contentDescription = null,
                        tint = if (request.isKeyChanged) androidx.compose.material3.MaterialTheme.colorScheme.error else androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        modifier = androidx.compose.ui.Modifier.size(24.dp),
                    )
                    androidx.compose.material3.Text(
                        text = if (request.isKeyChanged) "Security Alert: Host Key Changed!" else "Unknown Host",
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    )
                }
            },
            text = {
                androidx.compose.foundation.layout.Column(
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                ) {
                    if (request.isKeyChanged) {
                        androidx.compose.material3.Text(
                            text = "WARNING: REMOTE HOST IDENTIFICATION HAS CHANGED! This could indicate a Man-in-the-Middle (MITM) attack intercepting your connection.",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        )

                        // High-friction comparison: Expected vs Received
                        androidx.compose.foundation.layout.Column(
                            modifier = androidx.compose.ui.Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    Color(0xFF38BDF8),
                                    RoundedCornerShape(6.dp),
                                )
                                .padding(8.dp),
                        ) {
                            androidx.compose.material3.Text(
                                "Expected Previous Key (Known Hosts)",
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = Color(0xFF38BDF8),
                            )
                            if (request.expectedFingerprint != null) {
                                androidx.compose.material3.Text(
                                    request.expectedFingerprint,
                                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                )
                            }
                            if (request.expectedRandomart != null) {
                                Spacer(modifier = androidx.compose.ui.Modifier.height(4.dp))
                                com.adamoutler.ssh.ui.components.RandomartBox(
                                    randomart = request.expectedRandomart,
                                    borderColor = Color(0xFF38BDF8),
                                    testTag = "ExpectedRandomartBox",
                                )
                            }
                        }

                        androidx.compose.foundation.layout.Column(
                            modifier = androidx.compose.ui.Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    androidx.compose.material3.MaterialTheme.colorScheme.error,
                                    RoundedCornerShape(6.dp),
                                )
                                .padding(8.dp),
                        ) {
                            androidx.compose.material3.Text(
                                "Received Host Key (Untrusted / Mismatch)",
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                            )
                            androidx.compose.material3.Text(
                                request.receivedFingerprint,
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            )
                            if (request.receivedRandomart != null) {
                                Spacer(modifier = androidx.compose.ui.Modifier.height(4.dp))
                                com.adamoutler.ssh.ui.components.RandomartBox(
                                    randomart = request.receivedRandomart,
                                    borderColor = androidx.compose.material3.MaterialTheme.colorScheme.error,
                                    testTag = "ReceivedRandomartBox",
                                )
                            }
                        }
                    } else {
                        androidx.compose.material3.Text(
                            text = "Establishing trust with ${request.hostname}. Verify the host key fingerprint matches your server.",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        )

                        androidx.compose.foundation.layout.Column(
                            modifier = androidx.compose.ui.Modifier
                                .fillMaxWidth()
                                .background(
                                    androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
                                    androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                                )
                                .padding(8.dp),
                        ) {
                            androidx.compose.material3.Text(
                                "Fingerprint: ${request.receivedFingerprint}",
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            )
                        }

                        if (request.receivedRandomart != null) {
                            com.adamoutler.ssh.ui.components.RandomartBox(
                                randomart = request.receivedRandomart,
                                title = "OpenSSH Visual Host Key Randomart",
                                testTag = "ReceivedRandomartBox",
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (request.isKeyChanged) {
                    com.adamoutler.ssh.ui.components.HoldToConfirmButton(
                        onConfirm = { com.adamoutler.ssh.network.ConnectionStateRepository.resolvePrompt(true) },
                        durationMillis = 2500,
                        text = "Hold 2.5s to Overwrite Host Key",
                        modifier = androidx.compose.ui.Modifier.defaultMinSize(minHeight = 48.dp),
                    )
                } else {
                    androidx.compose.material3.TextButton(
                        onClick = { com.adamoutler.ssh.network.ConnectionStateRepository.resolvePrompt(true) },
                    ) {
                        androidx.compose.material3.Text("Accept")
                    }
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { com.adamoutler.ssh.network.ConnectionStateRepository.resolvePrompt(false) },
                ) {
                    androidx.compose.material3.Text("Abort", color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                }
            },
        )
    }
}
