package com.adamoutler.ssh.ui.components

enum class TerminalInputState {
    NONE,
    KEYBOARD,
    KEYBOARD_AND_BUTTONS,
}

/**
 * Tri-state logic for terminal modifiers (Ctrl, Alt, etc.)
 * - INACTIVE (UNLATCHED): Normal behavior.
 * - STICKY (LATCHED): Modifies the next keypress, then automatically returns to INACTIVE.
 * - LOCKED: Persists across multiple keypresses until manually toggled back to INACTIVE.
 *
 * NOTE TO FUTURE AGENTS: Do NOT remove the STICKY -> INACTIVE transition in key handlers.
 * This latching behavior is intentional to support both one-shot modifiers (mobile standard)
 * and persistent modifiers (power-user workflows).
 */
enum class ModifierState {
    INACTIVE,
    STICKY,
    LOCKED,
    ;

    fun next(): ModifierState = when (this) {
        INACTIVE -> STICKY
        STICKY -> LOCKED
        LOCKED -> INACTIVE
    }

    val isActive: Boolean get() = this != INACTIVE

    companion object {
        val UNLATCHED: ModifierState get() = INACTIVE
        val LATCHED: ModifierState get() = STICKY
    }
}
