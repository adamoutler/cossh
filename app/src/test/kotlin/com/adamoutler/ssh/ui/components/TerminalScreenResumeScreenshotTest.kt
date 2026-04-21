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
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TerminalScreenContent(
                        profileId = "test",
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
                        activeSession = com.adamoutler.ssh.network.ActiveSessionState(profileId = "test"),
                        currentFontSize = 14,
                        activeConnections = setOf("test"),
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
