package com.adamoutler.ssh.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig.Companion.PIXEL_5
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class SettingsScreenSyncPassphraseScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar",
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
                        onNavigateBack = {},
                    )
                    SyncPassphraseDialog(
                        passphrase = "",
                        onPassphraseChange = {},
                        onDismiss = {},
                        onConfirm = {},
                    )
                }
            }
        }
    }
}
