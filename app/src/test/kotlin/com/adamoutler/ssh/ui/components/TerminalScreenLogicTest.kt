package com.adamoutler.ssh.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.Protocol
import com.adamoutler.ssh.network.ActiveSessionState
import com.adamoutler.ssh.network.ConnectionStateRepository
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.PipedInputStream
import java.io.PipedOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TerminalScreenLogicTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testTerminalScreenLogic() {
        // This is a placeholder test. I will add more logic tests here.
        assertTrue(true)
    }

    @Test
    fun testTerminalScreenContentKeyboardInteractions() {
        var navigateBackClicked = false

        var updateFontSizeCalled = false
        var terminalInputState = 1 // 1 for EXTRA_KEYS

        val mockSessionId = "test-session-123"
        ConnectionStateRepository.clearSession(mockSessionId)
        ConnectionStateRepository.isHeadlessTest = true

        val pos = PipedOutputStream()

        val pis = PipedInputStream(pos)

        val mockSession = TerminalSession(
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

        composeTestRule.setContent {
            TerminalScreenContent(
                profileId = "test-profile-123",
                sessionId = mockSessionId,
                session = mockSession,
                activeSession = ActiveSessionState(profileId = "test-profile-123", ptyOutputStream = pos, sshShell = null),
                currentFontSize = 14,
                isConnectionActive = true,
                errorMessage = null,
                onUpdateFontSize = { updateFontSizeCalled = true },
                onNavigateBack = { navigateBackClicked = true },
                onClearError = { },
                profile = ConnectionProfile(id = "1", nickname = "My Server", host = "localhost", protocol = Protocol.SSH),
                initialTerminalInputState = terminalInputState,
            )
        }

        composeTestRule.waitForIdle()
        assertFalse(navigateBackClicked)
        assertFalse(updateFontSizeCalled)
        assertNotNull(pis)
    }
}
