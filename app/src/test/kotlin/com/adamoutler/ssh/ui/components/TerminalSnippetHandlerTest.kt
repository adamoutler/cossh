package com.adamoutler.ssh.ui.components

import com.adamoutler.ssh.data.CommandSnippet
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalSnippetHandlerTest {

    @Test
    fun testFormatCommandWithAutoSendAppendsCarriageReturn() {
        val snippet = CommandSnippet(
            id = "s-1",
            name = "Restart Nginx",
            command = "systemctl restart nginx",
            autoSend = true,
            requireAuth = false,
        )

        val formattedBytes = TerminalSnippetHandler.formatCommand(snippet)
        val expected = "systemctl restart nginx\r".toByteArray(Charsets.UTF_8)
        assertArrayEquals(expected, formattedBytes)
        assertEquals('\r'.code.toByte(), formattedBytes.last())
    }

    @Test
    fun testFormatCommandWithoutAutoSendDoesNotAppendNewline() {
        val snippet = CommandSnippet(
            id = "s-2",
            name = "Check Disk",
            command = "df -h",
            autoSend = false,
            requireAuth = false,
        )

        val formattedBytes = TerminalSnippetHandler.formatCommand(snippet)
        val expected = "df -h".toByteArray(Charsets.UTF_8)
        assertArrayEquals(expected, formattedBytes)
        assertFalse(String(formattedBytes).endsWith("\r"))
        assertFalse(String(formattedBytes).endsWith("\n"))
    }

    @Test
    fun testOnSnippetClickedWithoutAuthDispatchesImmediatelyWithAutoSend() {
        var sentBytes: ByteArray? = null
        var requestedConfirmation: CommandSnippet? = null

        val handler = TerminalSnippetHandler(
            sendToTerminal = { bytes -> sentBytes = bytes },
            requestConfirmation = { snippet -> requestedConfirmation = snippet },
        )

        val snippet = CommandSnippet(
            id = "s-3",
            name = "Quick Ping",
            command = "ping -c 1 8.8.8.8",
            autoSend = true,
            requireAuth = false,
        )

        handler.onSnippetClicked(snippet)

        assertNull("Confirmation should not be requested when requireAuth is false", requestedConfirmation)
        val expected = "ping -c 1 8.8.8.8\r".toByteArray(Charsets.UTF_8)
        assertArrayEquals(expected, sentBytes)
    }

    @Test
    fun testOnSnippetClickedWithoutAuthDispatchesImmediatelyWithoutAutoSend() {
        var sentBytes: ByteArray? = null
        var requestedConfirmation: CommandSnippet? = null

        val handler = TerminalSnippetHandler(
            sendToTerminal = { bytes -> sentBytes = bytes },
            requestConfirmation = { snippet -> requestedConfirmation = snippet },
        )

        val snippet = CommandSnippet(
            id = "s-4",
            name = "Git Status",
            command = "git status",
            autoSend = false,
            requireAuth = false,
        )

        handler.onSnippetClicked(snippet)

        assertNull("Confirmation should not be requested when requireAuth is false", requestedConfirmation)
        val expected = "git status".toByteArray(Charsets.UTF_8)
        assertArrayEquals(expected, sentBytes)
    }

    @Test
    fun testOnSnippetClickedWithRequireAuthGatedBehindConfirmation() {
        var sentBytes: ByteArray? = null
        var requestedConfirmation: CommandSnippet? = null

        val handler = TerminalSnippetHandler(
            sendToTerminal = { bytes -> sentBytes = bytes },
            requestConfirmation = { snippet -> requestedConfirmation = snippet },
        )

        val dangerousSnippet = CommandSnippet(
            id = "s-5",
            name = "Dangerous Wipe",
            command = "rm -rf /var/cache/*",
            autoSend = true,
            requireAuth = true,
        )

        handler.onSnippetClicked(dangerousSnippet)

        assertEquals("Confirmation should be requested for protected snippet", dangerousSnippet, requestedConfirmation)
        assertNull("Command bytes must not be sent before user confirmation", sentBytes)
    }

    @Test
    fun testOnSnippetConfirmedDispatchesAutoSendSnippet() {
        var sentBytes: ByteArray? = null
        val handler = TerminalSnippetHandler(
            sendToTerminal = { bytes -> sentBytes = bytes },
            requestConfirmation = { },
        )

        val snippet = CommandSnippet(
            id = "s-6",
            name = "Reboot",
            command = "sudo reboot",
            autoSend = true,
            requireAuth = true,
        )

        handler.onSnippetConfirmed(snippet)

        val expected = "sudo reboot\r".toByteArray(Charsets.UTF_8)
        assertArrayEquals(expected, sentBytes)
    }

    @Test
    fun testOnSnippetConfirmedDispatchesManualSnippet() {
        var sentBytes: ByteArray? = null
        val handler = TerminalSnippetHandler(
            sendToTerminal = { bytes -> sentBytes = bytes },
            requestConfirmation = { },
        )

        val snippet = CommandSnippet(
            id = "s-7",
            name = "Custom Script Prompt",
            command = "./deploy.sh --prod",
            autoSend = false,
            requireAuth = true,
        )

        handler.onSnippetConfirmed(snippet)

        val expected = "./deploy.sh --prod".toByteArray(Charsets.UTF_8)
        assertArrayEquals(expected, sentBytes)
    }

    @Test
    fun testFormatCommandPreservesUtf8AndSpecialCharacters() {
        val snippet = CommandSnippet(
            id = "s-8",
            name = "Echo Unicode",
            command = "echo '🚀 CoSSH Terminal' && grep -r \"test\" .",
            autoSend = true,
            requireAuth = false,
        )

        val formatted = TerminalSnippetHandler.formatCommand(snippet)
        val decoded = String(formatted, Charsets.UTF_8)
        assertEquals("echo '🚀 CoSSH Terminal' && grep -r \"test\" .\r", decoded)
        assertTrue(decoded.contains("🚀"))
    }
}
