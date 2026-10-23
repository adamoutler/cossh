package com.adamoutler.ssh.ui.screens.connectionlist.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.adamoutler.ssh.data.IdentityProfile
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import org.junit.Rule
import org.junit.Test

class QuickConnectBottomSheetScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @Test
    fun quickConnectDefaultState() {
        val mockIdentities = listOf(
            IdentityProfile(
                id = "id_1",
                name = "Production RSA Key",
                username = "admin",
            ),
        )

        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    QuickConnectContent(
                        identities = mockIdentities,
                        initialAddress = "admin@192.168.1.100",
                        initialPort = "22",
                    )
                }
            }
        }
    }

    @Test
    fun quickConnectInvalidState() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    QuickConnectContent(
                        initialAddress = "invalid hostname; rm -rf /",
                        initialErrorMessage = "Hostname contains invalid characters or injection attempts",
                    )
                }
            }
        }
    }
}
