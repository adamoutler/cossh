package com.adamoutler.ssh.ui.screens.identity.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import org.junit.Rule
import org.junit.Test

class EncryptedKeyPassphraseModalScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @Test
    fun encryptedKeyPassphraseModal_Default_Light() {
        paparazzi.snapshot {
            CoSSHTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        EncryptedKeyPassphraseModal(
                            keyFormatSummary = "OpenSSH (aes256-cbc)",
                            errorMessage = null,
                            onConfirm = {},
                            onDismiss = {},
                        )
                    }
                }
            }
        }
    }

    @Test
    fun encryptedKeyPassphraseModal_WithError_Dark() {
        paparazzi.snapshot {
            CoSSHTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        EncryptedKeyPassphraseModal(
                            keyFormatSummary = "PKCS#8 Encrypted",
                            errorMessage = "Incorrect passphrase for private key",
                            onConfirm = {},
                            onDismiss = {},
                        )
                    }
                }
            }
        }
    }
}
