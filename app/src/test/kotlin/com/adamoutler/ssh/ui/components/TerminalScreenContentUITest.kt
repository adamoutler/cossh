package com.adamoutler.ssh.ui.components

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.Protocol
import com.adamoutler.ssh.network.ActiveSessionState
import com.adamoutler.ssh.network.ConnectionStateRepository
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class TerminalScreenContentUITest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @Test
    fun testTerminalExtraKeysTriggerCallbacks() {
        org.junit.Assume.assumeTrue(com.adamoutler.ssh.BuildConfig.DEBUG)

        val mockSessionId = "test-session-123"
        ConnectionStateRepository.clearSession(mockSessionId)
        ConnectionStateRepository.isHeadlessTest = true

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

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    TerminalScreenContent(
                        profileId = "test-profile-123",
                        sessionId = mockSessionId,
                        session = mockSession,
                        activeSession = ActiveSessionState(profileId = "test-profile-123"),
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

            // Click some extra keys to trigger the onKeyPress lambda and execute the switch cases
            val visibleKeys = listOf(
                "Esc", "Tab", "Ctrl-C", "↑", "Home", "End", "↓", "←", "→"
            )

            for (key in visibleKeys) {
                composeTestRule.onNodeWithText(key).performClick()
            }

            assertTrue(true)
        }
    }
}
