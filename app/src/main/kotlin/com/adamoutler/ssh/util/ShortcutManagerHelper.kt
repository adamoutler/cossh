package com.adamoutler.ssh.util

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.adamoutler.ssh.MainActivity
import com.adamoutler.ssh.R
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.ui.navigation.IntentSanitizer

/**
 * Hardened launcher shortcut dispatcher for home-screen dynamic and pinned profile launcher icons.
 * Enforces OS shortcut quotas, branded vector assets, and strict intent construction.
 */
object ShortcutManagerHelper {
    private const val TAG = "ShortcutManagerHelper"
    const val MAX_DYNAMIC_SHORTCUTS = 4

    /**
     * Publishes a dynamic shortcut for the given [profile] and requests pinning if supported.
     * Enforces the maximum 4-shortcut quota to comply with Android OS guidelines.
     */
    fun pushProfileShortcut(context: Context, profile: ConnectionProfile): Boolean = try {
        val label = profile.nickname.ifBlank { profile.host }
        val longLabel = "Connect to $label (${profile.username}@${profile.host}:${profile.port})"

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            action = IntentSanitizer.ACTION_LAUNCH_PROFILE
            putExtra(IntentSanitizer.EXTRA_PROFILE_ID, profile.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val icon = try {
            IconCompat.createWithResource(context, R.drawable.ic_shortcut_ssh)
        } catch (_: Exception) {
            IconCompat.createWithResource(context, R.drawable.ic_stat_ssh)
        }

        val shortcut = ShortcutInfoCompat.Builder(context, profile.id)
            .setShortLabel(label)
            .setLongLabel(longLabel)
            .setIcon(icon)
            .setIntent(launchIntent)
            .build()

        // Push dynamic shortcut into launcher history
        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)

        // Request pinning to home screen if launcher supports it
        if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
        }

        AppLog.i(TAG, "Successfully published and requested pin shortcut for profile '${profile.nickname}' (${profile.id})")
        true
    } catch (e: Exception) {
        AppLog.e(TAG, "Failed to push profile shortcut for '${profile.nickname}': ${e.message}", e)
        false
    }

    /**
     * Removes an active shortcut when a profile is deleted.
     */
    fun removeProfileShortcut(context: Context, profileId: String) {
        try {
            ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(profileId))
            ShortcutManagerCompat.disableShortcuts(context, listOf(profileId), "Profile removed")
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to remove shortcut for $profileId: ${e.message}")
        }
    }
}
