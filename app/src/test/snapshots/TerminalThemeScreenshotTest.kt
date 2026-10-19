package com.adamoutler.ssh.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.network.ActiveSessionState
import com.adamoutler.ssh.network.ConnectionStateRepository
import com.adamoutler.ssh.ui.screens.TerminalPreviewCard
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import com.adamoutler.ssh.ui.theme.TerminalFontChoice
import com.adamoutler.ssh.ui.theme.TerminalTheme
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TerminalThemeScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private val mockProfileId = "test-theme-profile"
    private val mockSessionId = "test-theme-session"
    private lateinit var mockSession: TerminalSession
    private lateinit var mockActiveSession: ActiveSessionState

    @Before
    fun setup() {
        ConnectionStateRepository.clearSession(mockSessionId)
        ConnectionStateRepository.isHeadlessTest = true
        ConnectionStateRepository.mockTestTranscripts[mockSessionId] = """
            user@cossh:~$ uname -a
            Linux cossh-hardened 6.6.0 #1 SMP PREEMPT
            user@cossh:~$ ls -la
            drwxr-xr-x 2 user user 4096 Oct 19 03:00 .
            -rw-r--r-- 1 user user  220 Oct 19 03:00 config.json
            user@cossh:~$ 
        """.trimIndent()

        mockSession = TerminalSession(
            "/system/bin/sh",
            "/",
            arrayOf(),
            arrayOf(),
            0,
            object : TerminalSessionClient {
                override fun onTextChanged(session: TerminalSession) {}
                override fun onTitleChanged(session: TerminalSession) {}
                override fun onSessionFinished(session: TerminalSession) {}
                override fun onCopyTextToClipboard(session: TerminalSession, text: String) {}
                override fun onPasteTextFromClipboard(session: TerminalSession) {}
                override fun onBell(session: TerminalSession) {}
                override fun onColorsChanged(session: TerminalSession) {}
                override fun onTerminalCursorStateChange(state: Boolean) {}
                override fun getTerminalCursorStyle(): Int = 0
                override fun logError(tag: String?, msg: String?) {}
                override fun logWarn(tag: String?, msg: String?) {}
                override fun logInfo(tag: String?, msg: String?) {}
                override fun logDebug(tag: String?, msg: String?) {}
                override fun logVerbose(tag: String?, msg: String?) {}
                override fun logStackTraceWithMessage(tag: String?, msg: String?, e: java.lang.Exception?) {}
                override fun logStackTrace(tag: String?, e: java.lang.Exception?) {}
            },
        )
        mockActiveSession = ActiveSessionState(profileId = mockProfileId)
    }

    @Test
    fun terminalTheme_CobaltBlue() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    TerminalScreenContent(
                        profileId = mockProfileId,
                        sessionId = mockSessionId,
                        session = mockSession,
                        activeSession = mockActiveSession,
                        currentFontSize = 14,
                        isConnectionActive = true,
                        errorMessage = null,
                        onUpdateFontSize = {},
                        onNavigateBack = {},
                        onClearError = {},
                        currentTheme = TerminalTheme.COBALT_BLUE,
                        currentFont = TerminalFontChoice.DEFAULT_MONOSPACE,
                        profile = ConnectionProfile(id = "1", nickname = "Cobalt Blue Server", host = "localhost"),
                    )
                }
            }
        }
    }

    @Test
    fun terminalTheme_SolarizedDark() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    TerminalScreenContent(
                        profileId = mockProfileId,
                        sessionId = mockSessionId,
                        session = mockSession,
                        activeSession = mockActiveSession,
                        currentFontSize = 14,
                        isConnectionActive = true,
                        errorMessage = null,
                        onUpdateFontSize = {},
                        onNavigateBack = {},
                        onClearError = {},
                        currentTheme = TerminalTheme.SOLARIZED_DARK,
                        currentFont = TerminalFontChoice.DEFAULT_MONOSPACE,
                        profile = ConnectionProfile(id = "2", nickname = "Solarized Server", host = "localhost"),
                    )
                }
            }
        }
    }

    @Test
    fun terminalTheme_Monokai() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    TerminalScreenContent(
                        profileId = mockProfileId,
                        sessionId = mockSessionId,
                        session = mockSession,
                        activeSession = mockActiveSession,
                        currentFontSize = 14,
                        isConnectionActive = true,
                        errorMessage = null,
                        onUpdateFontSize = {},
                        onNavigateBack = {},
                        onClearError = {},
                        currentTheme = TerminalTheme.MONOKAI,
                        currentFont = TerminalFontChoice.DEFAULT_MONOSPACE,
                        profile = ConnectionProfile(id = "3", nickname = "Monokai Server", host = "localhost"),
                    )
                }
            }
        }
    }

    @Test
    fun terminalTheme_Dracula() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    TerminalScreenContent(
                        profileId = mockProfileId,
                        sessionId = mockSessionId,
                        session = mockSession,
                        activeSession = mockActiveSession,
                        currentFontSize = 14,
                        isConnectionActive = true,
                        errorMessage = null,
                        onUpdateFontSize = {},
                        onNavigateBack = {},
                        onClearError = {},
                        currentTheme = TerminalTheme.DRACULA,
                        currentFont = TerminalFontChoice.DEFAULT_MONOSPACE,
                        profile = ConnectionProfile(id = "4", nickname = "Dracula Server", host = "localhost"),
                    )
                }
            }
        }
    }

    @Test
    fun terminalFont_JetBrainsMono() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    TerminalScreenContent(
                        profileId = mockProfileId,
                        sessionId = mockSessionId,
                        session = mockSession,
                        activeSession = mockActiveSession,
                        currentFontSize = 14,
                        isConnectionActive = true,
                        errorMessage = null,
                        onUpdateFontSize = {},
                        onNavigateBack = {},
                        onClearError = {},
                        currentTheme = TerminalTheme.COBALT_BLUE,
                        currentFont = TerminalFontChoice.JETBRAINS_MONO,
                        profile = ConnectionProfile(id = "5", nickname = "JetBrains Mono Server", host = "localhost"),
                    )
                }
            }
        }
    }

    @Test
    fun terminalFont_FiraCode() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    TerminalScreenContent(
                        profileId = mockProfileId,
                        sessionId = mockSessionId,
                        session = mockSession,
                        activeSession = mockActiveSession,
                        currentFontSize = 14,
                        isConnectionActive = true,
                        errorMessage = null,
                        onUpdateFontSize = {},
                        onNavigateBack = {},
                        onClearError = {},
                        currentTheme = TerminalTheme.COBALT_BLUE,
                        currentFont = TerminalFontChoice.FIRA_CODE,
                        profile = ConnectionProfile(id = "6", nickname = "Fira Code Server", host = "localhost"),
                    )
                }
            }
        }
    }

    @Test
    fun terminalPreviewCard_CobaltBlue() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    TerminalPreviewCard(
                        theme = TerminalTheme.COBALT_BLUE,
                        fontChoice = TerminalFontChoice.DEFAULT_MONOSPACE,
                    )
                }
            }
        }
    }

    @Test
    fun terminalPreviewCard_Dracula() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    TerminalPreviewCard(
                        theme = TerminalTheme.DRACULA,
                        fontChoice = TerminalFontChoice.SOURCE_CODE_PRO,
                    )
                }
            }
        }
    }
}
