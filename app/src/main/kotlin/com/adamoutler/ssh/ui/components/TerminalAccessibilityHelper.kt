package com.adamoutler.ssh.ui.components

/**
 * Helper utility for extracting sanitized, screen-reader-friendly text from terminal
 * transcripts, stripping ANSI control escape sequences, and formatting polite TalkBack
 * announcements for terminal updates and modifier key transitions.
 */
object TerminalAccessibilityHelper {

    // Regular expressions for terminal escape code sanitization
    // 1. Control Sequence Introducer (CSI): ESC [ ...
    private val CSI_REGEX = Regex("""\u001B\[[0-9:;<=>?]*[ -/]*[@-~]""")
    // 2. Operating System Command (OSC): ESC ] ... BEL or ESC \
    private val OSC_REGEX = Regex("""\u001B\].*?(?:\u0007|\u001B\\)""")
    // 3. Two-character escape sequences: ESC followed by ASCII 0x40-0x5F
    private val ESC_2CHAR_REGEX = Regex("""\u001B[@-Z\\-_]""")
    // 4. Non-printable control characters except newline and tab
    private val NON_PRINTABLE_REGEX = Regex("""[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]""")

    /**
     * Strips ANSI escape sequences, OSC control sequences, and binary control characters
     * from terminal output, normalizing line breaks.
     */
    fun stripAnsiControlCodes(raw: String): String {
        if (raw.isEmpty()) return ""
        var cleaned = OSC_REGEX.replace(raw, "")
        cleaned = CSI_REGEX.replace(cleaned, "")
        cleaned = ESC_2CHAR_REGEX.replace(cleaned, "")
        cleaned = NON_PRINTABLE_REGEX.replace(cleaned, "")
        return cleaned.replace("\r\n", "\n").replace('\r', '\n')
    }

    /**
     * Extracts sanitized, accessible lines from a raw terminal transcript, returning up to
     * [maxLines] of the most recent visible content.
     */
    fun extractAccessibleLines(rawTranscript: String, maxLines: Int = 50): List<String> {
        require(maxLines > 0) { "maxLines must be greater than zero, was $maxLines" }
        if (rawTranscript.isEmpty()) return emptyList()

        val sanitized = stripAnsiControlCodes(rawTranscript)
        val lines = sanitized.split('\n')
        return if (lines.size <= maxLines) {
            lines
        } else {
            lines.takeLast(maxLines)
        }
    }

    /**
     * Formats a polite TalkBack announcement when a modifier key state transitions
     * between INACTIVE (Active: Off), STICKY, and LOCKED.
     */
    fun buildModifierAnnouncement(modifierName: String, state: ModifierState): String {
        val stateLabel = when (state) {
            ModifierState.INACTIVE -> "Active: Off"
            ModifierState.STICKY -> "Sticky"
            ModifierState.LOCKED -> "Locked"
        }
        return "$modifierName $stateLabel"
    }

    /**
     * Builds a polite live-region announcement for newly received terminal output,
     * stripping control codes and bounding the character length to avoid overwhelming
     * the screen reader speech queue.
     */
    fun buildOutputAnnouncement(newOutput: String, maxChars: Int = 200): String {
        val sanitized = stripAnsiControlCodes(newOutput).trim()
        if (sanitized.isEmpty()) return ""
        return if (sanitized.length <= maxChars) {
            sanitized
        } else {
            sanitized.take(maxChars).trimEnd() + "…"
        }
    }
}

/**
 * Top-level delegator for [TerminalAccessibilityHelper.extractAccessibleLines].
 */
fun extractAccessibleLines(rawTranscript: String, maxLines: Int = 50): List<String> {
    return TerminalAccessibilityHelper.extractAccessibleLines(rawTranscript, maxLines)
}

/**
 * Top-level delegator for [TerminalAccessibilityHelper.stripAnsiControlCodes].
 */
fun stripAnsiControlCodes(raw: String): String {
    return TerminalAccessibilityHelper.stripAnsiControlCodes(raw)
}

/**
 * Top-level delegator for [TerminalAccessibilityHelper.buildModifierAnnouncement].
 */
fun buildModifierAnnouncement(modifierName: String, state: ModifierState): String {
    return TerminalAccessibilityHelper.buildModifierAnnouncement(modifierName, state)
}

/**
 * Top-level delegator for [TerminalAccessibilityHelper.buildOutputAnnouncement].
 */
fun buildOutputAnnouncement(newOutput: String, maxChars: Int = 200): String {
    return TerminalAccessibilityHelper.buildOutputAnnouncement(newOutput, maxChars)
}
