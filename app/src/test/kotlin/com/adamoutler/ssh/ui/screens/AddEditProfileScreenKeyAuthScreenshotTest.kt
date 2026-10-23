package com.adamoutler.ssh.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.IdentityProfile
import com.adamoutler.ssh.data.Protocol
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import org.junit.Rule
import org.junit.Test

class AddEditProfileScreenKeyAuthScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 2200),
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private val sampleIdentity = IdentityProfile(
        id = "ident-1",
        name = "Production Bastion Key",
        username = "ubuntu",
        authType = AuthType.KEY,
        publicKey = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGenKeyProd2026",
    )

    @Test
    fun addEditProfile_KeyAuth_IdentitySelected() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AddEditProfileScreenContent(
                        profileId = "prof-prod",
                        nickname = "Prod Bastion Host",
                        onNicknameChange = {},
                        host = "bastion.example.com",
                        onHostChange = {},
                        port = "22",
                        onPortChange = {},
                        protocol = Protocol.SSH,
                        onProtocolChange = {},
                        username = "",
                        onUsernameChange = {},
                        password = "",
                        onPasswordChange = {},
                        isPasswordLocked = false,
                        onPasswordLockedChange = {},
                        authType = AuthType.KEY,
                        onAuthTypeChange = {},
                        identities = listOf(sampleIdentity),
                        identityId = sampleIdentity.id,
                        onIdentityChange = {},
                        onManageIdentities = {},
                        envVarsText = "",
                        onEnvVarsTextChange = {},
                        portForwards = emptyList(),
                        onPortForwardsChange = {},
                        commandSnippets = emptyList(),
                        onCommandSnippetsChange = {},
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
            }
        }
    }

    @Test
    fun addEditProfile_KeyAuth_NoIdentitySelected() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AddEditProfileScreenContent(
                        profileId = null,
                        nickname = "Staging Host",
                        onNicknameChange = {},
                        host = "staging.internal",
                        onHostChange = {},
                        port = "22",
                        onPortChange = {},
                        protocol = Protocol.SSH,
                        onProtocolChange = {},
                        username = "",
                        onUsernameChange = {},
                        password = "",
                        onPasswordChange = {},
                        isPasswordLocked = false,
                        onPasswordLockedChange = {},
                        authType = AuthType.KEY,
                        onAuthTypeChange = {},
                        identities = listOf(sampleIdentity),
                        identityId = null,
                        onIdentityChange = {},
                        onManageIdentities = {},
                        envVarsText = "",
                        onEnvVarsTextChange = {},
                        portForwards = emptyList(),
                        onPortForwardsChange = {},
                        commandSnippets = emptyList(),
                        onCommandSnippetsChange = {},
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
            }
        }
    }

    @Test
    fun addEditProfile_KeyAuth_EmptyIdentitiesPrompt() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AddEditProfileScreenContent(
                        profileId = null,
                        nickname = "New Server",
                        onNicknameChange = {},
                        host = "10.0.0.1",
                        onHostChange = {},
                        port = "22",
                        onPortChange = {},
                        protocol = Protocol.SSH,
                        onProtocolChange = {},
                        username = "",
                        onUsernameChange = {},
                        password = "",
                        onPasswordChange = {},
                        isPasswordLocked = false,
                        onPasswordLockedChange = {},
                        authType = AuthType.KEY,
                        onAuthTypeChange = {},
                        identities = emptyList(),
                        identityId = null,
                        onIdentityChange = {},
                        onManageIdentities = {},
                        envVarsText = "",
                        onEnvVarsTextChange = {},
                        portForwards = emptyList(),
                        onPortForwardsChange = {},
                        commandSnippets = emptyList(),
                        onCommandSnippetsChange = {},
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
            }
        }
    }

    @Test
    fun addEditProfile_PasswordAuth() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AddEditProfileScreenContent(
                        profileId = null,
                        nickname = "Password Host",
                        onNicknameChange = {},
                        host = "192.168.1.1",
                        onHostChange = {},
                        port = "22",
                        onPortChange = {},
                        protocol = Protocol.SSH,
                        onProtocolChange = {},
                        username = "admin",
                        onUsernameChange = {},
                        password = "secure_password_123",
                        onPasswordChange = {},
                        isPasswordLocked = false,
                        onPasswordLockedChange = {},
                        authType = AuthType.PASSWORD,
                        onAuthTypeChange = {},
                        identities = listOf(sampleIdentity),
                        identityId = null,
                        onIdentityChange = {},
                        onManageIdentities = {},
                        envVarsText = "",
                        onEnvVarsTextChange = {},
                        portForwards = emptyList(),
                        onPortForwardsChange = {},
                        commandSnippets = emptyList(),
                        onCommandSnippetsChange = {},
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
            }
        }
    }
}
