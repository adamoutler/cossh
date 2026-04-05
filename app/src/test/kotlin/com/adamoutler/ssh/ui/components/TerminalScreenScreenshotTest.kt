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

    @Test
    fun testTerminalScreenLiveCommand() {
        paparazzi.snapshot {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalInspectionMode provides true
            ) {
                TerminalScreen(initialText = "user@test-server:~$ top\n\nTasks: 1 total,   1 running,   0 sleeping,   0 stopped,   0 zombie\n%Cpu(s):  0.0 us,  0.0 sy,  0.0 ni,100.0 id,  0.0 wa,  0.0 hi,  0.0 si,  0.0 st\nMiB Mem :   7950.4 total,   5314.9 free,   1101.4 used,   1534.1 buff/cache\nMiB Swap:   4096.0 total,   4096.0 free,      0.0 used.   6543.8 avail Mem\n\n  PID USER      PR  NI    VIRT    RES    SHR S  %CPU  %MEM     TIME+ COMMAND\n    1 user      20   0    2932   1576   1408 R   0.0   0.0   0:00.00 top")
            }
        }
    }
}
