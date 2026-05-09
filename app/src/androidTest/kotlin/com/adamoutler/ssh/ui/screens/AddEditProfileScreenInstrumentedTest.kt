package com.adamoutler.ssh.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.Protocol
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddEditProfileScreenInstrumentedTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test(timeout = 300000L)
    fun saveButtonInTopAppBarTriggersSave() {
        var onSaveCalled = false

        composeTestRule.setContent {
            AddEditProfileScreenContent(
                profileId = null,
                nickname = "",
                onNicknameChange = {},
                host = "",
                onHostChange = {},
                port = "22",
                onPortChange = {},
                username = "",
                onUsernameChange = {},
                password = "",
                onPasswordChange = {},
                authType = AuthType.PASSWORD,
                onAuthTypeChange = {},
                availableKeys = emptyList(),
                keyReference = "",
                onKeyReferenceChange = {},
                identities = emptyList(),
                identityId = null,
                onIdentityChange = {},
                onManageIdentities = {},
                onSave = {
                    onSaveCalled = true
                },
                onNavigateBack = {},
                protocol = Protocol.SSH,
                onProtocolChange = {},
                isPasswordLocked = false,
                onPasswordLockedChange = {},
                envVarsText = "",
                onEnvVarsTextChange = {},
                portForwards = emptyList(),
                onPortForwardsChange = {},
                initialDirectory = "",
                onInitialDirectoryChange = {},
                terminalInputState = 0,
                onTerminalInputStateChange = {},
                keepScreenOnMode = com.adamoutler.ssh.data.KeepScreenOnMode.SYSTEM_DEFAULT,
                onKeepScreenOnModeChange = {},
            )
        }

        // Click the save button in the Top App Bar
        composeTestRule.onNodeWithContentDescription("Save Profile").performClick()

        // Assert that onSave was called
        assertTrue(onSaveCalled)
    }
}
