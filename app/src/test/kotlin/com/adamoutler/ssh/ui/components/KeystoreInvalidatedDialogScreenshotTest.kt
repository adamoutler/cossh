package com.adamoutler.ssh.ui.components

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import org.junit.Rule
import org.junit.Test

class KeystoreInvalidatedDialogScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar"
    )

    @Test
    fun keystoreInvalidatedDialogScreen() {
        paparazzi.snapshot {
            CoSSHTheme {
                KeystoreInvalidatedDialog(
                    onConfirmReset = {},
                    onDismissApp = {}
                )
            }
        }
    }
}