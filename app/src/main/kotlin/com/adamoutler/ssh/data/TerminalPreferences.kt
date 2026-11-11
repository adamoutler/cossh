package com.adamoutler.ssh.data

enum class TerminalVolumeKeysAction(val id: String, val displayName: String) {
    SYSTEM_VOLUME("SYSTEM_VOLUME", "System Volume"),
    ZOOM_FONT("ZOOM_FONT", "Zoom Font Size"),
    ;

    companion object {
        fun fromId(id: String?): TerminalVolumeKeysAction = entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: SYSTEM_VOLUME
    }
}

enum class TerminalBackButtonBehavior(val id: String, val displayName: String) {
    PROMPT_DIALOG("PROMPT_DIALOG", "Prompt Dialog (Keep Alive / Exit)"),
    SEND_ESC("SEND_ESC", "Send ESC Key"),
    BACKGROUND_SESSION("BACKGROUND_SESSION", "Background Immediately"),
    ;

    companion object {
        fun fromId(id: String?): TerminalBackButtonBehavior = entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: PROMPT_DIALOG
    }
}

enum class TerminalUrlConfirmationMode(val id: String, val displayName: String) {
    SUSPICIOUS_ONLY("SUSPICIOUS_ONLY", "Suspicious Links Only"),
    ALWAYS("ALWAYS", "Always Confirm"),
    NEVER("NEVER", "Open Immediately"),
    ;

    companion object {
        fun fromId(id: String?): TerminalUrlConfirmationMode = entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: SUSPICIOUS_ONLY
    }
}
