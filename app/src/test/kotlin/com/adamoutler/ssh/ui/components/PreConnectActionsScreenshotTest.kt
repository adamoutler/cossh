package com.adamoutler.ssh.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.adamoutler.ssh.data.KnockProtocol
import com.adamoutler.ssh.data.PortKnockStep
import com.adamoutler.ssh.data.PortKnockingConfig
import com.adamoutler.ssh.data.WolConfig
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import org.junit.Rule
import org.junit.Test

class PreConnectActionsScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @Test
    fun testPreConnectActions_ExpandedWithWolAndKnocking() {
        paparazzi.snapshot {
            CoSSHTheme(darkTheme = true) {
                Surface {
                    Box(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        PreConnectActionsCard(
                            wolConfig = WolConfig(
                                macAddress = "00:11:22:33:44:55",
                                broadcastIp = "255.255.255.255",
                                port = 9,
                                postWakeDelayMs = 2000L,
                            ),
                            onWolConfigChange = {},
                            portKnockingConfig = PortKnockingConfig(
                                steps = listOf(
                                    PortKnockStep(7000, KnockProtocol.TCP),
                                    PortKnockStep(8000, KnockProtocol.UDP),
                                    PortKnockStep(9000, KnockProtocol.TCP),
                                ),
                            ),
                            onPortKnockingConfigChange = {},
                            initiallyExpanded = true,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun testPreConnectActions_Collapsed() {
        paparazzi.snapshot {
            CoSSHTheme(darkTheme = false) {
                Surface {
                    Box(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        PreConnectActionsCard(
                            wolConfig = null,
                            onWolConfigChange = {},
                            portKnockingConfig = null,
                            onPortKnockingConfigChange = {},
                            initiallyExpanded = false,
                        )
                    }
                }
            }
        }
    }
}
