package com.adamoutler.ssh.ui

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

class HostKeyPromptDialogScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar"
    )

    @Test
    fun tofuDialogScreen() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AlertDialog(
                        onDismissRequest = { },
                        title = { Text("Unknown Host") },
                        text = {
                            val msg = "The authenticity of host '192.168.1.100' can't be established.\nFingerprint: SHA256:abc123def456\nAre you sure you want to continue connecting?"
                            Text(msg)
                        },
                        confirmButton = {
                            TextButton(onClick = { }) { Text("Accept & Save") }
                        },
                        dismissButton = {
                            TextButton(onClick = { }) { Text("Decline") }
                        }
                    )
                }
            }
        }
    }
}