package com.adamoutler.ssh.terminal

import com.adamoutler.ssh.data.CommandSnippet
import com.adamoutler.ssh.ui.components.TerminalSnippetHandler
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalSnippetAuthTest {

    @Test
    fun testSnippetWithRequireAuthTriggersBiometricAuthPrompt() {
        var dispatchedBytes: ByteArray? = null
        var authRequestedSnippet: CommandSnippet? = null
        var authCallback: (() -> Unit)? = null

        val handler = TerminalSnippetHandler(
            sendToTerminal = { bytes -> dispatchedBytes = bytes },
            requestConfirmation = {},
            requestAuthentication = { snippet, onAuthenticated ->
                authRequestedSnippet = snippet
                authCallback = onAuthenticated
            },
        )

        val protectedSnippet = CommandSnippet(
            id = "snippet-danger-1",
            name = "Drop Cache",
            command = "sync && echo 3 > /proc/sys/vm/drop_caches",
            autoSend = true,
            requireAuth = true,
        )

        handler.onSnippetClicked(protectedSnippet)

        assertNotNull("Auth callback must be supplied", authCallback)
        assertEquals("Authentication prompt must be triggered for protected snippet", protectedSnippet, authRequestedSnippet)
        assertNull("Command must not be dispatched to PTY before biometric authentication completes", dispatchedBytes)
    }

    @Test
    fun testSnippetCommandDispatchedOnlyAfterAuthSucceeds() {
        var dispatchedBytes: ByteArray? = null
        var authCallback: (() -> Unit)? = null

        val handler = TerminalSnippetHandler(
            sendToTerminal = { bytes -> dispatchedBytes = bytes },
            requestConfirmation = {},
            requestAuthentication = { _, onAuthenticated ->
                authCallback = onAuthenticated
            },
        )

        val protectedSnippet = CommandSnippet(
            id = "snippet-reboot",
            name = "Reboot Host",
            command = "sudo reboot",
            autoSend = true,
            requireAuth = true,
        )

        handler.onSnippetClicked(protectedSnippet)
        assertNull("PTY stream must receive 0 bytes prior to auth", dispatchedBytes)

        // Simulate successful biometric authentication callback
        authCallback?.invoke()

        val expected = "sudo reboot\r".toByteArray(Charsets.UTF_8)
        assertArrayEquals("PTY stream must receive command with carriage return after biometric success", expected, dispatchedBytes)
    }

    @Test
    fun testSnippetCommandNotDispatchedIfAuthCancelledOrFailed() {
        var dispatchedBytes: ByteArray? = null
        var authPromptShown = false

        val handler = TerminalSnippetHandler(
            sendToTerminal = { bytes -> dispatchedBytes = bytes },
            requestConfirmation = {},
            requestAuthentication = { _, _ ->
                authPromptShown = true
                // Simulate user dismissal or failed biometric match (onAuthenticated never invoked)
            },
        )

        val protectedSnippet = CommandSnippet(
            id = "snippet-rm",
            name = "Remove Log",
            command = "rm -f /var/log/syslog",
            autoSend = true,
            requireAuth = true,
        )

        handler.onSnippetClicked(protectedSnippet)

        assertTrue("Biometric prompt should have been requested", authPromptShown)
        assertNull("0 bytes must be sent to PTY when user cancels or fails biometric authentication", dispatchedBytes)
    }

    @Test
    fun testSnippetWithoutRequireAuthDispatchesImmediatelyWithoutPromptingAuth() {
        var dispatchedBytes: ByteArray? = null
        var authPromptShown = false

        val handler = TerminalSnippetHandler(
            sendToTerminal = { bytes -> dispatchedBytes = bytes },
            requestConfirmation = {},
            requestAuthentication = { _, _ ->
                authPromptShown = true
            },
        )

        val normalSnippet = CommandSnippet(
            id = "snippet-ls",
            name = "List Directory",
            command = "ls -la",
            autoSend = true,
            requireAuth = false,
        )

        handler.onSnippetClicked(normalSnippet)

        assertFalse("Authentication prompt must not be requested when requireAuth is false", authPromptShown)
        val expected = "ls -la\r".toByteArray(Charsets.UTF_8)
        assertArrayEquals("Normal snippet must dispatch to terminal immediately", expected, dispatchedBytes)
    }

    @Test
    fun testSnippetRequireAuthWithoutAutoSendSendsCleanCommandAfterAuth() {
        var dispatchedBytes: ByteArray? = null
        var authCallback: (() -> Unit)? = null

        val handler = TerminalSnippetHandler(
            sendToTerminal = { bytes -> dispatchedBytes = bytes },
            requestConfirmation = {},
            requestAuthentication = { _, onAuthenticated ->
                authCallback = onAuthenticated
            },
        )

        val manualSnippet = CommandSnippet(
            id = "snippet-manual",
            name = "Database Dump",
            command = "pg_dump -U postgres dbname",
            autoSend = false,
            requireAuth = true,
        )

        handler.onSnippetClicked(manualSnippet)
        assertNull(dispatchedBytes)

        authCallback?.invoke()

        val expected = "pg_dump -U postgres dbname".toByteArray(Charsets.UTF_8)
        assertArrayEquals("Manual snippet must not append newline characters", expected, dispatchedBytes)
        assertFalse(String(dispatchedBytes!!).endsWith("\r"))
        assertFalse(String(dispatchedBytes!!).endsWith("\n"))
    }
}
