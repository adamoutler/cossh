package com.adamoutler.ssh.ui.components

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class TerminalScreenScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5
    )

    @Test
    fun testTerminalScreenANSI() {
        paparazzi.snapshot {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalInspectionMode provides true
            ) {
                TerminalScreen()
            }
        }
    }
}
