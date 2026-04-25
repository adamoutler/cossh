package com.adamoutler.ssh.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import org.junit.Rule
import org.junit.Test
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

class TerminalScreenDialogScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar"
    )

    @Test
    fun sessionDisconnectedDialogScreen() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AlertDialog(
                        onDismissRequest = { },
                        title = { Text("Session Disconnected") },
                        text = { Text("The SSH session has ended or the connection was lost.") },
                        confirmButton = {
                            TextButton(onClick = { }) { Text("OK") }
                        }
                    )
                }
            }
        }
    }

    @Test
    fun keepAliveDialogScreen() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AlertDialog(
                        onDismissRequest = { },
                        title = { Text("Keep Session Alive?") },
                        text = { Text("Do you want to keep this SSH session running in the background or terminate it?") },
                        confirmButton = {
                            TextButton(onClick = { }) { Text("Keep Alive") }
                        },
                        dismissButton = {
                            TextButton(onClick = { }) { Text("Terminate") }
                        }
                    )
                }
            }
        }
    }

    @Test
    fun connectionFailedDialogScreen() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AlertDialog(
                        onDismissRequest = { },
                        title = { Text("Connection Failed") },
                        text = { Text("Error: Authentication exhausted. Please check your credentials.") },
                        confirmButton = {
                            TextButton(onClick = { }) { Text("OK") }
                        }
                    )
                }
            }
        }
    }
}
