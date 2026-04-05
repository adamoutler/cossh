package com.adamoutler.ssh.ui.screens

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.AuthType
import org.junit.Rule
import org.junit.Test
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

class ConnectionListScreenScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar"
    )

    @Test
    fun defaultScreen() {
        val mockProfiles = listOf(
            ConnectionProfile(
                id = "1",
                nickname = "Production Server",
                host = "192.168.1.10",
                port = 22,
                username = "admin",
                authType = AuthType.KEY,
                sshKeyPasswordReferenceId = "mock-key-1"
            ),
            ConnectionProfile(
                id = "2",
                nickname = "Staging Server",
                host = "staging.example.com",
                port = 2222,
                username = "deploy",
                authType = AuthType.PASSWORD,
                password = "password".toByteArray()
            )
        )
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ConnectionListScreenContent(
                        profiles = mockProfiles,
                        searchQuery = "",
                        onSearchQueryChange = {},
                        onAddConnection = {},
                        onEditConnection = {}
                    )
                }
            }
        }
    }

    @Test
    fun menuExpandedScreen() {
        val mockProfiles = listOf(
            ConnectionProfile(
                id = "1",
                nickname = "Production Server",
                host = "192.168.1.10",
                port = 22,
                username = "admin",
                authType = AuthType.KEY,
                sshKeyPasswordReferenceId = "mock-key-1"
            )
        )
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ConnectionListScreenContent(
                        profiles = mockProfiles,
                        searchQuery = "",
                        onSearchQueryChange = {},
                        onAddConnection = {},
                        onEditConnection = {},
                        initialMenuExpanded = true
                    )
                }
            }
        }
    }
}
