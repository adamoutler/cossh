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

class TerminalScreen256ColorScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar"
    )

    @Before
    fun setup() {
        ConnectionStateRepository.clearSession("test256")
        ConnectionStateRepository.isHeadlessTest = true
        
        val sb = java.lang.StringBuilder()
        sb.append("xterm-256color Support Proof\r\n")
        sb.append("System Colors:\r\n")
        for (i in 0..15) {
            sb.append("\u001b[48;5;${i}m \u2588\u2588 ")
        }
        sb.append("\u001b[0m\r\n\r\nColor Cube, 6x6x6:\r\n")
        for (r in 0..5) {
            for (g in 0..5) {
                for (b in 0..5) {
                    val color = 16 + (r * 36) + (g * 6) + b
                    sb.append("\u001b[48;5;${color}m \u2588\u2588 ")
                }
                sb.append("\u001b[0m  ")
            }
            sb.append("\u001b[0m\r\n")
        }
        sb.append("\r\nGrayscale Ramp:\r\n")
        for (i in 232..255) {
            sb.append("\u001b[48;5;${i}m \u2588\u2588 ")
        }
        sb.append("\u001b[0m\r\n")
        
        ConnectionStateRepository.mockTestTranscripts["test256"] = sb.toString()
    }

    @Test
    fun terminalShows256Colors() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TerminalScreenContent(
                        profileId = "test256",
                        session = com.termux.terminal.TerminalSession(
                            "/system/bin/sh", "/", arrayOf(), arrayOf(), 0, object : com.termux.terminal.TerminalSessionClient {
                                override fun onTextChanged(session: com.termux.terminal.TerminalSession) {}
                                override fun onTitleChanged(session: com.termux.terminal.TerminalSession) {}
                                override fun onSessionFinished(session: com.termux.terminal.TerminalSession) {}
                                override fun onCopyTextToClipboard(session: com.termux.terminal.TerminalSession, text: String) {}
                                override fun onPasteTextFromClipboard(session: com.termux.terminal.TerminalSession) {}
                                override fun onBell(session: com.termux.terminal.TerminalSession) {}
                                override fun onColorsChanged(session: com.termux.terminal.TerminalSession) {}
                                override fun onTerminalCursorStateChange(state: Boolean) {}
                                override fun getTerminalCursorStyle(): Int = 0
                                override fun logError(tag: String?, msg: String?) {}
                                override fun logWarn(tag: String?, msg: String?) {}
                                override fun logInfo(tag: String?, msg: String?) {}
                                override fun logDebug(tag: String?, msg: String?) {}
                                override fun logVerbose(tag: String?, msg: String?) {}
                                override fun logStackTraceWithMessage(tag: String?, msg: String?, e: java.lang.Exception?) {}
                                override fun logStackTrace(tag: String?, e: java.lang.Exception?) {}
                            }
                        ),
                        activeSession = com.adamoutler.ssh.network.ActiveSessionState(profileId = "test256"),
                        currentFontSize = 10,
                        sessionId = "123",
                        isConnectionActive = true,
                        errorMessage = null,
                        onUpdateFontSize = {},
                        onNavigateBack = {},
                        onClearError = {}
                    )
                }
            }
        }
    }
}
