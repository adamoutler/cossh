package com.adamoutler.ssh.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.adamoutler.ssh.ui.screens.ConnectionListScreen
import com.adamoutler.ssh.ui.screens.AddEditProfileScreen
import com.adamoutler.ssh.ui.keys.KeyManagementScreen
import com.adamoutler.ssh.ui.components.TerminalScreen
import com.adamoutler.ssh.ui.screens.identity.IdentityListScreen
import com.adamoutler.ssh.ui.screens.identity.AddEditIdentityScreen
import com.adamoutler.ssh.billing.BillingManager
import com.adamoutler.ssh.sync.DriveSyncManager
import com.adamoutler.ssh.ui.screens.SettingsScreen
import com.adamoutler.ssh.ui.components.KeystoreInvalidatedDialog
import com.adamoutler.ssh.crypto.SecurityStorageManager

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val activity = context as? androidx.activity.ComponentActivity
    val billingManager = remember { BillingManager(context) }
    val driveSyncManager = remember { DriveSyncManager(context) }
    val securityStorageManager = remember { SecurityStorageManager(context) }

    var showKeystoreDialog by remember { mutableStateOf(false) }

    androidx.compose.runtime.DisposableEffect(activity) {
        val listener = androidx.core.util.Consumer<android.content.Intent> { intent ->
            if (intent.hasExtra(com.adamoutler.ssh.network.SshService.EXTRA_PROFILE_ID)) {
                val profileId = intent.getStringExtra(com.adamoutler.ssh.network.SshService.EXTRA_PROFILE_ID)
                val sessionId = intent.getStringExtra(com.adamoutler.ssh.network.SshService.EXTRA_SESSION_ID)
                if (profileId != null && sessionId != null) {
                    navController.navigate("terminal?profileId=$profileId&sessionId=$sessionId") { 
                        popUpTo("connectionList") { inclusive = false }
                        launchSingleTop = true 
                    }
                }
            }
        }
        activity?.addOnNewIntentListener(listener)
        onDispose {
            activity?.removeOnNewIntentListener(listener)
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        val intent = activity?.intent
        if (intent?.hasExtra(com.adamoutler.ssh.network.SshService.EXTRA_PROFILE_ID) == true) {
            val profileId = intent.getStringExtra(com.adamoutler.ssh.network.SshService.EXTRA_PROFILE_ID)
            val sessionId = intent.getStringExtra(com.adamoutler.ssh.network.SshService.EXTRA_SESSION_ID)
            activity.intent = android.content.Intent() // Clear the intent so it isn't re-processed
            if (profileId != null && sessionId != null) {
                navController.navigate("terminal?profileId=$profileId&sessionId=$sessionId") { 
                    popUpTo("connectionList") { inclusive = false }
                    launchSingleTop = true 
                }
            }
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
            }
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
                onManageIdentitiesRequested = { navController.navigate("identityList") }
            )
        }
        composable("settings") {
            SettingsScreen(
                billingManager = billingManager,
                driveSyncManager = driveSyncManager,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "addEditProfile?profileId={profileId}",
            arguments = listOf(navArgument("profileId") { 
                type = NavType.StringType 
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getString("profileId")
            AddEditProfileScreen(
                profileId = profileId,
                onNavigateBack = { navController.popBackStack() },
                onManageIdentities = { navController.navigate("identityList") }
            )
        }
        composable(
            route = "terminal?profileId={profileId}&sessionId={sessionId}",
            arguments = listOf(
                navArgument("profileId") { type = NavType.StringType },
                navArgument("sessionId") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getString("profileId") ?: ""
            val sessionId = backStackEntry.arguments?.getString("sessionId")
            
            val activity = LocalContext.current as androidx.activity.ComponentActivity
            val terminalViewModel: com.adamoutler.ssh.ui.screens.TerminalViewModel = androidx.lifecycle.viewmodel.compose.viewModel(activity)
            
            TerminalScreen(
                profileId = profileId,
                sessionId = sessionId,
                terminalViewModel = terminalViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("identityList") {
            IdentityListScreen(
                onAddIdentity = { navController.navigate("addEditIdentity") },
                onEditIdentity = { identityId -> navController.navigate("addEditIdentity?identityId=$identityId") },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "addEditIdentity?identityId={identityId}",
            arguments = listOf(navArgument("identityId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val identityId = backStackEntry.arguments?.getString("identityId")
            AddEditIdentityScreen(
                identityId = identityId,
                onBack = { navController.popBackStack() }
            )
        }
        composable("keyManagement") {
            KeyManagementScreen()
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
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Filled.Warning,
                        contentDescription = "Warning",
                        tint = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        modifier = androidx.compose.ui.Modifier.padding(end = 8.dp)
                    )
                    androidx.compose.material3.Text(
                        if (request.isKeyChanged) "Security Alert: Host Key Changed" else "Unknown Host",
                        style = androidx.compose.material3.MaterialTheme.typography.titleLarge
                    )
                }
            },
            text = {
                androidx.compose.foundation.layout.Column {
                    val msg = if (request.isKeyChanged) {
                        "The identification key for this server has changed. This could mean the server was rebuilt, or someone is intercepting your connection (Man-in-the-Middle attack)."
                    } else {
                        "The authenticity of host '${request.hostname}' can't be established."
                    }
                    androidx.compose.material3.Text(msg, modifier = androidx.compose.ui.Modifier.padding(bottom = 16.dp))

                    androidx.compose.foundation.layout.Column(
                        modifier = androidx.compose.ui.Modifier
                            .fillMaxWidth()
                            .background(
                                color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp)
                    ) {
                        if (request.expectedFingerprint != null) {
                            androidx.compose.material3.Text(
                                "Expected: ${request.expectedFingerprint}",
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                            )
                            androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(8.dp))
                        }
                        androidx.compose.material3.Text(
                            "Received: ${request.receivedFingerprint}",
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                if (request.isKeyChanged) {
                    com.adamoutler.ssh.ui.components.HoldToConfirmButton(
                        onConfirm = { com.adamoutler.ssh.network.ConnectionStateRepository.resolvePrompt(true) },
                        text = "Hold to Accept Risk",
                        modifier = androidx.compose.ui.Modifier.padding(end = 8.dp)
                    )
                } else {
                    androidx.compose.material3.TextButton(onClick = { com.adamoutler.ssh.network.ConnectionStateRepository.resolvePrompt(true) }) {
                        androidx.compose.material3.Text("Accept & Save")
                    }
                }
            },
            dismissButton = {
                androidx.compose.material3.Button(onClick = { com.adamoutler.ssh.network.ConnectionStateRepository.resolvePrompt(false) }) {
                    androidx.compose.material3.Text("Abort Connection")
                }
            }
        )
    }
}