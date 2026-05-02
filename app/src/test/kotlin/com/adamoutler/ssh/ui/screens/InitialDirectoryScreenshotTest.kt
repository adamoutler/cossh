package com.adamoutler.ssh.ui.screens

import app.cash.paparazzi.DeviceConfig.Companion.PIXEL_5
import app.cash.paparazzi.Paparazzi
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.Protocol
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import org.junit.Rule
import org.junit.Test

class InitialDirectoryScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @Test
    fun initialDirectoryScreen() {
        paparazzi.snapshot(name = "SSH-132-ui") {
            CoSSHTheme {
                androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
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
                    initialDirectory = "/var/www/html",
                    onInitialDirectoryChange = {},
                    onSave = {},
                    onNavigateBack = {},
                    protocol = Protocol.SSH,
                    onProtocolChange = {}
                )
                }
            }
        }
    }
}
