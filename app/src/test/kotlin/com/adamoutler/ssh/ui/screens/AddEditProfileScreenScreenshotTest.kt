package com.adamoutler.ssh.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import org.junit.Rule
import org.junit.Test

class AddEditProfileScreenScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 4000),
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @Test
    fun telnetScreenWarningBanner() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AddEditProfileScreenContent(
                        profileId = null,
                        nickname = "Test Telnet Server",
                        onNicknameChange = {},
                        host = "test.example.com",
                        onHostChange = {},
                        port = "23",
                        onPortChange = {},
                        protocol = com.adamoutler.ssh.data.Protocol.TELNET,
                        onProtocolChange = {},
                        username = "admin",
                        onUsernameChange = {},
                        password = "password123",
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
                        portForwards = listOf(
                            com.adamoutler.ssh.data.PortForwardConfig(
                                type = com.adamoutler.ssh.data.PortForwardType.LOCAL,
                                localPort = 8080,
                                remoteHost = "localhost",
                                remotePort = 80,
                            ),
                            com.adamoutler.ssh.data.PortForwardConfig(
                                type = com.adamoutler.ssh.data.PortForwardType.REMOTE,
                                localPort = 9000,
                                remoteHost = "internal-db",
                                remotePort = 5432,
                            ),
                        ),
                        onPortForwardsChange = {},
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
                        defaultPasswordVisible = false,
                    )
                }
            }
        }
    }

    @Test
    fun portForwardTableScreen() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AddEditProfileScreenContent(
                        profileId = null,
                        nickname = "Test Server",
                        onNicknameChange = {},
                        host = "test.example.com",
                        onHostChange = {},
                        port = "22",
                        onPortChange = {},
                        protocol = com.adamoutler.ssh.data.Protocol.SSH,
                        onProtocolChange = {},
                        username = "ubuntu",
                        onUsernameChange = {},
                        password = "secure_password",
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
                        portForwards = listOf(
                            com.adamoutler.ssh.data.PortForwardConfig(
                                type = com.adamoutler.ssh.data.PortForwardType.LOCAL,
                                localPort = 8080,
                                remoteHost = "localhost",
                                remotePort = 80,
                            ),
                            com.adamoutler.ssh.data.PortForwardConfig(
                                type = com.adamoutler.ssh.data.PortForwardType.REMOTE,
                                localPort = 9000,
                                remoteHost = "internal-db",
                                remotePort = 5432,
                            ),
                        ),
                        onPortForwardsChange = {},
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
    fun defaultScreenKeyAuth() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AddEditProfileScreenContent(
                        profileId = null,
                        nickname = "Test Server",
                        onNicknameChange = {},
                        host = "test.example.com",
                        onHostChange = {},
                        port = "22",
                        onPortChange = {},
                        protocol = com.adamoutler.ssh.data.Protocol.SSH,
                        onProtocolChange = {},
                        username = "ubuntu",
                        onUsernameChange = {},
                        password = "",
                        onPasswordChange = {},
                        isPasswordLocked = false,
                        onPasswordLockedChange = {},
                        authType = AuthType.KEY,
                        onAuthTypeChange = {},
                        availableKeys = listOf("mock-key-1", "mock-key-2"),
                        keyReference = "mock-key-1",
                        onKeyReferenceChange = {},
                        identities = emptyList(),
                        identityId = null,
                        onIdentityChange = {},
                        onManageIdentities = {},
                        envVarsText = "",
                        onEnvVarsTextChange = {},
                        portForwards = listOf(
                            com.adamoutler.ssh.data.PortForwardConfig(
                                type = com.adamoutler.ssh.data.PortForwardType.LOCAL,
                                localPort = 8080,
                                remoteHost = "localhost",
                                remotePort = 80,
                            ),
                            com.adamoutler.ssh.data.PortForwardConfig(
                                type = com.adamoutler.ssh.data.PortForwardType.REMOTE,
                                localPort = 9000,
                                remoteHost = "internal-db",
                                remotePort = 5432,
                            ),
                        ),
                        onPortForwardsChange = {},
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
    fun portForwardDialogScreen() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AddEditProfileScreenContent(
                        profileId = null,
                        nickname = "Test Server",
                        onNicknameChange = {},
                        host = "test.example.com",
                        onHostChange = {},
                        port = "22",
                        onPortChange = {},
                        protocol = com.adamoutler.ssh.data.Protocol.SSH,
                        onProtocolChange = {},
                        username = "ubuntu",
                        onUsernameChange = {},
                        password = "secure_password",
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
                        portForwards = listOf(
                            com.adamoutler.ssh.data.PortForwardConfig(
                                type = com.adamoutler.ssh.data.PortForwardType.LOCAL,
                                localPort = 8080,
                                remoteHost = "localhost",
                                remotePort = 80,
                            ),
                            com.adamoutler.ssh.data.PortForwardConfig(
                                type = com.adamoutler.ssh.data.PortForwardType.REMOTE,
                                localPort = 9000,
                                remoteHost = "internal-db",
                                remotePort = 5432,
                            ),
                        ),
                        onPortForwardsChange = {},
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
                        defaultPasswordVisible = true,
                        defaultShowAddDialog = true,
                    )
                }
            }
        }
    }

    @Test
    fun useLocalDnsToggleScreen() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AddEditProfileScreenContent(
                        profileId = null,
                        nickname = "Local Server",
                        onNicknameChange = {},
                        host = "raspberrypi.local",
                        onHostChange = {},
                        port = "22",
                        onPortChange = {},
                        protocol = com.adamoutler.ssh.data.Protocol.SSH,
                        onProtocolChange = {},
                        username = "pi",
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
                        commandSnippets = emptyList(),
                        onCommandSnippetsChange = {},                        initialDirectory = "",
                        onInitialDirectoryChange = {},
                        terminalInputState = 0,
                        onTerminalInputStateChange = {},
                        keepScreenOnMode = com.adamoutler.ssh.data.KeepScreenOnMode.SYSTEM_DEFAULT,
                        onKeepScreenOnModeChange = {},
                        useLocalDns = true,
                        onUseLocalDnsChange = {},
                        onSave = {},
                        onNavigateBack = {},
                    )
                }
            }
        }
    }
}
