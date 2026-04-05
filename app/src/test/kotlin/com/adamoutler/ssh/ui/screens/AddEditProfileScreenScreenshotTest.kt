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
                        username = "ubuntu",
                        onUsernameChange = {},
                        password = "secure_password",
                        onPasswordChange = {},
                        authType = AuthType.PASSWORD,
                        onAuthTypeChange = {},
                        keyReference = "",
                        onKeyReferenceChange = {},
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
                        username = "ubuntu",
                        onUsernameChange = {},
                        password = "",
                        onPasswordChange = {},
                        authType = AuthType.KEY,
                        onAuthTypeChange = {},
                        keyReference = "mock-key-1",
                        onKeyReferenceChange = {},
                        onSave = {},
                        onNavigateBack = {}
                    )
                }
            }
        }
    }
}
