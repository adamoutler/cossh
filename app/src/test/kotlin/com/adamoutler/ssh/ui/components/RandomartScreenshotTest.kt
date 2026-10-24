package com.adamoutler.ssh.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.adamoutler.ssh.crypto.randomart.OpenSshRandomartGenerator
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import org.junit.Rule
import org.junit.Test
import java.security.MessageDigest

class RandomartScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @Test
    fun testRandomartBox_Ed25519() {
        val digest = MessageDigest.getInstance("SHA-256").digest("CoSSH-Ed25519-HostKey-TestVector".toByteArray())
        val randomart = OpenSshRandomartGenerator.generate(digest, "ED25519 256", "SHA256")

        paparazzi.snapshot {
            CoSSHTheme(darkTheme = true) {
                Surface {
                    Box(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        RandomartBox(
                            randomart = randomart,
                            title = "Server Host Key Visual Fingerprint",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun testRandomartBox_RsaSha256() {
        val digest = MessageDigest.getInstance("SHA-256").digest("CoSSH-RSA-4096-HostKey-TestVector".toByteArray())
        val randomart = OpenSshRandomartGenerator.generate(digest, "RSA 4096", "SHA256")

        paparazzi.snapshot {
            CoSSHTheme(darkTheme = false) {
                Surface {
                    Box(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        RandomartBox(
                            randomart = randomart,
                            title = "Expected Host Key",
                        )
                    }
                }
            }
        }
    }
}
