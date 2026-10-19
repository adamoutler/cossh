package com.adamoutler.ssh.ui.theme

import com.termux.terminal.TerminalSession
import com.termux.terminal.TextStyle
import com.termux.view.TerminalView

/**
 * Curated color themes for the CoSSH terminal.
 * Provides complete 16-color ANSI palettes, foreground, background, and cursor colors.
 */
enum class TerminalTheme(
    val id: String,
    val displayName: String,
    val background: Int,
    val foreground: Int,
    val cursor: Int,
    val ansiColors: IntArray,
) {
    COBALT_BLUE(
        id = "COBALT_BLUE",
        displayName = "Cobalt Blue",
        background = 0xFF001933.toInt(),
        foreground = 0xFFE0F2FE.toInt(),
        cursor = 0xFF38BDF8.toInt(),
        ansiColors = intArrayOf(
            0xFF001020.toInt(), // 0: Black
            0xFFEF4444.toInt(), // 1: Red
            0xFF22C55E.toInt(), // 2: Green
            0xFFEAB308.toInt(), // 3: Yellow
            0xFF3B82F6.toInt(), // 4: Blue
            0xFFA855F7.toInt(), // 5: Magenta
            0xFF06B6D4.toInt(), // 6: Cyan
            0xFFE2E8F0.toInt(), // 7: White
            0xFF1E293B.toInt(), // 8: Bright Black
            0xFFF87171.toInt(), // 9: Bright Red
            0xFF4ADE80.toInt(), // 10: Bright Green
            0xFFFDE047.toInt(), // 11: Bright Yellow
            0xFF60A5FA.toInt(), // 12: Bright Blue
            0xFFC084FC.toInt(), // 13: Bright Magenta
            0xFF22D3EE.toInt(), // 14: Bright Cyan
            0xFFF8FAFC.toInt(), // 15: Bright White
        ),
    ),
    SOLARIZED_DARK(
        id = "SOLARIZED_DARK",
        displayName = "Solarized Dark",
        background = 0xFF002B36.toInt(),
        foreground = 0xFF839496.toInt(),
        cursor = 0xFF93A1A1.toInt(),
        ansiColors = intArrayOf(
            0xFF073642.toInt(), // 0: Black
            0xFFDC322F.toInt(), // 1: Red
            0xFF859900.toInt(), // 2: Green
            0xFFB58900.toInt(), // 3: Yellow
            0xFF268BD2.toInt(), // 4: Blue
            0xFFD33682.toInt(), // 5: Magenta
            0xFF2AA198.toInt(), // 6: Cyan
            0xFFEEE8D5.toInt(), // 7: White
            0xFF002B36.toInt(), // 8: Bright Black
            0xFFCB4B16.toInt(), // 9: Bright Red (Orange)
            0xFF586E75.toInt(), // 10: Bright Green (Base01)
            0xFF657B83.toInt(), // 11: Bright Yellow (Base00)
            0xFF839496.toInt(), // 12: Bright Blue (Base0)
            0xFF6C71C4.toInt(), // 13: Bright Magenta (Violet)
            0xFF93A1A1.toInt(), // 14: Bright Cyan (Base1)
            0xFFFDF6E3.toInt(), // 15: Bright White (Base3)
        ),
    ),
    MONOKAI(
        id = "MONOKAI",
        displayName = "Monokai",
        background = 0xFF272822.toInt(),
        foreground = 0xFFF8F8F2.toInt(),
        cursor = 0xFFF8F8F0.toInt(),
        ansiColors = intArrayOf(
            0xFF272822.toInt(), // 0: Black
            0xFFF92672.toInt(), // 1: Red
            0xFFA6E22E.toInt(), // 2: Green
            0xFFE6DB74.toInt(), // 3: Yellow
            0xFF66D9EF.toInt(), // 4: Blue
            0xFFAE81FF.toInt(), // 5: Magenta
            0xFFA1EFE4.toInt(), // 6: Cyan
            0xFFF8F8F2.toInt(), // 7: White
            0xFF75715E.toInt(), // 8: Bright Black
            0xFFF92672.toInt(), // 9: Bright Red
            0xFFA6E22E.toInt(), // 10: Bright Green
            0xFFE6DB74.toInt(), // 11: Bright Yellow
            0xFF66D9EF.toInt(), // 12: Bright Blue
            0xFFAE81FF.toInt(), // 13: Bright Magenta
            0xFFA1EFE4.toInt(), // 14: Bright Cyan
            0xFFF9F8F5.toInt(), // 15: Bright White
        ),
    ),
    DRACULA(
        id = "DRACULA",
        displayName = "Dracula",
        background = 0xFF282A36.toInt(),
        foreground = 0xFFF8F8F2.toInt(),
        cursor = 0xFFFF79C6.toInt(),
        ansiColors = intArrayOf(
            0xFF21222C.toInt(), // 0: Black
            0xFFFF5555.toInt(), // 1: Red
            0xFF50FA7B.toInt(), // 2: Green
            0xFFF1FA8C.toInt(), // 3: Yellow
            0xFFBD93F9.toInt(), // 4: Blue
            0xFFFF79C6.toInt(), // 5: Magenta
            0xFF8BE9FD.toInt(), // 6: Cyan
            0xFFF8F8F2.toInt(), // 7: White
            0xFF6272A4.toInt(), // 8: Bright Black
            0xFFFF6E6E.toInt(), // 9: Bright Red
            0xFF69FF94.toInt(), // 10: Bright Green
            0xFFFFFFA5.toInt(), // 11: Bright Yellow
            0xFFD6ACFF.toInt(), // 12: Bright Blue
            0xFFFF92DF.toInt(), // 13: Bright Magenta
            0xFFA4FFFF.toInt(), // 14: Bright Cyan
            0xFFFFFFFF.toInt(), // 15: Bright White
        ),
    );

    /**
     * Applies this theme to the provided [TerminalView] and its [TerminalSession] emulator.
     */
    fun applyTo(terminalView: TerminalView, session: TerminalSession?) {
        terminalView.setBackgroundColor(background)
        val emulator = session?.emulator
        if (emulator != null) {
            val colors = emulator.mColors.mCurrentColors
            for (i in 0 until minOf(16, ansiColors.size)) {
                if (i < colors.size) {
                    colors[i] = ansiColors[i]
                }
            }
            if (TextStyle.COLOR_INDEX_FOREGROUND < colors.size) {
                colors[TextStyle.COLOR_INDEX_FOREGROUND] = foreground
            }
            if (TextStyle.COLOR_INDEX_BACKGROUND < colors.size) {
                colors[TextStyle.COLOR_INDEX_BACKGROUND] = background
            }
            if (TextStyle.COLOR_INDEX_CURSOR < colors.size) {
                colors[TextStyle.COLOR_INDEX_CURSOR] = cursor
            }
        }
        terminalView.onScreenUpdated()
    }

    companion object {
        fun fromId(id: String?): TerminalTheme =
            entries.firstOrNull { it.name.equals(id, ignoreCase = true) || it.id.equals(id, ignoreCase = true) }
                ?: COBALT_BLUE
    }
}
