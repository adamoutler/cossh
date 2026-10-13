package com.adamoutler.ssh.ui.components

import com.adamoutler.ssh.data.CommandSnippet

/**
 * Encapsulates execution and confirmation logic for command snippets.
 * Ensures proper newline termination for auto-send and gates critical snippets behind confirmation.
 */
class TerminalSnippetHandler(
    private val sendToTerminal: (ByteArray) -> Unit,
    private val requestConfirmation: (CommandSnippet) -> Unit,
) {
    /**
     * Called when a snippet chip is clicked. If requireAuth is enabled,
     * routes to confirmation dialog before dispatching. Otherwise, dispatches immediately.
     */
    fun onSnippetClicked(snippet: CommandSnippet) {
        if (snippet.requireAuth) {
            requestConfirmation(snippet)
        } else {
            dispatchSnippet(snippet)
        }
    }

    /**
     * Called after confirmation has been approved by the user.
     */
    fun onSnippetConfirmed(snippet: CommandSnippet) {
        dispatchSnippet(snippet)
    }

    private fun dispatchSnippet(snippet: CommandSnippet) {
        val bytes = formatCommand(snippet)
        sendToTerminal(bytes)
    }

    companion object {
        /**
         * Formats the snippet command as UTF-8 bytes.
         * If autoSend is true, appends carriage return ('\r') to trigger execution in terminal PTY.
         */
        fun formatCommand(snippet: CommandSnippet): ByteArray {
            val commandText = if (snippet.autoSend) "${snippet.command}\r" else snippet.command
            return commandText.toByteArray(Charsets.UTF_8)
        }
    }
}
