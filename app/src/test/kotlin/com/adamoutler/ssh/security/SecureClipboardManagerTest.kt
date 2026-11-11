package com.adamoutler.ssh.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.crypto.SettingsManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecureClipboardManagerTest {

    private lateinit var context: Context
    private lateinit var clipboardManager: ClipboardManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.clearPrimaryClip()
        context.getSharedPreferences("cossh_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        SecureClipboardManager.clearClipboard(context)
        context.getSharedPreferences("cossh_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun testCopyToClipboard_setsPrimaryClipWithSensitiveExtraOnApi33Plus() {
        SecureClipboardManager.copyToClipboard(
            context = context,
            text = "SensitiveCredential#123\n",
            label = "Terminal text",
        )

        val clip = clipboardManager.primaryClip
        assertNotNull("Clipboard must contain primary clip", clip)
        assertEquals(1, clip!!.itemCount)
        assertEquals("SensitiveCredential#123", clip.getItemAt(0).text.toString())

        val extras = clip.description.extras
        assertNotNull("ClipDescription extras must not be null on API 34", extras)
        assertTrue(
            "EXTRA_IS_SENSITIVE must be set to true on API 33+",
            extras!!.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE),
        )
    }

    @Test
    fun testSensitiveTaggingDisabled_omitsExtraIsSensitive() {
        val settings = SettingsManager(context)
        settings.isClipboardSensitiveTaggingEnabled = false

        SecureClipboardManager.copyToClipboard(
            context = context,
            text = "UntaggedCredential",
            label = "Terminal text",
        )

        val clip = clipboardManager.primaryClip
        assertNotNull("Clipboard must contain primary clip", clip)
        val extras = clip!!.description.extras
        val isSensitiveExtra = extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false) ?: false
        assertFalse("EXTRA_IS_SENSITIVE must be false/omitted when tagging is disabled", isSensitiveExtra)
    }

    @Test
    fun testExplicitIsSensitiveFalse_omitsExtraIsSensitive() {
        val settings = SettingsManager(context)
        settings.isClipboardSensitiveTaggingEnabled = true

        SecureClipboardManager.copyToClipboard(
            context = context,
            text = "NonSensitiveText",
            label = "Terminal text",
            isSensitive = false,
        )

        val clip = clipboardManager.primaryClip
        assertNotNull("Clipboard must contain primary clip", clip)
        val extras = clip!!.description.extras
        val isSensitiveExtra = extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false) ?: false
        assertFalse("EXTRA_IS_SENSITIVE must be false/omitted when isSensitive parameter is false", isSensitiveExtra)
    }

    @Test
    fun testDefaultClipboardAutoClearZero_doesNotClearAfter30Seconds() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val settings = SettingsManager(context)
        assertEquals("Default clipboard auto-clear must be 0s", 0, settings.clipboardAutoClearSeconds)

        SecureClipboardManager.copyToClipboard(
            context = context,
            text = "persistent_clipboard_item",
            scope = testScope,
        )

        assertNull("autoClearJob must be null when delay is 0s", SecureClipboardManager.autoClearJob)
        assertEquals("persistent_clipboard_item", clipboardManager.primaryClip?.getItemAt(0)?.text?.toString())

        // Fast-forward 30 seconds
        testScope.advanceTimeBy(30_000L)
        testScope.runCurrent()
        assertEquals("persistent_clipboard_item", clipboardManager.primaryClip?.getItemAt(0)?.text?.toString())

        // Fast-forward another 30 seconds (total 60s)
        testScope.advanceTimeBy(30_000L)
        testScope.runCurrent()
        assertEquals("persistent_clipboard_item", clipboardManager.primaryClip?.getItemAt(0)?.text?.toString())
    }

    @Test
    fun testConfiguredClipboardAutoClearSeconds_clearsClipboardAfterConfiguredDelay() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val settings = SettingsManager(context)
        settings.clipboardAutoClearSeconds = 30

        SecureClipboardManager.copyToClipboard(
            context = context,
            text = "auto_clear_configured",
            scope = testScope,
        )

        assertNotNull("autoClearJob must be scheduled when clipboardAutoClearSeconds = 30", SecureClipboardManager.autoClearJob)

        testScope.advanceTimeBy(29_000L)
        testScope.runCurrent()
        assertEquals("auto_clear_configured", clipboardManager.primaryClip?.getItemAt(0)?.text?.toString())

        testScope.advanceTimeBy(1_001L)
        testScope.runCurrent()

        val clipAfter = clipboardManager.primaryClip
        assertTrue("Clipboard must be cleared after configured 30s timeout", clipAfter == null || clipAfter.itemCount == 0 || clipAfter.getItemAt(0).text.isNullOrEmpty())
    }

    @Test
    fun testAutoClear_clearsClipboardAfterTimeoutWithExplicitDelay() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        SecureClipboardManager.copyToClipboard(
            context = context,
            text = "ephemeral_password",
            scope = testScope,
            autoClearDelayMs = 30_000L,
        )

        assertEquals("ephemeral_password", clipboardManager.primaryClip?.getItemAt(0)?.text?.toString())

        // Fast-forward 29 seconds (should still be present)
        testScope.advanceTimeBy(29_000L)
        testScope.runCurrent()
        assertEquals("ephemeral_password", clipboardManager.primaryClip?.getItemAt(0)?.text?.toString())

        // Fast-forward past 30 seconds
        testScope.advanceTimeBy(1_001L)
        testScope.runCurrent()

        val clipAfter = clipboardManager.primaryClip
        assertTrue("Clipboard must be empty or null after auto-clear timeout", clipAfter == null || clipAfter.itemCount == 0 || clipAfter.getItemAt(0).text.isNullOrEmpty())
    }

    @Test
    fun testNewCopy_cancelsPriorAutoClearJob() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        SecureClipboardManager.copyToClipboard(
            context = context,
            text = "first_secret",
            scope = testScope,
            autoClearDelayMs = 30_000L,
        )
        val firstJob = SecureClipboardManager.autoClearJob
        assertNotNull(firstJob)
        assertFalse(firstJob!!.isCancelled)

        // Advance 15 seconds
        testScope.advanceTimeBy(15_000L)
        testScope.runCurrent()

        // Copy new secret
        SecureClipboardManager.copyToClipboard(
            context = context,
            text = "second_secret",
            scope = testScope,
            autoClearDelayMs = 30_000L,
        )
        val secondJob = SecureClipboardManager.autoClearJob

        assertTrue("First auto-clear job must be cancelled", firstJob.isCancelled)
        assertNotNull("Second auto-clear job must be active", secondJob)
        assertFalse("Second auto-clear job must not be cancelled", secondJob!!.isCancelled)

        // Advance 16 seconds (first job would have completed, but second job should still be alive)
        testScope.advanceTimeBy(16_000L)
        testScope.runCurrent()
        assertEquals("second_secret", clipboardManager.primaryClip?.getItemAt(0)?.text?.toString())

        // Advance another 15 seconds (total 31s since second copy)
        testScope.advanceTimeBy(15_000L)
        testScope.runCurrent()
        val clipAfter = clipboardManager.primaryClip
        assertTrue("Clipboard must be cleared after second timeout", clipAfter == null || clipAfter.itemCount == 0 || clipAfter.getItemAt(0).text.isNullOrEmpty())
    }

    @Test
    fun testClearClipboard_manuallyClearsAndCancelsJob() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        SecureClipboardManager.copyToClipboard(
            context = context,
            text = "to_be_cleared",
            scope = testScope,
            autoClearDelayMs = 30_000L,
        )
        val job = SecureClipboardManager.autoClearJob
        assertNotNull(job)

        SecureClipboardManager.clearClipboard(context)

        assertTrue("Job must be cancelled on manual clear", job!!.isCancelled)
        assertNull("autoClearJob reference must be null", SecureClipboardManager.autoClearJob)

        val clip = clipboardManager.primaryClip
        assertTrue("Clipboard must be cleared", clip == null || clip.itemCount == 0 || clip.getItemAt(0).text.isNullOrEmpty())
    }

    @Test
    fun testAutoClear_doesNotOverwriteExternalClipboardContent() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        SecureClipboardManager.copyToClipboard(
            context = context,
            text = "app_secret",
            scope = testScope,
            autoClearDelayMs = 30_000L,
        )

        // User copies something else externally before 30s
        clipboardManager.setPrimaryClip(ClipData.newPlainText("User Clip", "important_external_note"))

        // Advance past 30 seconds
        testScope.advanceTimeBy(31_000L)
        testScope.runCurrent()

        // Clipboard should still contain external note
        assertEquals("important_external_note", clipboardManager.primaryClip?.getItemAt(0)?.text?.toString())
    }

    @Test
    fun testAppLockTimeoutSeconds_defaultsTo60() {
        val settings = SettingsManager(context)
        assertEquals("appLockTimeoutSeconds must default to 60s", 60, settings.appLockTimeoutSeconds)
        assertEquals("DEFAULT_APP_LOCK_TIMEOUT_SECONDS constant must be 60", 60, SettingsManager.DEFAULT_APP_LOCK_TIMEOUT_SECONDS)
    }
}
