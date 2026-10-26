package com.adamoutler.ssh.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.MainActivity
import com.adamoutler.ssh.R
import com.adamoutler.ssh.ui.navigation.IntentSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoSshAppWidgetProviderSecurityTest {

    private lateinit var context: Context
    private val testProfileId = "prod-server-secure-42"
    private var testWidgetId: Int = 101

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val shadowAwm = shadowOf(appWidgetManager)
        testWidgetId = shadowAwm.createWidget(
            CoSshAppWidgetProvider::class.java,
            R.layout.widget_quick_connect,
        )
        context.getSharedPreferences(CoSshAppWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `createPendingIntent targets explicit MainActivity with ACTION_LAUNCH_PROFILE`() {
        val pendingIntent = CoSshAppWidgetProvider.createPendingIntent(
            context = context,
            appWidgetId = testWidgetId,
            profileId = testProfileId,
        )

        assertNotNull(pendingIntent)
        val shadowPi = shadowOf(pendingIntent)
        assertTrue("PendingIntent must be an Activity intent", shadowPi.isActivity)

        val targetIntent = shadowPi.savedIntent
        assertNotNull(targetIntent)
        assertEquals(MainActivity::class.java.name, targetIntent.component?.className)
        assertEquals(IntentSanitizer.ACTION_LAUNCH_PROFILE, targetIntent.action)
        assertEquals(testProfileId, targetIntent.getStringExtra(IntentSanitizer.EXTRA_PROFILE_ID))
    }

    @Test
    fun `createPendingIntent enforces immutable and update current flags`() {
        val pendingIntent = CoSshAppWidgetProvider.createPendingIntent(
            context = context,
            appWidgetId = testWidgetId,
            profileId = testProfileId,
        )

        val shadowPi = shadowOf(pendingIntent)
        val flags = shadowPi.flags
        assertTrue("Must include FLAG_IMMUTABLE", (flags and PendingIntent.FLAG_IMMUTABLE) != 0)
        assertTrue("Must include FLAG_UPDATE_CURRENT", (flags and PendingIntent.FLAG_UPDATE_CURRENT) != 0)
    }

    @Test
    fun `createPendingIntent enforces zero credentials in intent extras`() {
        val pendingIntent = CoSshAppWidgetProvider.createPendingIntent(
            context = context,
            appWidgetId = testWidgetId,
            profileId = testProfileId,
        )

        val targetIntent = shadowOf(pendingIntent).savedIntent
        val extras = targetIntent.extras
        assertNotNull(extras)

        // Strict Invariant: ONLY EXTRA_PROFILE_ID may be present
        val extraKeys = extras!!.keySet()
        assertEquals(setOf(IntentSanitizer.EXTRA_PROFILE_ID), extraKeys)

        val hostileOrSensitiveKeys = listOf(
            "password",
            "pwd",
            "private_key",
            "key",
            "secret",
            "passphrase",
            "command",
            "cmd",
            "exec",
            "host",
            "hostname",
            "port",
            "user",
            "username",
        )
        for (key in hostileOrSensitiveKeys) {
            assertFalse("Intent must NOT contain credential or hostile key '$key'", targetIntent.hasExtra(key))
        }
    }

    @Test
    fun `widget profile persistence lifecycle`() {
        val freshWidgetId = 999
        assertNull(CoSshAppWidgetProvider.getWidgetProfile(context, freshWidgetId))

        CoSshAppWidgetProvider.saveWidgetProfile(context, freshWidgetId, testProfileId)
        assertEquals(testProfileId, CoSshAppWidgetProvider.getWidgetProfile(context, freshWidgetId))

        CoSshAppWidgetProvider.deleteWidgetProfile(context, freshWidgetId)
        assertNull(CoSshAppWidgetProvider.getWidgetProfile(context, freshWidgetId))
    }

    @Test
    fun `onUpdate updates widget without exception`() {
        val provider = CoSshAppWidgetProvider()
        val appWidgetManager = AppWidgetManager.getInstance(context)

        CoSshAppWidgetProvider.saveWidgetProfile(context, testWidgetId, testProfileId)
        provider.onUpdate(context, appWidgetManager, intArrayOf(testWidgetId))

        val views = shadowOf(appWidgetManager).getViewFor(testWidgetId)
        assertNotNull(views)
    }

    @Test
    fun `onDeleted purges widget profile preferences`() {
        val provider = CoSshAppWidgetProvider()
        CoSshAppWidgetProvider.saveWidgetProfile(context, testWidgetId, testProfileId)
        assertEquals(testProfileId, CoSshAppWidgetProvider.getWidgetProfile(context, testWidgetId))

        provider.onDeleted(context, intArrayOf(testWidgetId))
        assertNull(CoSshAppWidgetProvider.getWidgetProfile(context, testWidgetId))
    }
}
