package com.adamoutler.ssh.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import com.adamoutler.ssh.util.TerminalUrlSanitizer
import org.junit.Rule
import org.junit.Test

class UrlConfirmationScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @Test
    fun testUrlConfirmation_HttpsStandard_Light() {
        val sanitized = TerminalUrlSanitizer.sanitize("https://github.com/adamoutler/cossh/releases/tag/v1.0")!!
        paparazzi.snapshot {
            CoSSHTheme(darkTheme = false) {
                Surface {
                    Box(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        UrlConfirmationContent(
                            sanitizedUrl = sanitized,
                            onOpenBrowser = {},
                            onCopyLink = {},
                            onDismiss = {},
                        )
                    }
                }
            }
        }
    }

    @Test
    fun testUrlConfirmation_InsecureHttpWithRawIp() {
        val sanitized = TerminalUrlSanitizer.sanitize("http://192.168.1.100:8080/admin/shell?token=secret")!!
        paparazzi.snapshot {
            CoSSHTheme(darkTheme = true) {
                Surface {
                    Box(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        UrlConfirmationContent(
                            sanitizedUrl = sanitized,
                            onOpenBrowser = {},
                            onCopyLink = {},
                            onDismiss = {},
                        )
                    }
                }
            }
        }
    }

    @Test
    fun testUrlConfirmation_IdnPunycodeHomographAttack() {
        val sanitized = TerminalUrlSanitizer.sanitize("https://xn--e1afmkfd.org/login")!!
        paparazzi.snapshot {
            CoSSHTheme(darkTheme = true) {
                Surface {
                    Box(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        UrlConfirmationContent(
                            sanitizedUrl = sanitized,
                            onOpenBrowser = {},
                            onCopyLink = {},
                            onDismiss = {},
                        )
                    }
                }
            }
        }
    }
}
