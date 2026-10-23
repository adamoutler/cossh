package com.adamoutler.ssh.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.adamoutler.ssh.network.PamPrompt
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import org.junit.Rule
import org.junit.Test

class PamAuthDialogScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @Test
    fun singlePromptPassword_Default_Light() {
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
                        PamAuthDialog(
                            name = "PAM Authentication",
                            instruction = "Please provide your system login password.",
                            prompts = listOf(PamPrompt(prompt = "Password:", echo = false)),
                            round = 1,
                            onConfirm = {},
                            onDismiss = {},
                        )
                    }
                }
            }
        }
    }

    @Test
    fun multiPromptTwoFactor_Dark() {
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
                        PamAuthDialog(
                            name = "Duo Multi-Factor Auth",
                            instruction = "A push notification was sent to your registered device, or enter a one-time passcode below.",
                            prompts = listOf(
                                PamPrompt(prompt = "Passcode or 'push':", echo = true),
                            ),
                            round = 2,
                            onConfirm = {},
                            onDismiss = {},
                        )
                    }
                }
            }
        }
    }
}
