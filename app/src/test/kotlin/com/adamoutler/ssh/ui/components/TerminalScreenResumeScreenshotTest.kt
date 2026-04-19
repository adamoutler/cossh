package com.adamoutler.ssh.ui.components

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.adamoutler.ssh.network.ConnectionStateRepository
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

class TerminalScreenResumeScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar"
    )

    @Before
    fun setup() {
        ConnectionStateRepository.clearSession("test")
        ConnectionStateRepository.isHeadlessTest = true
        
        val dummyText = "Welcome to CoSSH Terminal\r\n" +
                        "root@server:~# tail -f /var/log/syslog\r\n" +
                        "This is a dummy log line 1 proving persistence.\r\n" +
                        "This is a dummy log line 2 proving persistence.\r\n" +
                        "This is a dummy log line 3 proving persistence.\r\n"
        
        ConnectionStateRepository.mockTestTranscripts["test"] = dummyText
    }

    @Test
    fun terminalShowsPersistedHistoryOnResume() {
        val viewModel = com.adamoutler.ssh.ui.screens.TerminalViewModel()
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TerminalScreen(profileId = "test", terminalViewModel = viewModel)
                }
            }
        }
    }
}
