package com.adamoutler.ssh.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.ui.screens.connectionlist.ConnectionListContent
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectionListClickTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testReorderModeDisablesClick() {
        var connectedId: String? = null
        val profile = ConnectionProfile(id = "test-1", nickname = "Test Server", host = "127.0.0.1", port = 22, authType = AuthType.PASSWORD, username = "test")

        composeTestRule.setContent {
            ConnectionListContent(
                flatItems = listOf(com.adamoutler.ssh.ui.screens.ConnectionListItem.Profile(profile)),
                groupedProfiles = mapOf("Default" to listOf(profile)),
                activeConnectionCounts = emptyMap(),
                searchQuery = "",
                onSearchQueryChange = {},
                onAddConnection = {},
                onEditConnection = {},
                onDeleteConnection = {},
                onConnect = { connectedId = it },
                isReorderingPreview = true, // Enable reorder mode immediately
            )
        }

        // Tap the connection
        composeTestRule.onNodeWithText("Test Server").performClick()

        // Assert it did not connect
        assertTrue("onConnect should not be called in reorder mode", connectedId == null)
        println("Reorder mode correctly ignored click.")
    }

    @Test
    fun testNormalModeEnablesClick() {
        var connectedId: String? = null
        val profile = ConnectionProfile(id = "test-1", nickname = "Test Server", host = "127.0.0.1", port = 22, authType = AuthType.PASSWORD, username = "test")

        composeTestRule.setContent {
            ConnectionListContent(
                flatItems = listOf(com.adamoutler.ssh.ui.screens.ConnectionListItem.Profile(profile)),
                groupedProfiles = mapOf("Default" to listOf(profile)),
                activeConnectionCounts = emptyMap(),
                searchQuery = "",
                onSearchQueryChange = {},
                onAddConnection = {},
                onEditConnection = {},
                onDeleteConnection = {},
                onConnect = { connectedId = it },
                isReorderingPreview = false, // Normal mode
            )
        }

        // Tap the connection
        composeTestRule.onNodeWithText("Test Server").performClick()

        // Assert it connected
        assertTrue("onConnect should be called in normal mode", connectedId == "test-1")
        println("Normal mode correctly triggered connection.")
    }
}
