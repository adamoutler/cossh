package com.adamoutler.ssh.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import org.junit.Rule
import org.junit.Test

class TerminalScreenDialogScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @Test
    fun sessionDisconnectedDialogScreen() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    SessionDisconnectedDialog(onDismiss = {})
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
                    color = MaterialTheme.colorScheme.background,
                ) {
                    KeepAliveDialog(
                        onDismiss = {},
                        onKeepAlive = {},
                        onTerminate = {},
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
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ConnectionFailedDialog(
                        errorMessage = "Connection failed",
                        onDismiss = {},
                    )
                }
            }
        }
    }

    @Test
    fun terminateConfirmDialogScreen() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    TerminateConfirmDialog(
                        onDismiss = {},
                        onTerminate = {},
                    )
                }
            }
        }
    }

    @Test
    fun authPromptDialogScreen() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AuthPromptDialog(
                        requireUsername = true,
                        isRetry = false,
                        onConfirm = {},
                        onDismiss = {},
                    )
                }
            }
        }
    }

    @Test
    fun authPromptDialogScreen_Retry() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AuthPromptDialog(
                        requireUsername = false,
                        isRetry = true,
                        onConfirm = {},
                        onDismiss = {},
                    )
                }
            }
        }
    }
}
