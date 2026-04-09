package com.adamoutler.ssh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.cash.paparazzi.DeviceConfig.Companion.PIXEL_5
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class TerminalScreenOverlayScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar"
    )

    @Test
    fun overlayButtonsVisible() {
        paparazzi.snapshot {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                TerminalOverlayButtons(
                    onBackground = {},
                    onTerminate = {}
                )
            }
        }
    }
}
