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
    }

    var globalFontSize: Int
        get() = prefs.getInt(KEY_GLOBAL_FONT_SIZE, DEFAULT_FONT_SIZE)
        set(value) = prefs.edit().putInt(KEY_GLOBAL_FONT_SIZE, value).apply()

    var defaultGroupName: String
        get() = prefs.getString(KEY_DEFAULT_GROUP_NAME, DEFAULT_GROUP_NAME) ?: DEFAULT_GROUP_NAME
        set(value) = prefs.edit().putString(KEY_DEFAULT_GROUP_NAME, value).apply()
}
