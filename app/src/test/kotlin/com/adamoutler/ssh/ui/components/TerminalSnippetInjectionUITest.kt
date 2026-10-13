package com.adamoutler.ssh.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.adamoutler.ssh.data.CommandSnippet
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.Protocol
import com.adamoutler.ssh.network.ActiveSessionState
import com.adamoutler.ssh.network.ConnectionStateRepository
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TerminalSnippetInjectionUITest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun createMockSession(): TerminalSession = TerminalSession(
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

    private fun waitForStreamContent(
        outputStream: ByteArrayOutputStream,
        condition: (String) -> Boolean,
        timeoutMs: Long = 3000,
    ): String {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            composeTestRule.waitForIdle()
            val text = synchronized(outputStream) { outputStream.toString("UTF-8") }
            if (condition(text)) return text
            Thread.sleep(50)
        }
        return synchronized(outputStream) { outputStream.toString("UTF-8") }
    }

    @Test
    fun testSnippetAutoSendSendsCommandWithCarriageReturn() {
        val mockSessionId = "session-snippet-autosend"
        ConnectionStateRepository.clearSession(mockSessionId)
        ConnectionStateRepository.isHeadlessTest = true

        val outputStream = ByteArrayOutputStream()
        val snippetAutoSend = CommandSnippet(
            id = "s-auto",
            name = "Auto Ping",
            command = "ping -c 1 127.0.0.1",
            autoSend = true,
            requireAuth = false,
        )

        val profile = ConnectionProfile(
            id = "profile-1",
            nickname = "Snippet Server",
            host = "localhost",
            protocol = Protocol.SSH,
            commandSnippets = listOf(snippetAutoSend),
        )

        composeTestRule.setContent {
            TerminalScreenContent(
                profileId = "profile-1",
                sessionId = mockSessionId,
                session = createMockSession(),
                activeSession = ActiveSessionState(profileId = "profile-1", ptyOutputStream = outputStream),
                currentFontSize = 14,
                isConnectionActive = true,
                errorMessage = null,
                onUpdateFontSize = {},
                onNavigateBack = {},
                onClearError = {},
                profile = profile,
                initialTerminalInputState = 0,
            )
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Auto Ping").assertExists()
        composeTestRule.onNodeWithText("Auto Ping").performClick()

        val captured = waitForStreamContent(outputStream, { it.contains("ping -c 1 127.0.0.1\r") })
        assertEquals("ping -c 1 127.0.0.1\r", captured)
        assertTrue("AutoSend snippet must append carriage return (\\r)", captured.endsWith("\r"))
    }

    @Test
    fun testSnippetWithoutAutoSendSendsCommandWithoutNewline() {
        val mockSessionId = "session-snippet-noautosend"
        ConnectionStateRepository.clearSession(mockSessionId)
        ConnectionStateRepository.isHeadlessTest = true

        val outputStream = ByteArrayOutputStream()
        val snippetManual = CommandSnippet(
            id = "s-manual",
            name = "Manual Disk",
            command = "df -h",
            autoSend = false,
            requireAuth = false,
        )

        val profile = ConnectionProfile(
            id = "profile-2",
            nickname = "Snippet Server 2",
            host = "localhost",
            protocol = Protocol.SSH,
            commandSnippets = listOf(snippetManual),
        )

        composeTestRule.setContent {
            TerminalScreenContent(
                profileId = "profile-2",
                sessionId = mockSessionId,
                session = createMockSession(),
                activeSession = ActiveSessionState(profileId = "profile-2", ptyOutputStream = outputStream),
                currentFontSize = 14,
                isConnectionActive = true,
                errorMessage = null,
                onUpdateFontSize = {},
                onNavigateBack = {},
                onClearError = {},
                profile = profile,
                initialTerminalInputState = 0,
            )
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Manual Disk").assertExists()
        composeTestRule.onNodeWithText("Manual Disk").performClick()

        val captured = waitForStreamContent(outputStream, { it.contains("df -h") })
        assertEquals("df -h", captured)
        assertFalse("Non-autoSend snippet must NOT append carriage return", captured.endsWith("\r"))
        assertFalse("Non-autoSend snippet must NOT append newline", captured.endsWith("\n"))
    }

    @Test
    fun testSnippetRequireAuthGatedBehindConfirmationDialog() {
        val mockSessionId = "session-snippet-auth"
        ConnectionStateRepository.clearSession(mockSessionId)
        ConnectionStateRepository.isHeadlessTest = true

        val outputStream = ByteArrayOutputStream()
        val protectedSnippet = CommandSnippet(
            id = "s-danger",
            name = "Dangerous Wipe",
            command = "rm -rf /cache/*",
            autoSend = true,
            requireAuth = true,
        )

        val profile = ConnectionProfile(
            id = "profile-3",
            nickname = "Snippet Server 3",
            host = "localhost",
            protocol = Protocol.SSH,
            commandSnippets = listOf(protectedSnippet),
        )

        composeTestRule.setContent {
            TerminalScreenContent(
                profileId = "profile-3",
                sessionId = mockSessionId,
                session = createMockSession(),
                activeSession = ActiveSessionState(profileId = "profile-3", ptyOutputStream = outputStream),
                currentFontSize = 14,
                isConnectionActive = true,
                errorMessage = null,
                onUpdateFontSize = {},
                onNavigateBack = {},
                onClearError = {},
                profile = profile,
                initialTerminalInputState = 0,
            )
        }

        composeTestRule.waitForIdle()

        // 1. Click snippet - confirmation dialog must appear, no data written to stream
        composeTestRule.onNodeWithText("Dangerous Wipe").performClick()
        composeTestRule.waitForIdle()

        assertEquals("No bytes should be written to terminal before confirmation", 0, outputStream.size())
        composeTestRule.onNodeWithText("Confirm Execution").assertExists()
        composeTestRule.onNodeWithText("Are you sure you want to execute 'Dangerous Wipe'?\n\nCommand: rm -rf /cache/*").assertExists()

        // 2. Dismiss via Cancel - dialog closes, still no data written
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Confirm Execution").assertDoesNotExist()
        assertEquals("No bytes should be written when user cancels confirmation", 0, outputStream.size())

        // 3. Click snippet again and approve via Execute
        composeTestRule.onNodeWithText("Dangerous Wipe").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Confirm Execution").assertExists()

        composeTestRule.onNodeWithText("Execute").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Confirm Execution").assertDoesNotExist()

        val captured = waitForStreamContent(outputStream, { it.contains("rm -rf /cache/*\r") })
        assertEquals("rm -rf /cache/*\r", captured)
    }

    @Test
    fun testSnippetRequireAuthWithoutAutoSendDispatchesCleanCommandOnConfirm() {
        val mockSessionId = "session-snippet-auth-noauto"
        ConnectionStateRepository.clearSession(mockSessionId)
        ConnectionStateRepository.isHeadlessTest = true

        val outputStream = ByteArrayOutputStream()
        val protectedManualSnippet = CommandSnippet(
            id = "s-auth-manual",
            name = "Sensitive Script",
            command = "./deploy.sh",
            autoSend = false,
            requireAuth = true,
        )

        val profile = ConnectionProfile(
            id = "profile-4",
            nickname = "Snippet Server 4",
            host = "localhost",
            protocol = Protocol.SSH,
            commandSnippets = listOf(protectedManualSnippet),
        )

        composeTestRule.setContent {
            TerminalScreenContent(
                profileId = "profile-4",
                sessionId = mockSessionId,
                session = createMockSession(),
                activeSession = ActiveSessionState(profileId = "profile-4", ptyOutputStream = outputStream),
                currentFontSize = 14,
                isConnectionActive = true,
                errorMessage = null,
                onUpdateFontSize = {},
                onNavigateBack = {},
                onClearError = {},
                profile = profile,
                initialTerminalInputState = 0,
            )
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Sensitive Script").performClick()
        composeTestRule.waitForIdle()

        assertEquals(0, outputStream.size())
        composeTestRule.onNodeWithText("Execute").performClick()
        composeTestRule.waitForIdle()

        val captured = waitForStreamContent(outputStream, { it.contains("./deploy.sh") })
        assertEquals("./deploy.sh", captured)
        assertFalse(captured.endsWith("\r"))
    }
}
