package com.adamoutler.ssh.ui.components

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Identifier for built-in and selectable extra keys row presets.
 */
enum class ExtraKeysPresetId {
    DEFAULT,
    VIM_CODING,
    TMUX,
    NAVIGATION,
}

/**
 * Specification for an individual key in the terminal extra keys row.
 *
 * @property id Unique identifier for the key within a preset.
 * @property label User-visible text displayed on the key cap.
 * @property contentDescription Accessibility label read by screen readers (e.g. TalkBack).
 * @property payload Bytes or string sequence dispatched to the PTY / remote shell.
 * @property isModifier True if this key latches or toggles modifier state (Ctrl, Alt, etc.).
 * @property isRepeatable True if holding this key triggers auto-repeat (e.g. arrows, backspace).
 */
@Serializable
data class ExtraKeySpec(
    val id: String,
    val label: String,
    val contentDescription: String,
    val payload: String,
    val isModifier: Boolean = false,
    val isRepeatable: Boolean = false,
)

/**
 * Registry of standard extra key presets, custom key validation rules,
 * and JSON serialization/deserialization utilities.
 */
object ExtraKeysPresets {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        prettyPrint = false
    }

    private val ID_REGEX = Regex("^[a-zA-Z0-9_\\-]+$")

    /**
     * Default mobile terminal preset: standard modifiers, navigation, Esc, Tab, Ctrl-C.
     */
    val DEFAULT_PRESET: List<List<ExtraKeySpec>> = listOf(
        listOf(
            ExtraKeySpec(id = "esc", label = "Esc", contentDescription = "Escape key", payload = "\u001b", isModifier = false, isRepeatable = false),
            ExtraKeySpec(id = "ctrl_c", label = "Ctrl-C", contentDescription = "Control-C interrupt", payload = "\u0003", isModifier = false, isRepeatable = true),
            ExtraKeySpec(id = "menu", label = "Menu", contentDescription = "Menu modifier", payload = "", isModifier = true, isRepeatable = false),
            ExtraKeySpec(id = "up", label = "↑", contentDescription = "Up arrow", payload = "\u001b[A", isModifier = false, isRepeatable = true),
            ExtraKeySpec(id = "tab", label = "Tab", contentDescription = "Tab key", payload = "\t", isModifier = false, isRepeatable = false),
            ExtraKeySpec(id = "home", label = "Home", contentDescription = "Home key", payload = "\u001b[H", isModifier = false, isRepeatable = true),
        ),
        listOf(
            ExtraKeySpec(id = "ctrl", label = "Ctrl", contentDescription = "Control modifier", payload = "", isModifier = true, isRepeatable = false),
            ExtraKeySpec(id = "alt", label = "Alt", contentDescription = "Alt modifier", payload = "", isModifier = true, isRepeatable = false),
            ExtraKeySpec(id = "left", label = "←", contentDescription = "Left arrow", payload = "\u001b[D", isModifier = false, isRepeatable = true),
            ExtraKeySpec(id = "down", label = "↓", contentDescription = "Down arrow", payload = "\u001b[B", isModifier = false, isRepeatable = true),
            ExtraKeySpec(id = "right", label = "→", contentDescription = "Right arrow", payload = "\u001b[C", isModifier = false, isRepeatable = true),
            ExtraKeySpec(id = "end", label = "End", contentDescription = "End key", payload = "\u001b[F", isModifier = false, isRepeatable = true),
        ),
    )

    /**
     * Preset tailored for Vim / Neovim coding: Esc, colon, search slash, pipe, tilde, tab, braces, brackets, redo.
     */
    val VIM_CODING_PRESET: List<List<ExtraKeySpec>> = listOf(
        listOf(
            ExtraKeySpec(id = "vim_esc", label = "Esc", contentDescription = "Escape to normal mode", payload = "\u001b", isModifier = false, isRepeatable = false),
            ExtraKeySpec(id = "vim_colon", label = ":", contentDescription = "Colon command prompt", payload = ":", isModifier = false, isRepeatable = false),
            ExtraKeySpec(id = "vim_slash", label = "/", contentDescription = "Forward slash search", payload = "/", isModifier = false, isRepeatable = false),
            ExtraKeySpec(id = "vim_pipe", label = "|", contentDescription = "Pipe character", payload = "|", isModifier = false, isRepeatable = false),
            ExtraKeySpec(id = "vim_tilde", label = "~", contentDescription = "Tilde character", payload = "~", isModifier = false, isRepeatable = false),
            ExtraKeySpec(id = "vim_tab", label = "Tab", contentDescription = "Tab indentation", payload = "\t", isModifier = false, isRepeatable = false),
        ),
        listOf(
            ExtraKeySpec(id = "vim_brace_open", label = "{", contentDescription = "Left curly brace", payload = "{", isModifier = false, isRepeatable = false),
            ExtraKeySpec(id = "vim_brace_close", label = "}", contentDescription = "Right curly brace", payload = "}", isModifier = false, isRepeatable = false),
            ExtraKeySpec(id = "vim_bracket_open", label = "[", contentDescription = "Left square bracket", payload = "[", isModifier = false, isRepeatable = false),
            ExtraKeySpec(id = "vim_bracket_close", label = "]", contentDescription = "Right square bracket", payload = "]", isModifier = false, isRepeatable = false),
            ExtraKeySpec(id = "vim_ctrl_c", label = "Ctrl-C", contentDescription = "Cancel or interrupt", payload = "\u0003", isModifier = false, isRepeatable = true),
            ExtraKeySpec(id = "vim_ctrl_r", label = "Ctrl-R", contentDescription = "Redo change", payload = "\u0012", isModifier = false, isRepeatable = false),
        ),
    )

    /**
     * Preset optimized for tmux terminal multiplexing: Ctrl-B prefix, splits, zoom, window management.
     */
    val TMUX_PRESET: List<List<ExtraKeySpec>> = listOf(
        listOf(
            ExtraKeySpec(id = "tmux_prefix", label = "Ctrl-B", contentDescription = "Tmux prefix", payload = "\u0002", isModifier = false, isRepeatable = false),
            ExtraKeySpec(id = "tmux_split_vert", label = "%", contentDescription = "Tmux split vertical", payload = "\u0002%", isModifier = false, isRepeatable = false),
            ExtraKeySpec(id = "tmux_split_horiz", label = "\"", contentDescription = "Tmux split horizontal", payload = "\u0002\"", isModifier = false, isRepeatable = false),
            ExtraKeySpec(id = "tmux_zoom", label = "z", contentDescription = "Tmux zoom pane toggle", payload = "\u0002z", isModifier = false, isRepeatable = false),
            ExtraKeySpec(id = "tmux_new", label = "c", contentDescription = "Tmux new window", payload = "\u0002c", isModifier = false, isRepeatable = false),
            ExtraKeySpec(id = "tmux_detach", label = "d", contentDescription = "Tmux detach session", payload = "\u0002d", isModifier = false, isRepeatable = false),
        ),
        listOf(
            ExtraKeySpec(id = "tmux_next", label = "n", contentDescription = "Tmux next window", payload = "\u0002n", isModifier = false, isRepeatable = false),
            ExtraKeySpec(id = "tmux_prev", label = "p", contentDescription = "Tmux previous window", payload = "\u0002p", isModifier = false, isRepeatable = false),
            ExtraKeySpec(id = "tmux_kill", label = "x", contentDescription = "Tmux kill pane", payload = "\u0002x", isModifier = false, isRepeatable = false),
            ExtraKeySpec(id = "tmux_up", label = "↑", contentDescription = "Tmux pane up", payload = "\u0002\u001b[A", isModifier = false, isRepeatable = true),
            ExtraKeySpec(id = "tmux_down", label = "↓", contentDescription = "Tmux pane down", payload = "\u0002\u001b[B", isModifier = false, isRepeatable = true),
            ExtraKeySpec(id = "tmux_windows", label = "w", contentDescription = "Tmux list windows", payload = "\u0002w", isModifier = false, isRepeatable = false),
        ),
    )

    /**
     * Preset dedicated to navigation and cursor motion: PgUp, PgDn, Home, End, Ins, Del, arrows.
     */
    val NAVIGATION_PRESET: List<List<ExtraKeySpec>> = listOf(
        listOf(
            ExtraKeySpec(id = "nav_pgup", label = "PgUp", contentDescription = "Page Up", payload = "\u001b[5~", isModifier = false, isRepeatable = true),
            ExtraKeySpec(id = "nav_up", label = "↑", contentDescription = "Up arrow", payload = "\u001b[A", isModifier = false, isRepeatable = true),
            ExtraKeySpec(id = "nav_pgdn", label = "PgDn", contentDescription = "Page Down", payload = "\u001b[6~", isModifier = false, isRepeatable = true),
            ExtraKeySpec(id = "nav_home", label = "Home", contentDescription = "Home key", payload = "\u001b[H", isModifier = false, isRepeatable = true),
            ExtraKeySpec(id = "nav_end", label = "End", contentDescription = "End key", payload = "\u001b[F", isModifier = false, isRepeatable = true),
            ExtraKeySpec(id = "nav_ins", label = "Ins", contentDescription = "Insert key", payload = "\u001b[2~", isModifier = false, isRepeatable = true),
        ),
        listOf(
            ExtraKeySpec(id = "nav_left", label = "←", contentDescription = "Left arrow", payload = "\u001b[D", isModifier = false, isRepeatable = true),
            ExtraKeySpec(id = "nav_down", label = "↓", contentDescription = "Down arrow", payload = "\u001b[B", isModifier = false, isRepeatable = true),
            ExtraKeySpec(id = "nav_right", label = "→", contentDescription = "Right arrow", payload = "\u001b[C", isModifier = false, isRepeatable = true),
            ExtraKeySpec(id = "nav_del", label = "Del", contentDescription = "Delete key", payload = "\u001b[3~", isModifier = false, isRepeatable = true),
            ExtraKeySpec(id = "nav_tab", label = "Tab", contentDescription = "Tab key", payload = "\t", isModifier = false, isRepeatable = false),
            ExtraKeySpec(id = "nav_esc", label = "Esc", contentDescription = "Escape key", payload = "\u001b", isModifier = false, isRepeatable = false),
        ),
    )

    /**
     * Resolves the structured key rows for a given preset identifier.
     */
    fun getPreset(id: ExtraKeysPresetId): List<List<ExtraKeySpec>> = when (id) {
        ExtraKeysPresetId.DEFAULT -> DEFAULT_PRESET
        ExtraKeysPresetId.VIM_CODING -> VIM_CODING_PRESET
        ExtraKeysPresetId.TMUX -> TMUX_PRESET
        ExtraKeysPresetId.NAVIGATION -> NAVIGATION_PRESET
    }

    /**
     * Returns a user-friendly display name for a preset.
     */
    fun getDisplayName(id: ExtraKeysPresetId): String = when (id) {
        ExtraKeysPresetId.DEFAULT -> "Default Terminal"
        ExtraKeysPresetId.VIM_CODING -> "Vim / Coding"
        ExtraKeysPresetId.TMUX -> "tmux Multiplexer"
        ExtraKeysPresetId.NAVIGATION -> "Navigation & Motion"
    }

    /**
     * Validates a single [ExtraKeySpec] against security bounds and structural requirements.
     */
    fun validateKeySpec(spec: ExtraKeySpec): Boolean {
        if (spec.id.isBlank() || spec.id.length > 64 || !ID_REGEX.matches(spec.id)) {
            return false
        }
        if (spec.label.isBlank() || spec.label.length > 32) {
            return false
        }
        if (spec.contentDescription.isBlank() || spec.contentDescription.length > 128) {
            return false
        }
        if (!spec.isModifier && spec.payload.isEmpty()) {
            return false
        }
        if (spec.payload.length > 128) {
            return false
        }
        return true
    }

    /**
     * Validates an entire preset row layout, verifying key bounds, non-empty rows,
     * and uniqueness of key IDs.
     */
    fun validatePreset(rows: List<List<ExtraKeySpec>>): Boolean {
        if (rows.isEmpty()) return false
        val seenIds = mutableSetOf<String>()
        for (row in rows) {
            if (row.isEmpty()) return false
            if (row.size > 12) return false
            for (key in row) {
                if (!validateKeySpec(key)) return false
                if (!seenIds.add(key.id)) return false
            }
        }
        return true
    }

    /**
     * Serializes a preset rows structure into a compact JSON string.
     */
    fun serializePreset(specs: List<List<ExtraKeySpec>>): String {
        require(validatePreset(specs)) { "Cannot serialize invalid key preset" }
        return json.encodeToString(specs)
    }

    /**
     * Deserializes and validates a preset rows structure from JSON.
     * Throws [IllegalArgumentException] if the format is invalid or keys fail validation.
     */
    fun deserializePreset(serialized: String): List<List<ExtraKeySpec>> {
        require(serialized.isNotBlank()) { "Serialized preset string cannot be blank" }
        return try {
            val decoded = json.decodeFromString<List<List<ExtraKeySpec>>>(serialized)
            require(validatePreset(decoded)) { "Deserialized preset contains invalid or duplicate keys" }
            decoded
        } catch (e: Exception) {
            if (e is IllegalArgumentException) throw e
            throw IllegalArgumentException("Malformed preset JSON: ${e.message}", e)
        }
    }
}
