package com.adamoutler.ssh.ui.components

import app.cash.paparazzi.DeviceConfig.Companion.PIXEL_5
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier

class TerminalExtraKeysScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar"
    )

    @Test
    fun page1_noModifiers() {
        paparazzi.snapshot {
            TerminalExtraKeys(
                ctrlActive = false,
                altActive = false,
                superActive = false,
                menuActive = false,
                onKeyToggle = {},
                onKeyPress = {},
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    @Test
    fun page1_withModifiers() {
        paparazzi.snapshot {
            TerminalExtraKeys(
                ctrlActive = true,
                altActive = false,
                superActive = false,
                menuActive = false,
                onKeyToggle = {},
                onKeyPress = {},
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    @Test
    fun page3_f1_f12_keys() {
        paparazzi.snapshot {
            TerminalExtraKeys(
                ctrlActive = false,
                altActive = false,
                superActive = false,
                menuActive = false,
                onKeyToggle = {},
                onKeyPress = {},
                modifier = Modifier.fillMaxWidth(),
                initialPage = 2
            )
        }
    }
}