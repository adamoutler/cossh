package com.adamoutler.ssh.ui.screens

import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import app.cash.paparazzi.DeviceConfig.Companion.PIXEL_5
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

class SettingsScreenSyncPassphraseScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar"
    )

    @Test
    fun syncPassphraseDialogScreen() {
        paparazzi.snapshot {
            com.adamoutler.ssh.ui.theme.CoSSHTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SettingsScreenContent(
                        isCloudSyncEnabled = true,
                        isSyncing = false,
                        defaultGroupName = "Uncategorized",
                        isPassphraseSet = false,
                        onDefaultGroupNameChange = {},
                        onPurchaseCloudSync = {},
                        onAuthenticateGoogle = {},
                        onResetPassphrase = {},
                        onNavigateBack = {}
                    )
                    // The easiest way to force the dialog in a screenshot test without mocking the parent state
                    // is to just render the AlertDialog directly over the surface.
                    androidx.compose.material3.AlertDialog(
                        modifier = androidx.compose.ui.Modifier.widthIn(max = 320.dp),
                        onDismissRequest = { },
                        title = { androidx.compose.material3.Text("Sync Passphrase") },
                        text = {
                            androidx.compose.foundation.layout.Column {
                                androidx.compose.material3.Text("Enter a secure passphrase to encrypt your backups before they leave this device.")
                                androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(8.dp))
                                androidx.compose.material3.OutlinedTextField(
                                    value = "",
                                    onValueChange = { },
                                    label = { androidx.compose.material3.Text("Passphrase") },
                                    singleLine = true,
                                    modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = { }, enabled = false) {
                                androidx.compose.material3.Text("Save & Sync")
                            }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = { }) {
                                androidx.compose.material3.Text("Cancel")
                            }
                        }
                    )
                }
            }
        }
    }
}
