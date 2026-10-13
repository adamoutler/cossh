package com.adamoutler.ssh.ui.screens

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.crypto.IdentityStorageManager
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.CommandSnippet
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CommandSnippetProfileStateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun createViewModel(): Triple<AddEditProfileViewModel, SecurityStorageManager, IdentityStorageManager> {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = app.getSharedPreferences("test_prefs_" + java.util.UUID.randomUUID(), android.content.Context.MODE_PRIVATE)
        val storageManager = SecurityStorageManager(app, prefs)
        val identityStorageManager = IdentityStorageManager(app, prefs)
        val viewModel = AddEditProfileViewModel(app, storageManager, identityStorageManager)
        return Triple(viewModel, storageManager, identityStorageManager)
    }

    @Test
    fun testViewModelSnippetDeletionRemovesFromStateAndStorage() {
        val (viewModel, storageManager, _) = createViewModel()

        val snippet1 = CommandSnippet("s1", "Restart Nginx", "systemctl restart nginx", true, false)
        val snippet2 = CommandSnippet("s2", "Check Uptime", "uptime", false, false)
        val snippet3 = CommandSnippet("s3", "Reboot Server", "reboot", true, true)

        val profile = ConnectionProfile(
            id = "profile-del-test",
            nickname = "Delete Test Server",
            host = "192.168.1.50",
            commandSnippets = listOf(snippet1, snippet2, snippet3),
        )
        storageManager.saveProfile(profile)

        // Load into ViewModel
        viewModel.loadProfileIfNeeded("profile-del-test")
        assertEquals(3, viewModel.uiState.value.commandSnippets.size)

        // Delete snippet 2 from state
        val updatedList = viewModel.uiState.value.commandSnippets.toMutableList().apply {
            remove(snippet2)
        }
        viewModel.updateState { it.copy(commandSnippets = updatedList) }

        assertEquals(2, viewModel.uiState.value.commandSnippets.size)
        assertFalse(viewModel.uiState.value.commandSnippets.any { snippet -> snippet.id == "s2" })

        // Save back to storage
        val saveResult = viewModel.saveProfile("profile-del-test")
        assertTrue("Save profile should succeed", saveResult)

        // Verify persisted state
        val persisted = storageManager.getProfile("profile-del-test")
        assertNotNull(persisted)
        val persistedSnippets = persisted!!.commandSnippets
        assertEquals(2, persistedSnippets.size)
        assertEquals("s1", persistedSnippets[0].id)
        assertEquals("s3", persistedSnippets[1].id)
        assertFalse("Deleted snippet must not be persisted in storage", persistedSnippets.any { snippet -> snippet.id == "s2" })
    }

    @Test
    fun testDeleteAllSnippetsFromStateAndStorage() {
        val (viewModel, storageManager, _) = createViewModel()

        val snippet1 = CommandSnippet("s1", "Single Snippet", "echo hello", true, false)
        val profile = ConnectionProfile(
            id = "profile-clear-snippets",
            nickname = "Clear Server",
            host = "10.0.0.1",
            commandSnippets = listOf(snippet1),
        )
        storageManager.saveProfile(profile)

        viewModel.loadProfileIfNeeded("profile-clear-snippets")
        assertEquals(1, viewModel.uiState.value.commandSnippets.size)

        // Clear all snippets
        viewModel.updateState { it.copy(commandSnippets = emptyList()) }
        assertEquals(0, viewModel.uiState.value.commandSnippets.size)

        viewModel.saveProfile("profile-clear-snippets")

        val persisted = storageManager.getProfile("profile-clear-snippets")
        assertNotNull(persisted)
        assertTrue("All snippets should be cleared from profile", persisted!!.commandSnippets.isEmpty())
    }

    @Test
    fun testComposeUIDeleteSnippetButtonRemovesSnippetViaCallback() {
        val snippet1 = CommandSnippet("s-del-1", "Snippet To Delete", "rm /tmp/test", true, false)
        val snippet2 = CommandSnippet("s-del-2", "Keep Snippet", "ls -l", false, false)

        var emittedSnippets: List<CommandSnippet>? = null

        composeTestRule.setContent {
            AddEditProfileScreenContent(
                profileId = "profile-ui-test",
                nickname = "UI Test Server",
                onNicknameChange = {},
                host = "localhost",
                onHostChange = {},
                port = "22",
                onPortChange = {},
                protocol = Protocol.SSH,
                onProtocolChange = {},
                username = "admin",
                onUsernameChange = {},
                password = "",
                onPasswordChange = {},
                isPasswordLocked = false,
                onPasswordLockedChange = {},
                authType = AuthType.PASSWORD,
                onAuthTypeChange = {},
                availableKeys = emptyList(),
                keyReference = "",
                onKeyReferenceChange = {},
                identities = emptyList(),
                identityId = null,
                onIdentityChange = {},
                onManageIdentities = {},
                envVarsText = "",
                onEnvVarsTextChange = {},
                portForwards = emptyList(),
                onPortForwardsChange = {},
                commandSnippets = listOf(snippet1, snippet2),
                onCommandSnippetsChange = { emittedSnippets = it },
                initialDirectory = "",
                onInitialDirectoryChange = {},
                terminalInputState = 0,
                onTerminalInputStateChange = {},
                keepScreenOnMode = com.adamoutler.ssh.data.KeepScreenOnMode.SYSTEM_DEFAULT,
                onKeepScreenOnModeChange = {},
                useLocalDns = false,
                onUseLocalDnsChange = {},
                onSave = {},
                onNavigateBack = {},
            )
        }

        composeTestRule.waitForIdle()

        // Find the first "Delete Snippet" icon button corresponding to snippet1 and click it
        composeTestRule.onAllNodesWithContentDescription("Delete Snippet").onFirst().performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertNotNull("onCommandSnippetsChange callback must be triggered on delete click", emittedSnippets)
        assertEquals(1, emittedSnippets!!.size)
        assertEquals("s-del-2", emittedSnippets!![0].id)
        assertEquals("Keep Snippet", emittedSnippets!![0].name)
        assertFalse("Snippet 1 must have been deleted from list", emittedSnippets!!.any { snippet -> snippet.id == "s-del-1" })
    }
}
