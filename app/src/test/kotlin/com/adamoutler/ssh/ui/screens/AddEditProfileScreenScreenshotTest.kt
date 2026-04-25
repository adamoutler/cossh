package com.adamoutler.ssh.ui.screens

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import com.adamoutler.ssh.data.AuthType
import org.junit.Rule
import org.junit.Test
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

class AddEditProfileScreenScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar"
    )

    @Test
    fun telnetScreenWarningBanner() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
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
                                remotePort = 80
                            ),
                            com.adamoutler.ssh.data.PortForwardConfig(
                                type = com.adamoutler.ssh.data.PortForwardType.REMOTE,
                                localPort = 9000,
                                remoteHost = "internal-db",
                                remotePort = 5432
                            )
                        ),
                        onPortForwardsChange = {},
                        onSave = {},
                        onNavigateBack = {},
                        defaultPasswordVisible = false
                    )
                }
            }
        }
    }

    @Test
    fun defaultScreenPasswordAuth() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
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
                                remotePort = 80
                            ),
                            com.adamoutler.ssh.data.PortForwardConfig(
                                type = com.adamoutler.ssh.data.PortForwardType.REMOTE,
                                localPort = 9000,
                                remoteHost = "internal-db",
                                remotePort = 5432
                            )
                        ),
                        onPortForwardsChange = {},
                        onSave = {},
                        onNavigateBack = {}
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
                    color = MaterialTheme.colorScheme.background
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
                                remotePort = 80
                            ),
                            com.adamoutler.ssh.data.PortForwardConfig(
                                type = com.adamoutler.ssh.data.PortForwardType.REMOTE,
                                localPort = 9000,
                                remoteHost = "internal-db",
                                remotePort = 5432
                            )
                        ),
                        onPortForwardsChange = {},
                        onSave = {},
                        onNavigateBack = {}
                    )
                }
            }
        }
    }

    @Test
    fun toggledScreenPasswordAuth() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
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
                                remotePort = 80
                            ),
                            com.adamoutler.ssh.data.PortForwardConfig(
                                type = com.adamoutler.ssh.data.PortForwardType.REMOTE,
                                localPort = 9000,
                                remoteHost = "internal-db",
                                remotePort = 5432
                            )
                        ),
                        onPortForwardsChange = {},
                        onSave = {},
                        onNavigateBack = {},
                        defaultPasswordVisible = true,
                        defaultShowAddDialog = true
                    )
                }
            }
        }
    }
}
