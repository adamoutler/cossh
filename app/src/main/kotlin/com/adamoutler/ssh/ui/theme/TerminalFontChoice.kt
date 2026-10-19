package com.adamoutler.ssh.ui.theme

import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily

/**
 * Curated monospace font choices for the CoSSH terminal.
 * Safely falls back to [Typeface.MONOSPACE] if a requested typeface is unavailable on the host OS.
 */
enum class TerminalFontChoice(
    val id: String,
    val displayName: String,
    val systemFontName: String,
) {
    DEFAULT_MONOSPACE(
        id = "DEFAULT_MONOSPACE",
        displayName = "Default Monospace",
        systemFontName = "monospace",
    ),
    JETBRAINS_MONO(
        id = "JETBRAINS_MONO",
        displayName = "JetBrains Mono",
        systemFontName = "jetbrains-mono",
    ),
    FIRA_CODE(
        id = "FIRA_CODE",
        displayName = "Fira Code",
        systemFontName = "fira-code",
    ),
    INCONSOLATA(
        id = "INCONSOLATA",
        displayName = "Inconsolata",
        systemFontName = "inconsolata",
    ),
    SOURCE_CODE_PRO(
        id = "SOURCE_CODE_PRO",
        displayName = "Source Code Pro",
        systemFontName = "source-code-pro",
    );

    /**
     * Resolves the Android [Typeface] for the Termux TerminalView.
     * Guaranteed to fall back to [Typeface.MONOSPACE].
     */
    fun getTypeface(): Typeface {
        return try {
            if (this == DEFAULT_MONOSPACE) {
                Typeface.MONOSPACE
            } else {
                Typeface.create(systemFontName, Typeface.NORMAL) ?: Typeface.MONOSPACE
            }
        } catch (_: Throwable) {
            Typeface.MONOSPACE
        }
    }

    /**
     * Resolves the Jetpack Compose [FontFamily] for headless preview cards and UI text.
     */
    val composeFontFamily: FontFamily
        get() = try {
            FontFamily(getTypeface())
        } catch (_: Throwable) {
            FontFamily.Monospace
        }

    companion object {
        fun fromId(id: String?): TerminalFontChoice =
            entries.firstOrNull { it.name.equals(id, ignoreCase = true) || it.id.equals(id, ignoreCase = true) }
                ?: DEFAULT_MONOSPACE
    }
}
