package com.adamoutler.ssh.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
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

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val billingManager = remember { BillingManager(context) }
    val driveSyncManager = remember { DriveSyncManager(context) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.adamoutler.ssh.ui.events.UiEventBus.events.collect { event ->
            if (event is com.adamoutler.ssh.ui.events.UiEvent.Navigate) {
                navController.navigate(event.route) {
                    launchSingleTop = true
                }
            } else if (event is com.adamoutler.ssh.ui.events.UiEvent.NavigateUp) {
                navController.navigateUp()
            }
        }
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
                onSettingsRequested = { navController.navigate("settings") }
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
            TerminalScreen(
                profileId = profileId,
                sessionId = sessionId,
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
}