package com.adamoutler.ssh.ui.navigation

import androidx.compose.runtime.Composable
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

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "connectionList") {
        composable("connectionList") {
            ConnectionListScreen(
                onAddConnection = { navController.navigate("addEditProfile") },
                onEditConnection = { profileId -> navController.navigate("addEditProfile?profileId=$profileId") },
                onConnect = { profileId -> navController.navigate("terminal?profileId=$profileId") }
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
            route = "terminal?profileId={profileId}",
            arguments = listOf(navArgument("profileId") {
                type = NavType.StringType
            })
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getString("profileId") ?: ""
            TerminalScreen(
                profileId = profileId,
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