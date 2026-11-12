package com.adamoutler.ssh.crypto

import android.content.Context
import android.content.SharedPreferences
import com.adamoutler.ssh.data.TerminalBackButtonBehavior
import com.adamoutler.ssh.data.TerminalCursorStyle
import com.adamoutler.ssh.data.TerminalUrlConfirmationMode
import com.adamoutler.ssh.data.TerminalVolumeKeysAction
import com.adamoutler.ssh.ui.components.ExtraKeysPresetId

/**
 * SettingsManager handles non-sensitive global application preferences.
 */
class SettingsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("cossh_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_GLOBAL_FONT_SIZE = "global_font_size"
        private const val DEFAULT_FONT_SIZE = 14
        private const val KEY_DEFAULT_GROUP_NAME = "default_group_name"
        private const val DEFAULT_GROUP_NAME = "Uncategorized"
        private const val KEY_GLOBAL_COLOR_THEME = "global_color_theme"
        private const val DEFAULT_COLOR_THEME = "COBALT_BLUE"
        private const val KEY_GLOBAL_FONT_CHOICE = "global_font_choice"
        private const val DEFAULT_FONT_CHOICE = "DEFAULT_MONOSPACE"
        private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        private const val DEFAULT_APP_LOCK_ENABLED = false
        private const val KEY_APP_LOCK_TIMEOUT_SECONDS = "app_lock_timeout_seconds"
        const val DEFAULT_APP_LOCK_TIMEOUT_SECONDS = 60
        private const val KEY_IS_CLOUD_SYNC_ACTIVE = "is_cloud_sync_active"
        private const val KEY_GLOBAL_CURSOR_STYLE = "global_cursor_style"
        private const val DEFAULT_CURSOR_STYLE = "BLOCK"
        private const val KEY_GLOBAL_CURSOR_BLINK = "global_cursor_blink"
        private const val DEFAULT_CURSOR_BLINK = true
        private const val KEY_GLOBAL_EXTRA_KEYS_PRESET = "global_extra_keys_preset"
        private const val DEFAULT_EXTRA_KEYS_PRESET = "DEFAULT"
        private const val KEY_ALLOW_OSC52_CLIPBOARD = "allow_osc52_clipboard"
        private const val DEFAULT_ALLOW_OSC52_CLIPBOARD = true

        const val KEY_SCREENSHOT_BLOCKING_ENABLED = "is_screenshot_blocking_enabled"
        private const val DEFAULT_SCREENSHOT_BLOCKING_ENABLED = false
        const val KEY_OVERLAY_TOUCH_BLOCKING_ENABLED = "is_overlay_touch_blocking_enabled"
        private const val DEFAULT_OVERLAY_TOUCH_BLOCKING_ENABLED = false
        const val KEY_CLIPBOARD_AUTO_CLEAR_SECONDS = "clipboard_auto_clear_seconds"
        private const val DEFAULT_CLIPBOARD_AUTO_CLEAR_SECONDS = 0
        const val KEY_CLIPBOARD_SENSITIVE_TAGGING_ENABLED = "is_clipboard_sensitive_tagging_enabled"
        private const val DEFAULT_CLIPBOARD_SENSITIVE_TAGGING_ENABLED = true
        const val KEY_ENABLE_SECURITY_AUDIT_LOG = "enable_security_audit_log"
        private const val DEFAULT_ENABLE_SECURITY_AUDIT_LOG = true
        const val KEY_HOLD_TO_CONFIRM_HOST_KEY_ENABLED = "is_hold_to_confirm_host_key_enabled"
        private const val DEFAULT_HOLD_TO_CONFIRM_HOST_KEY_ENABLED = true
        const val KEY_TERMINAL_URL_CONFIRMATION_MODE = "terminal_url_confirmation_mode"
        private const val DEFAULT_TERMINAL_URL_CONFIRMATION_MODE = "SUSPICIOUS_ONLY"

        const val KEY_VOLUME_KEYS_ACTION = "terminal_volume_keys_action"
        private const val DEFAULT_VOLUME_KEYS_ACTION = "SYSTEM_VOLUME"
        const val KEY_PINCH_TO_ZOOM_ENABLED = "terminal_pinch_to_zoom_enabled"
        private const val DEFAULT_PINCH_TO_ZOOM_ENABLED = true
        const val KEY_BELL_HAPTIC_ENABLED = "terminal_bell_haptic_enabled"
        private const val DEFAULT_BELL_HAPTIC_ENABLED = true
        const val KEY_BACK_BUTTON_BEHAVIOR = "terminal_back_button_behavior"
        private const val DEFAULT_BACK_BUTTON_BEHAVIOR = "PROMPT_DIALOG"
        const val KEY_ENFORCE_CHAR_BASED_INPUT = "enforce_char_based_input"
        private const val DEFAULT_ENFORCE_CHAR_BASED_INPUT = false
        const val KEY_POCKET_DETECTION_ENABLED = "pocket_detection_enabled"
        private const val DEFAULT_POCKET_DETECTION_ENABLED = true
    }

    fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    var allowOsc52Clipboard: Boolean
        get() = prefs.getBoolean(KEY_ALLOW_OSC52_CLIPBOARD, DEFAULT_ALLOW_OSC52_CLIPBOARD)
        set(value) = prefs.edit().putBoolean(KEY_ALLOW_OSC52_CLIPBOARD, value).apply()

    var globalCursorStyle: String
        get() = prefs.getString(KEY_GLOBAL_CURSOR_STYLE, DEFAULT_CURSOR_STYLE) ?: DEFAULT_CURSOR_STYLE
        set(value) = prefs.edit().putString(KEY_GLOBAL_CURSOR_STYLE, value).apply()

    var cursorStyle: TerminalCursorStyle
        get() = TerminalCursorStyle.fromId(globalCursorStyle)
        set(value) {
            globalCursorStyle = value.id
        }

    var globalCursorBlink: Boolean
        get() = prefs.getBoolean(KEY_GLOBAL_CURSOR_BLINK, DEFAULT_CURSOR_BLINK)
        set(value) = prefs.edit().putBoolean(KEY_GLOBAL_CURSOR_BLINK, value).apply()

    var isCloudSyncActive: Boolean
        get() = prefs.getBoolean(KEY_IS_CLOUD_SYNC_ACTIVE, true)
        set(value) = prefs.edit().putBoolean(KEY_IS_CLOUD_SYNC_ACTIVE, value).apply()

    var globalFontSize: Int
        get() = prefs.getInt(KEY_GLOBAL_FONT_SIZE, DEFAULT_FONT_SIZE)
        set(value) = prefs.edit().putInt(KEY_GLOBAL_FONT_SIZE, value).apply()

    var defaultGroupName: String
        get() = prefs.getString(KEY_DEFAULT_GROUP_NAME, DEFAULT_GROUP_NAME) ?: DEFAULT_GROUP_NAME
        set(value) = prefs.edit().putString(KEY_DEFAULT_GROUP_NAME, value).apply()

    var globalColorTheme: String
        get() = prefs.getString(KEY_GLOBAL_COLOR_THEME, DEFAULT_COLOR_THEME) ?: DEFAULT_COLOR_THEME
        set(value) = prefs.edit().putString(KEY_GLOBAL_COLOR_THEME, value).apply()

    var globalFontChoice: String
        get() = prefs.getString(KEY_GLOBAL_FONT_CHOICE, DEFAULT_FONT_CHOICE) ?: DEFAULT_FONT_CHOICE
        set(value) = prefs.edit().putString(KEY_GLOBAL_FONT_CHOICE, value).apply()

    var isAppLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_APP_LOCK_ENABLED, DEFAULT_APP_LOCK_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_APP_LOCK_ENABLED, value).apply()

    var appLockTimeoutSeconds: Int
        get() = prefs.getInt(KEY_APP_LOCK_TIMEOUT_SECONDS, DEFAULT_APP_LOCK_TIMEOUT_SECONDS)
        set(value) = prefs.edit().putInt(KEY_APP_LOCK_TIMEOUT_SECONDS, value).apply()

    var globalExtraKeysPreset: String
        get() = prefs.getString(KEY_GLOBAL_EXTRA_KEYS_PRESET, DEFAULT_EXTRA_KEYS_PRESET) ?: DEFAULT_EXTRA_KEYS_PRESET
        set(value) = prefs.edit().putString(KEY_GLOBAL_EXTRA_KEYS_PRESET, value).apply()

    var extraKeysPreset: ExtraKeysPresetId
        get() = try {
            ExtraKeysPresetId.valueOf(globalExtraKeysPreset)
        } catch (_: IllegalArgumentException) {
            ExtraKeysPresetId.DEFAULT
        }
        set(value) {
            globalExtraKeysPreset = value.name
        }

    var isScreenshotBlockingEnabled: Boolean
        get() = prefs.getBoolean(KEY_SCREENSHOT_BLOCKING_ENABLED, DEFAULT_SCREENSHOT_BLOCKING_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_SCREENSHOT_BLOCKING_ENABLED, value).apply()

    var isOverlayTouchBlockingEnabled: Boolean
        get() = prefs.getBoolean(KEY_OVERLAY_TOUCH_BLOCKING_ENABLED, DEFAULT_OVERLAY_TOUCH_BLOCKING_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_OVERLAY_TOUCH_BLOCKING_ENABLED, value).apply()

    var clipboardAutoClearSeconds: Int
        get() = prefs.getInt(KEY_CLIPBOARD_AUTO_CLEAR_SECONDS, DEFAULT_CLIPBOARD_AUTO_CLEAR_SECONDS)
        set(value) = prefs.edit().putInt(KEY_CLIPBOARD_AUTO_CLEAR_SECONDS, value).apply()

    var isClipboardSensitiveTaggingEnabled: Boolean
        get() = prefs.getBoolean(KEY_CLIPBOARD_SENSITIVE_TAGGING_ENABLED, DEFAULT_CLIPBOARD_SENSITIVE_TAGGING_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_CLIPBOARD_SENSITIVE_TAGGING_ENABLED, value).apply()

    var enableSecurityAuditLog: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_SECURITY_AUDIT_LOG, DEFAULT_ENABLE_SECURITY_AUDIT_LOG)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_SECURITY_AUDIT_LOG, value).apply()

    var isHoldToConfirmHostKeyEnabled: Boolean
        get() = prefs.getBoolean(KEY_HOLD_TO_CONFIRM_HOST_KEY_ENABLED, DEFAULT_HOLD_TO_CONFIRM_HOST_KEY_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_HOLD_TO_CONFIRM_HOST_KEY_ENABLED, value).apply()

    var terminalUrlConfirmationMode: TerminalUrlConfirmationMode
        get() = TerminalUrlConfirmationMode.fromId(
            prefs.getString(KEY_TERMINAL_URL_CONFIRMATION_MODE, DEFAULT_TERMINAL_URL_CONFIRMATION_MODE),
        )
        set(value) = prefs.edit().putString(KEY_TERMINAL_URL_CONFIRMATION_MODE, value.id).apply()

    var volumeKeysAction: TerminalVolumeKeysAction
        get() = TerminalVolumeKeysAction.fromId(
            prefs.getString(KEY_VOLUME_KEYS_ACTION, DEFAULT_VOLUME_KEYS_ACTION),
        )
        set(value) = prefs.edit().putString(KEY_VOLUME_KEYS_ACTION, value.id).apply()

    var isPinchToZoomEnabled: Boolean
        get() = prefs.getBoolean(KEY_PINCH_TO_ZOOM_ENABLED, DEFAULT_PINCH_TO_ZOOM_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_PINCH_TO_ZOOM_ENABLED, value).apply()

    var isTerminalBellHapticEnabled: Boolean
        get() = prefs.getBoolean(KEY_BELL_HAPTIC_ENABLED, DEFAULT_BELL_HAPTIC_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_BELL_HAPTIC_ENABLED, value).apply()

    var backButtonBehavior: TerminalBackButtonBehavior
        get() = TerminalBackButtonBehavior.fromId(
            prefs.getString(KEY_BACK_BUTTON_BEHAVIOR, DEFAULT_BACK_BUTTON_BEHAVIOR),
        )
        set(value) = prefs.edit().putString(KEY_BACK_BUTTON_BEHAVIOR, value.id).apply()

    var enforceCharBasedInput: Boolean
        get() = prefs.getBoolean(KEY_ENFORCE_CHAR_BASED_INPUT, DEFAULT_ENFORCE_CHAR_BASED_INPUT)
        set(value) = prefs.edit().putBoolean(KEY_ENFORCE_CHAR_BASED_INPUT, value).apply()

    var isPocketDetectionEnabled: Boolean
        get() = prefs.getBoolean(KEY_POCKET_DETECTION_ENABLED, DEFAULT_POCKET_DETECTION_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_POCKET_DETECTION_ENABLED, value).apply()
}
