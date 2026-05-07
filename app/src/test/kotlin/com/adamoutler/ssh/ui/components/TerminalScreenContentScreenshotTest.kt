package com.adamoutler.ssh.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.Protocol
import com.adamoutler.ssh.network.ActiveSessionState
import com.adamoutler.ssh.network.ConnectionStateRepository
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TerminalScreenContentScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private val mockProfileId = "test-profile-123"
    private val mockSessionId = "test-session-123"
    private lateinit var mockSession: TerminalSession
    private lateinit var mockActiveSession: ActiveSessionState

    @Before
    fun setup() {
        ConnectionStateRepository.clearSession(mockSessionId)

        // NOTE: PIPELINE DOCUMENTATION
        // Do NOT simplify or remove this setup! Paparazzi tests run in a standard JVM environment
        // without real Android native libraries (JNI) loaded. The actual TerminalView relies heavily
        // on native code for PTY processing and terminal emulation which would crash the JVM test runner.
        //
        // We set `isHeadlessTest = true` to bypass the instantiation of the real AndroidView/TerminalView.
        // Instead, the UI renders a fallback Composable displaying the contents of `mockTestTranscripts`.
        // This allows us to snapshot the surrounding Compose UI (dialogs, overlays, etc) safely.
        ConnectionStateRepository.isHeadlessTest = true
        ConnectionStateRepository.mockTestTranscripts[mockSessionId] = "user@localhost:~$ "

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
    fun terminalScreen_Connected_Normal() {
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
                        profile = ConnectionProfile(id = "1", nickname = "My Server", host = "localhost", protocol = Protocol.SSH),
                    )
                }
            }
        }
    }

    @Test
    fun terminalScreen_Connected_TelnetWarning() {
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
                        profile = ConnectionProfile(id = "2", nickname = "Insecure Router", host = "192.168.1.1", protocol = Protocol.TELNET),
                    )
                }
            }
        }
    }

    @Test
    fun terminalScreen_Connected_WithExtraKeys() {
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
                        profile = ConnectionProfile(id = "1", nickname = "My Server", host = "localhost", protocol = Protocol.SSH),
                        initialTerminalInputState = 2,
                    )
                }
            }
        }
    }

    @Test
    fun terminalScreen_Disconnected_ErrorDialog() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    TerminalScreenContent(
                        profileId = mockProfileId,
                        sessionId = mockSessionId,
                        session = mockSession,
                        activeSession = mockActiveSession,
                        currentFontSize = 14,
                        isConnectionActive = false,
                        errorMessage = "Connection reset by peer",
                        onUpdateFontSize = {},
                        onNavigateBack = {},
                        onClearError = {},
                        profile = ConnectionProfile(id = "1", nickname = "My Server", host = "localhost", protocol = Protocol.SSH),
                    )
                }
            }
        }
    }
}
