package com.adamoutler.ssh.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.adamoutler.ssh.network.ConnectionLogEntry
import com.adamoutler.ssh.network.ConnectionLogLevel
import com.adamoutler.ssh.network.ConnectionMilestone
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import org.junit.Rule
import org.junit.Test

class ConnectingLogOverlayScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private val sampleLogs = listOf(
        ConnectionLogEntry(
            milestone = ConnectionMilestone.DNS,
            message = "Resolving bastion.corp.internal via local DNS...",
            level = ConnectionLogLevel.INFO,
        ),
        ConnectionLogEntry(
            milestone = ConnectionMilestone.DNS,
            message = "Resolved to 10.240.0.1 (12ms)",
            level = ConnectionLogLevel.INFO,
        ),
        ConnectionLogEntry(
            milestone = ConnectionMilestone.TCP,
            message = "Connecting TCP socket to 10.240.0.1:22...",
            level = ConnectionLogLevel.INFO,
        ),
        ConnectionLogEntry(
            milestone = ConnectionMilestone.TCP,
            message = "TCP handshake completed (syn-ack 18ms)",
            level = ConnectionLogLevel.INFO,
        ),
        ConnectionLogEntry(
            milestone = ConnectionMilestone.SSH,
            message = "Server banner: SSH-2.0-OpenSSH_9.6p1 Ubuntu-3ubuntu13",
            level = ConnectionLogLevel.INFO,
        ),
        ConnectionLogEntry(
            milestone = ConnectionMilestone.KEX,
            message = "KEX negotiated: curve25519-sha256, aes256-gcm@openssh.com",
            level = ConnectionLogLevel.INFO,
        ),
        ConnectionLogEntry(
            milestone = ConnectionMilestone.HOSTKEY,
            message = "Host key SHA256:7D8a9F0e2Bc... verified in known_hosts",
            level = ConnectionLogLevel.INFO,
        ),
        ConnectionLogEntry(
            milestone = ConnectionMilestone.AUTH,
            message = "Attempting publickey auth (Titan M2 StrongBox EC)...",
            level = ConnectionLogLevel.INFO,
        ),
        ConnectionLogEntry(
            milestone = ConnectionMilestone.MFA,
            message = "Partial auth success (SSH_AUTH_PARTIAL). OTP prompt active",
            level = ConnectionLogLevel.WARN,
        ),
        ConnectionLogEntry(
            milestone = ConnectionMilestone.CHANNEL,
            message = "Allocated PTY (xterm-256color, 80x24)",
            level = ConnectionLogLevel.INFO,
        ),
        ConnectionLogEntry(
            milestone = ConnectionMilestone.READY,
            message = "Interactive shell session established (540ms total)",
            level = ConnectionLogLevel.INFO,
        ),
    )

    @Test
    fun testConnectingLogOverlay_DarkTheme() {
        paparazzi.snapshot {
            CoSSHTheme(darkTheme = true) {
                Box(modifier = Modifier.fillMaxSize()) {
                    ConnectingLogOverlay(
                        profileNickname = "Production Bastion",
                        host = "bastion.corp.internal",
                        port = 22,
                        logs = sampleLogs,
                        onCopyDiagnostics = {},
                    )
                }
            }
        }
    }

    @Test
    fun testConnectingLogOverlay_LightTheme() {
        paparazzi.snapshot {
            CoSSHTheme(darkTheme = false) {
                Box(modifier = Modifier.fillMaxSize()) {
                    ConnectingLogOverlay(
                        profileNickname = "Homelab Server",
                        host = "192.168.1.100",
                        port = 2223,
                        logs = sampleLogs.take(6),
                        onCopyDiagnostics = {},
                    )
                }
            }
        }
    }

    @Test
    fun testConnectingLogOverlay_ErrorState() {
        val errorLogs = sampleLogs.take(7) + listOf(
            ConnectionLogEntry(
                milestone = ConnectionMilestone.AUTH,
                message = "Publickey auth rejected by remote server",
                level = ConnectionLogLevel.WARN,
            ),
            ConnectionLogEntry(
                milestone = ConnectionMilestone.ERROR,
                message = "Exhausted available authentication methods (UserAuthException)",
                level = ConnectionLogLevel.ERROR,
            ),
        )

        paparazzi.snapshot {
            CoSSHTheme(darkTheme = true) {
                Box(modifier = Modifier.fillMaxSize()) {
                    ConnectingLogOverlay(
                        profileNickname = "Hardened Jump Host",
                        host = "jump.security.corp",
                        port = 22,
                        logs = errorLogs,
                        onCopyDiagnostics = {},
                    )
                }
            }
        }
    }
}
