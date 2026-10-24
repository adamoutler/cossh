package com.adamoutler.ssh.ui.screens.connectionlist.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.Protocol
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import org.junit.Rule
import org.junit.Test

class ShortcutActionScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private val sampleProfile = ConnectionProfile(
        id = "prof-jump-bastion",
        nickname = "Production Bastion",
        host = "bastion.internal.cloud",
        port = 22,
        protocol = Protocol.SSH,
        username = "admin",
    )

    @Test
    fun testConnectionItem_WithShortcutMenuOpen() {
        paparazzi.snapshot {
            CoSSHTheme(darkTheme = true) {
                Surface {
                    Box(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        ConnectionItem(
                            profile = sampleProfile,
                            activeCount = 1,
                            isReordering = false,
                            onClick = {},
                            onEdit = {},
                            onAddToHomeScreen = {},
                            initialMenuExpanded = true,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun testConnectionItem_DefaultCard() {
        paparazzi.snapshot {
            CoSSHTheme(darkTheme = false) {
                Surface {
                    Box(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        ConnectionItem(
                            profile = sampleProfile,
                            activeCount = 0,
                            isReordering = false,
                            onClick = {},
                            onEdit = {},
                            onAddToHomeScreen = {},
                            initialMenuExpanded = false,
                        )
                    }
                }
            }
        }
    }
}
