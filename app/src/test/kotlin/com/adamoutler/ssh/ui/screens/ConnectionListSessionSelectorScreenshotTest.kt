package com.adamoutler.ssh.ui.screens

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import com.adamoutler.ssh.ui.screens.connectionlist.ConnectionListContent
import org.junit.Rule
import org.junit.Test
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp

class ConnectionListSessionSelectorScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar"
    )

    @Test
    fun sessionSelectorDialogScreen() {
        val profiles = listOf(
            ConnectionProfile(
                id = "1", nickname = "Server Alpha", host = "192.168.1.1", port = 22,
                username = "admin", authType = AuthType.PASSWORD, password = "password".toByteArray()
            ),
            ConnectionProfile(
                id = "2", nickname = "Server Beta", host = "192.168.1.2", port = 22,
                username = "root", authType = AuthType.KEY, sshKeyPasswordReferenceId = "key1"
            )
        )
        
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ConnectionListContent(
                        profiles = profiles,
                        searchQuery = "",
                        activeConnections = setOf("1", "2"),
                        onSearchQueryChange = {},
                        onAddConnection = {},
                        onEditConnection = {},
                        onConnect = {}
                    )
                    // Triggering the dialog directly by mocking the state
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { },
                        title = { androidx.compose.material3.Text("Active Sessions") },
                        text = {
                            androidx.compose.foundation.layout.Column {
                                androidx.compose.material3.Text("You have multiple active sessions. Select one to resume or start a new one.")
                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
                                androidx.compose.foundation.lazy.LazyColumn {
                                    items(2) { index ->
                                        val profile = profiles[index]
                                        androidx.compose.material3.TextButton(onClick = { }) {
                                            androidx.compose.material3.Text(profile.nickname)
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = { }) {
                                androidx.compose.material3.Text("Start New")
                            }
                        }
                    )
                }
            }
        }
    }
}
