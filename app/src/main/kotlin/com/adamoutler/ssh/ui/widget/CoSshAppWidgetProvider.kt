package com.adamoutler.ssh.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.adamoutler.ssh.MainActivity
import com.adamoutler.ssh.R
import com.adamoutler.ssh.ui.navigation.IntentSanitizer

/**
 * AppWidgetProvider for home-screen Quick Connect widgets.
 *
 * Adheres to strict zero-credential IPC invariants:
 * - Employs explicit intents targeting [MainActivity]
 * - Uses [IntentSanitizer.ACTION_LAUNCH_PROFILE] and [IntentSanitizer.EXTRA_PROFILE_ID]
 * - Enforces [PendingIntent.FLAG_IMMUTABLE] and [PendingIntent.FLAG_UPDATE_CURRENT]
 * - Passes strictly zero credentials, secrets, or shell commands in extras
 */
class CoSshAppWidgetProvider : AppWidgetProvider() {

    companion object {
        const val PREFS_NAME = "com.adamoutler.ssh.ui.widget.CoSshAppWidgetProvider"
        const val PREF_PREFIX_KEY = "appwidget_profile_"

        /**
         * Creates a secure, immutable [PendingIntent] for launching an SSH profile shortcut.
         */
        fun createPendingIntent(
            context: Context,
            appWidgetId: Int,
            profileId: String,
        ): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = IntentSanitizer.ACTION_LAUNCH_PROFILE
                putExtra(IntentSanitizer.EXTRA_PROFILE_ID, profileId)
            }
            return PendingIntent.getActivity(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /**
         * Persists the assigned profileId for a specific widget instance.
         */
        fun saveWidgetProfile(context: Context, appWidgetId: Int, profileId: String) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_PREFIX_KEY + appWidgetId, profileId)
                .apply()
        }

        /**
         * Retrieves the assigned profileId for a specific widget instance.
         */
        fun getWidgetProfile(context: Context, appWidgetId: Int): String? {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(PREF_PREFIX_KEY + appWidgetId, null)
        }

        /**
         * Deletes preference storage for a specific widget instance.
         */
        fun deleteWidgetProfile(context: Context, appWidgetId: Int) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(PREF_PREFIX_KEY + appWidgetId)
                .apply()
        }

        /**
         * Updates a single widget instance with RemoteViews and click pending intents.
         */
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            profileId: String? = null,
            profileName: String? = null,
        ) {
            val resolvedProfileId = profileId ?: getWidgetProfile(context, appWidgetId)
            val views = RemoteViews(context.packageName, R.layout.widget_quick_connect)

            if (!profileName.isNullOrEmpty()) {
                views.setTextViewText(R.id.widget_title, profileName)
            }

            val pendingIntent = if (resolvedProfileId != null) {
                createPendingIntent(context, appWidgetId, resolvedProfileId)
            } else {
                val mainIntent = Intent(context, MainActivity::class.java)
                PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    mainIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }

            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_button, pendingIntent)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    /**
     * Instance delegate for createPendingIntent.
     */
    fun createPendingIntent(
        context: Context,
        appWidgetId: Int,
        profileId: String,
    ): PendingIntent = Companion.createPendingIntent(context, appWidgetId, profileId)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            deleteWidgetProfile(context, appWidgetId)
        }
    }
}
