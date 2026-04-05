package com.adamoutler.ssh.ui.keys

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import org.junit.Rule
import org.junit.Test

class KeyManagementScreenScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar"
    )

    @Test
    fun defaultScreen() {
        paparazzi.snapshot {
            CoSSHTheme {
                KeyManagementScreen()
            }
        }
    }

    @Test
    fun screenWithGeneratedKey() {
        val generatedKeys = listOf(
            SshKeyDisplay(
                id = "key-1",
                algorithm = "Ed25519",
                publicKeyBase64 = "AAAAC3NzaC1lZDI1NTE5AAAAIJGzP736vWqH6WbO0D9Z3Y5P5xZ1mP6B6+N4r5v8fR"
            )
        )
        paparazzi.snapshot {
            CoSSHTheme {
                KeyManagementScreen(initialKeys = generatedKeys)
            }
        }
    }
}
