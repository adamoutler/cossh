package com.adamoutler.ssh.ui.screens

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import org.junit.Rule
import org.junit.Test
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

class SettingsScreenScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar"
    )

    @Test
    fun defaultScreen() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettingsScreenContent(
                        isCloudSyncEnabled = false,
                        isSyncing = false,
                        defaultGroupName = "Uncategorized",
                        isPassphraseSet = false,
                        onDefaultGroupNameChange = {},
                        onPurchaseCloudSync = {},
                        onAuthenticateGoogle = {},
                        onResetPassphrase = {},
                        onNavigateBack = {}                    )
                }
            }
        }
    }

    @Test
    fun screenWithRenamedGroup() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
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
                }
            }
        }
    }
}