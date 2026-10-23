package com.adamoutler.ssh.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.adamoutler.ssh.network.ConnectionState
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import org.junit.Rule
import org.junit.Test

class TerminalMultiSessionTabsScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @Test
    fun testMultiSessionTabs_DualSession_ConnectedAndReconnecting() {
        val tabs = listOf(
            SessionTabItem(
                sessionId = "s1",
                profileId = "p1",
                nickname = "Prod Bastion",
                isTelnet = false,
                connectionState = ConnectionState.Connected,
                isSelected = true
            ),
            SessionTabItem(
                sessionId = "s2",
                profileId = "p2",
                nickname = "Staging Gateway",
                isTelnet = false,
                connectionState = ConnectionState.Reconnecting(attempt = 2, maxAttempts = 5),
                isSelected = false
            )
        )

        paparazzi.snapshot {
            CoSSHTheme(darkTheme = true) {
                Surface(color = Color(0xFF10192C)) {
                    Box(modifier = Modifier.padding(8.dp).fillMaxWidth()) {
                        TerminalSessionTabBar(
                            sessions = tabs,
                            onSelectSession = {},
                            onCloseSession = {},
                            onNewSession = {},
                            backgroundColor = Color(0xFF10192C)
                        )
                    }
                }
            }
        }
    }

    @Test
    fun testMultiSessionTabs_MultiProtocolWithErrors() {
        val tabs = listOf(
            SessionTabItem(
                sessionId = "s1",
                profileId = "p1",
                nickname = "App Server",
                isTelnet = false,
                connectionState = ConnectionState.Connected,
                isSelected = true
            ),
            SessionTabItem(
                sessionId = "s2",
                profileId = "p2",
                nickname = "Legacy Router",
                isTelnet = true,
                connectionState = ConnectionState.Connected,
                isSelected = false
            ),
            SessionTabItem(
                sessionId = "s3",
                profileId = "p3",
                nickname = "Dev Container",
                isTelnet = false,
                connectionState = ConnectionState.Error("Broken pipe"),
                isSelected = false
            )
        )

        paparazzi.snapshot {
            CoSSHTheme(darkTheme = true) {
                Surface(color = Color(0xFF10192C)) {
                    Box(modifier = Modifier.padding(8.dp).fillMaxWidth()) {
                        TerminalSessionTabBar(
                            sessions = tabs,
                            onSelectSession = {},
                            onCloseSession = {},
                            onNewSession = {},
                            backgroundColor = Color(0xFF10192C)
                        )
                    }
                }
            }
        }
    }
}
