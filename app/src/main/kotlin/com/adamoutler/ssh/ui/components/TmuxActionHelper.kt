package com.adamoutler.ssh.ui.components

/**
 * Common tmux prefix key configurations.
 */
enum class TmuxPrefix(val byte: Byte, val displayLabel: String) {
    CTRL_B(0x02, "Ctrl+B"),
    CTRL_A(0x01, "Ctrl+A"),
}

/**
 * Common tmux multiplexer actions mapped to standard key sequences.
 */
enum class TmuxCommand(
    val displayName: String,
    val description: String,
    val keySequenceHint: String,
    val suffixBytes: ByteArray,
) {
    SPLIT_HORIZONTAL(
        displayName = "Split Horizontally",
        description = "Splits current pane horizontally into top and bottom panes",
        keySequenceHint = "\"",
        suffixBytes = byteArrayOf('"'.code.toByte()),
    ),
    SPLIT_VERTICAL(
        displayName = "Split Vertically",
        description = "Splits current pane vertically into left and right panes",
        keySequenceHint = "%",
        suffixBytes = byteArrayOf('%'.code.toByte()),
    ),
    PANE_UP(
        displayName = "Focus Pane Up",
        description = "Selects the pane above the active pane",
        keySequenceHint = "↑",
        suffixBytes = byteArrayOf(0x1B, '['.code.toByte(), 'A'.code.toByte()),
    ),
    PANE_DOWN(
        displayName = "Focus Pane Down",
        description = "Selects the pane below the active pane",
        keySequenceHint = "↓",
        suffixBytes = byteArrayOf(0x1B, '['.code.toByte(), 'B'.code.toByte()),
    ),
    PANE_LEFT(
        displayName = "Focus Pane Left",
        description = "Selects the pane to the left of the active pane",
        keySequenceHint = "←",
        suffixBytes = byteArrayOf(0x1B, '['.code.toByte(), 'D'.code.toByte()),
    ),
    PANE_RIGHT(
        displayName = "Focus Pane Right",
        description = "Selects the pane to the right of the active pane",
        keySequenceHint = "→",
        suffixBytes = byteArrayOf(0x1B, '['.code.toByte(), 'C'.code.toByte()),
    ),
    ZOOM_TOGGLE(
        displayName = "Zoom Pane Toggle",
        description = "Toggles full-window zoom for the active pane",
        keySequenceHint = "z",
        suffixBytes = byteArrayOf('z'.code.toByte()),
    ),
    NEW_WINDOW(
        displayName = "New Window",
        description = "Creates a new tmux window in the current session",
        keySequenceHint = "c",
        suffixBytes = byteArrayOf('c'.code.toByte()),
    ),
    NEXT_WINDOW(
        displayName = "Next Window",
        description = "Selects the next tmux window",
        keySequenceHint = "n",
        suffixBytes = byteArrayOf('n'.code.toByte()),
    ),
    PREV_WINDOW(
        displayName = "Previous Window",
        description = "Selects the previous tmux window",
        keySequenceHint = "p",
        suffixBytes = byteArrayOf('p'.code.toByte()),
    ),
    DETACH_SESSION(
        displayName = "Detach Session",
        description = "Detaches from the current tmux session while keeping it alive",
        keySequenceHint = "d",
        suffixBytes = byteArrayOf('d'.code.toByte()),
    ),
    KILL_PANE(
        displayName = "Kill Pane",
        description = "Terminates the active tmux pane",
        keySequenceHint = "x",
        suffixBytes = byteArrayOf('x'.code.toByte()),
    );
}

/**
 * Utility for formatting and building tmux multiplexer command byte sequences.
 */
object TmuxActionHelper {

    /**
     * Constructs the exact byte sequence to transmit to the PTY for a given tmux command.
     */
    fun buildCommandBytes(command: TmuxCommand, prefix: TmuxPrefix = TmuxPrefix.CTRL_B): ByteArray {
        val result = ByteArray(1 + command.suffixBytes.size)
        result[0] = prefix.byte
        System.arraycopy(command.suffixBytes, 0, result, 1, command.suffixBytes.size)
        return result
    }

    /**
     * Constructs the command sequence as a string payload suitable for terminal injection.
     */
    fun buildCommandPayload(command: TmuxCommand, prefix: TmuxPrefix = TmuxPrefix.CTRL_B): String {
        return String(buildCommandBytes(command, prefix), Charsets.ISO_8859_1)
    }

    /**
     * Finds a command matching the given identifier name or returns null if not recognized.
     */
    fun findCommand(name: String): TmuxCommand? {
        return TmuxCommand.values().firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

/**
 * Top-level function for constructing tmux command bytes.
 */
fun buildCommandBytes(command: TmuxCommand, prefix: TmuxPrefix = TmuxPrefix.CTRL_B): ByteArray {
    return TmuxActionHelper.buildCommandBytes(command, prefix)
}
