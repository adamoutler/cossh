package com.adamoutler.ssh.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import org.junit.Rule
import org.junit.Test

class PasswordPromptDialogScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @Test
    fun passwordPromptDialogScreen() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AlertDialog(
                        onDismissRequest = { },
                        title = { Text("Password Required") },
                        text = {
                            Column {
                                Text("Please enter the password for the connection.")
                                Spacer(modifier = Modifier.height(16.dp))
                                com.adamoutler.ssh.ui.components.SecurePasswordEditText(
                                    hint = "Password",
                                    onPasswordChanged = {},
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { }) { Text("Connect") }
                        },
                        dismissButton = {
                            TextButton(onClick = { }) { Text("Cancel") }
                        },
                    )
                }
            }
        }
    }
}
