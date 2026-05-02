package com.adamoutler.ssh.ui.screens.identity

import app.cash.paparazzi.DeviceConfig.Companion.PIXEL_5
import app.cash.paparazzi.Paparazzi
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.Protocol
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import org.junit.Rule
import org.junit.Test

class InjectKeyDialogScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private val profiles = listOf(
        ConnectionProfile(
            id = "1",
            nickname = "My Production Server",
            host = "192.168.1.100",
            port = 22,
            protocol = Protocol.SSH,
            username = "admin",
            authType = AuthType.PASSWORD,
        ),
        ConnectionProfile(
            id = "2",
            nickname = "Router",
            host = "10.0.0.1",
            port = 22,
            protocol = Protocol.SSH,
            username = "root",
            authType = AuthType.PASSWORD,
        )
    )

    @Test
    fun placeholderScreen() {
        paparazzi.snapshot(name = "inject-key-placeholder") {
            CoSSHTheme {
                androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                    InjectKeyDialog(
                        onDismiss = {},
                        onInject = { _, _, _ -> },
                        profiles = profiles,
                    )
                }
            }
        }
    }
}
