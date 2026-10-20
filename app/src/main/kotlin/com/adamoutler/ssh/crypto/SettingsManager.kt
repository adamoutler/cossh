package com.adamoutler.ssh.crypto

import android.content.Context
import android.content.SharedPreferences

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
        private const val DEFAULT_APP_LOCK_TIMEOUT_SECONDS = 0
        private const val KEY_IS_CLOUD_SYNC_ACTIVE = "is_cloud_sync_active"
    }

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
}
