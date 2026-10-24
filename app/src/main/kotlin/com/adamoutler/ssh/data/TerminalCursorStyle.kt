package com.adamoutler.ssh.data

/**
 * Terminal cursor styles supported by Termux TerminalEmulator.
 *
 * Termux codes:
 * 0 = TERMINAL_CURSOR_STYLE_BLOCK
 * 1 = TERMINAL_CURSOR_STYLE_UNDERLINE
 * 2 = TERMINAL_CURSOR_STYLE_BAR
 */
enum class TerminalCursorStyle(val id: String, val styleCode: Int, val displayName: String) {
    BLOCK("BLOCK", 0, "Block"),
    UNDERLINE("UNDERLINE", 1, "Underline"),
    VERTICAL_BAR("BAR", 2, "Bar"),
    ;

    companion object {
        fun fromId(id: String?): TerminalCursorStyle = entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: BLOCK

        fun fromStyleCode(code: Int): TerminalCursorStyle = entries.firstOrNull { it.styleCode == code } ?: BLOCK
    }
}
