package com.adamoutler.ssh.ui.navigation

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.navigation.NavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.network.ActiveSessionState
import com.adamoutler.ssh.network.ConnectionStateRepository
import com.adamoutler.ssh.network.SshService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class IntentSanitizerTest {

    private lateinit var context: Context
    private lateinit var storageManager: SecurityStorageManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        storageManager = SecurityStorageManager(context)
        ConnectionStateRepository.sessions.clear()
        ConnectionStateRepository.isHeadlessTest = true
    }

    @Test
    fun testIdentifierRegex_ValidIdentifiers() {
        val validCases = listOf(
            "a",
            "A",
            "1",
            "_",
            "-",
            "my_profile",
            "profile-123",
            "UUID_1234-abcd",
            "A".repeat(64),
        )
        for (id in validCases) {
            assertTrue("Expected '$id' to match IDENTIFIER_REGEX", IntentSanitizer.IDENTIFIER_REGEX.matches(id))
        }
    }

    @Test
    fun testIdentifierRegex_InvalidIdentifiers() {
        val invalidCases = listOf(
            "",
            "A".repeat(65),
            "profile with space",
            "prof/subpath",
            "prof\\backslash",
            "profile;rm",
            "prof?key=val",
            "prof&inject=1",
            "<script>",
            "../etc/passwd",
            "prof:22",
            "emoji😀",
        )
        for (id in invalidCases) {
            assertFalse("Expected '$id' to NOT match IDENTIFIER_REGEX", IntentSanitizer.IDENTIFIER_REGEX.matches(id))
        }
    }

    @Test
    fun testIsResumeIntent_Detection() {
        assertFalse(IntentSanitizer.isResumeIntent(null))
        assertFalse(IntentSanitizer.isResumeIntent(Intent()))
        assertFalse(IntentSanitizer.isResumeIntent(Intent(Intent.ACTION_MAIN)))

        val actionIntent = Intent("${IntentSanitizer.RESUME_ACTION_PREFIX}session123")
        assertTrue(IntentSanitizer.isResumeIntent(actionIntent))

        val profileExtraIntent = Intent().apply {
            putExtra(SshService.EXTRA_PROFILE_ID, "prof1")
        }
        assertTrue(IntentSanitizer.isResumeIntent(profileExtraIntent))

        val sessionExtraIntent = Intent().apply {
            putExtra(SshService.EXTRA_SESSION_ID, "sess1")
        }
        assertTrue(IntentSanitizer.isResumeIntent(sessionExtraIntent))
    }

    @Test
    fun testValidateResumeIntent_NullIntent() {
        assertNull(IntentSanitizer.validateResumeIntent(null, storageManager))
    }

    @Test
    fun testValidateResumeIntent_MissingOrEmptyExtras() {
        val intentNoExtras = Intent("${IntentSanitizer.RESUME_ACTION_PREFIX}sess1")
        assertNull(IntentSanitizer.validateResumeIntent(intentNoExtras, storageManager))

        val intentMissingSession = Intent("${IntentSanitizer.RESUME_ACTION_PREFIX}sess1").apply {
            putExtra(SshService.EXTRA_PROFILE_ID, "prof1")
        }
        assertNull(IntentSanitizer.validateResumeIntent(intentMissingSession, storageManager))

        val intentMissingProfile = Intent("${IntentSanitizer.RESUME_ACTION_PREFIX}sess1").apply {
            putExtra(SshService.EXTRA_SESSION_ID, "sess1")
        }
        assertNull(IntentSanitizer.validateResumeIntent(intentMissingProfile, storageManager))

        val intentEmptyStrings = Intent("${IntentSanitizer.RESUME_ACTION_PREFIX}sess1").apply {
            putExtra(SshService.EXTRA_PROFILE_ID, "")
            putExtra(SshService.EXTRA_SESSION_ID, "")
        }
        assertNull(IntentSanitizer.validateResumeIntent(intentEmptyStrings, storageManager))
    }

    @Test
    fun testValidateResumeIntent_RegexFormatRejection() {
        val badProfileIntent = Intent("${IntentSanitizer.RESUME_ACTION_PREFIX}sess1").apply {
            putExtra(SshService.EXTRA_PROFILE_ID, "bad profile name!")
            putExtra(SshService.EXTRA_SESSION_ID, "sess1")
        }
        assertNull(IntentSanitizer.validateResumeIntent(badProfileIntent, storageManager))

        val badSessionIntent = Intent("${IntentSanitizer.RESUME_ACTION_PREFIX}bad;sess").apply {
            putExtra(SshService.EXTRA_PROFILE_ID, "prof1")
            putExtra(SshService.EXTRA_SESSION_ID, "bad;sess")
        }
        assertNull(IntentSanitizer.validateResumeIntent(badSessionIntent, storageManager))

        val overlyLongIntent = Intent("${IntentSanitizer.RESUME_ACTION_PREFIX}${"a".repeat(65)}").apply {
            putExtra(SshService.EXTRA_PROFILE_ID, "prof1")
            putExtra(SshService.EXTRA_SESSION_ID, "a".repeat(65))
        }
        assertNull(IntentSanitizer.validateResumeIntent(overlyLongIntent, storageManager))
    }

    @Test
    fun testValidateResumeIntent_ActionVerification() {
        val validProfileId = "prof_123"
        val validSessionId = "sess_456"

        // Null action
        val nullActionIntent = Intent().apply {
            putExtra(SshService.EXTRA_PROFILE_ID, validProfileId)
            putExtra(SshService.EXTRA_SESSION_ID, validSessionId)
        }
        assertNull(IntentSanitizer.validateResumeIntent(nullActionIntent, storageManager))

        // Generic / wrong action
        val wrongActionIntent = Intent("android.intent.action.VIEW").apply {
            putExtra(SshService.EXTRA_PROFILE_ID, validProfileId)
            putExtra(SshService.EXTRA_SESSION_ID, validSessionId)
        }
        assertNull(IntentSanitizer.validateResumeIntent(wrongActionIntent, storageManager))

        // Action sessionId mismatch
        val mismatchActionIntent = Intent("${IntentSanitizer.RESUME_ACTION_PREFIX}other_session").apply {
            putExtra(SshService.EXTRA_PROFILE_ID, validProfileId)
            putExtra(SshService.EXTRA_SESSION_ID, validSessionId)
        }
        assertNull(IntentSanitizer.validateResumeIntent(mismatchActionIntent, storageManager))
    }

    @Test
    fun testValidateResumeIntent_ActiveSessionLookupFailure() {
        val profileId = "prof_valid"
        val sessionId = "sess_not_in_repo"

        storageManager.saveProfile(ConnectionProfile(id = profileId, nickname = "Valid", host = "localhost"))

        val intent = Intent("${IntentSanitizer.RESUME_ACTION_PREFIX}$sessionId").apply {
            putExtra(SshService.EXTRA_PROFILE_ID, profileId)
            putExtra(SshService.EXTRA_SESSION_ID, sessionId)
        }

        // Active session does not exist in ConnectionStateRepository
        assertNull(IntentSanitizer.validateResumeIntent(intent, storageManager))
    }

    @Test
    fun testValidateResumeIntent_ProfileStorageLookupFailure() {
        val profileId = "prof_not_in_storage"
        val sessionId = "sess_active"

        // Active session exists in repository
        ConnectionStateRepository.sessions[sessionId] = ActiveSessionState(
            profileId = profileId,
            sessionId = sessionId,
            ptyOutputStream = ByteArrayOutputStream(),
        )

        val intent = Intent("${IntentSanitizer.RESUME_ACTION_PREFIX}$sessionId").apply {
            putExtra(SshService.EXTRA_PROFILE_ID, profileId)
            putExtra(SshService.EXTRA_SESSION_ID, sessionId)
        }

        // Profile not in storage
        assertNull(IntentSanitizer.validateResumeIntent(intent, storageManager))
    }

    @Test
    fun testValidateResumeIntent_Success() {
        val profileId = "profile-uuid-1234"
        val sessionId = "session-uuid-5678"

        storageManager.saveProfile(ConnectionProfile(id = profileId, nickname = "Production Server", host = "prod.example.com"))
        ConnectionStateRepository.sessions[sessionId] = ActiveSessionState(
            profileId = profileId,
            sessionId = sessionId,
            ptyOutputStream = ByteArrayOutputStream(),
        )

        val intent = Intent("${IntentSanitizer.RESUME_ACTION_PREFIX}$sessionId").apply {
            putExtra(SshService.EXTRA_PROFILE_ID, profileId)
            putExtra(SshService.EXTRA_SESSION_ID, sessionId)
        }

        val target = IntentSanitizer.validateResumeIntent(intent, storageManager)
        assertNotNull("Expected valid target", target)
        assertEquals(profileId, target?.profileId)
        assertEquals(sessionId, target?.sessionId)
        assertEquals("terminal?profileId=profile-uuid-1234&sessionId=session-uuid-5678", target?.route)
    }

    @Test
    fun testValidateResumeIntent_CustomLookups() {
        val intent = Intent("${IntentSanitizer.RESUME_ACTION_PREFIX}session_99").apply {
            putExtra(SshService.EXTRA_PROFILE_ID, "profile_99")
            putExtra(SshService.EXTRA_SESSION_ID, "session_99")
        }

        // Custom lookups returning true
        val target = IntentSanitizer.validateResumeIntent(
            intent = intent,
            sessionLookup = { it == "session_99" },
            profileLookup = { it == "profile_99" },
        )
        assertNotNull(target)
        assertEquals("profile_99", target?.profileId)
        assertEquals("session_99", target?.sessionId)

        // Custom session lookup returning false
        val rejectedTarget1 = IntentSanitizer.validateResumeIntent(
            intent = intent,
            sessionLookup = { false },
            profileLookup = { true },
        )
        assertNull(rejectedTarget1)

        // Custom profile lookup returning false
        val rejectedTarget2 = IntentSanitizer.validateResumeIntent(
            intent = intent,
            sessionLookup = { true },
            profileLookup = { false },
        )
        assertNull(rejectedTarget2)
    }

    @Test
    fun testSanitizeAndNavigate_NullOrNonResumeIntent() {
        val activity = Robolectric.buildActivity(Activity::class.java).get()
        val originalIntent = Intent(Intent.ACTION_MAIN)
        activity.intent = originalIntent

        val mockNavController = NavController(context)

        // Null intent
        assertFalse(IntentSanitizer.sanitizeAndNavigate(null, mockNavController, activity, storageManager))
        assertEquals(originalIntent, activity.intent) // Intent not cleared

        // Non-resume intent
        assertFalse(IntentSanitizer.sanitizeAndNavigate(originalIntent, mockNavController, activity, storageManager))
        assertEquals(originalIntent, activity.intent) // Intent not cleared
    }

    @Test
    fun testSanitizeAndNavigate_InvalidResumeIntentClearsActivityIntent() {
        val activity = Robolectric.buildActivity(Activity::class.java).get()
        val maliciousIntent = Intent("${IntentSanitizer.RESUME_ACTION_PREFIX}fake_session").apply {
            putExtra(SshService.EXTRA_PROFILE_ID, "bad profile ; drop table")
            putExtra(SshService.EXTRA_SESSION_ID, "fake_session")
        }
        activity.intent = maliciousIntent

        val mockNavController = NavController(context)

        val handled = IntentSanitizer.sanitizeAndNavigate(
            intent = maliciousIntent,
            navController = mockNavController,
            activity = activity,
            securityStorageManager = storageManager,
        )

        assertFalse("Malformed resume intent must be rejected", handled)
        // Activity intent must be cleared to empty Intent() to prevent replay on activity recreation
        assertFalse("Activity intent extras must be cleared", activity.intent.hasExtra(SshService.EXTRA_PROFILE_ID))
        assertNull("Activity intent action must be cleared", activity.intent.action)
    }

    @Test
    fun testSanitizeAndNavigate_GracefullyHandlesNavigationExceptions() {
        val activity = Robolectric.buildActivity(Activity::class.java).get()
        val profileId = "valid_prof"
        val sessionId = "valid_sess"

        storageManager.saveProfile(ConnectionProfile(id = profileId, nickname = "Test", host = "localhost"))
        ConnectionStateRepository.sessions[sessionId] = ActiveSessionState(
            profileId = profileId,
            sessionId = sessionId,
            ptyOutputStream = ByteArrayOutputStream(),
        )

        val validIntent = Intent("${IntentSanitizer.RESUME_ACTION_PREFIX}$sessionId").apply {
            putExtra(SshService.EXTRA_PROFILE_ID, profileId)
            putExtra(SshService.EXTRA_SESSION_ID, sessionId)
        }
        activity.intent = validIntent

        // NavController without graph attached will throw IllegalStateException upon navigate
        val unconfiguredNavController = NavController(context)

        val handled = IntentSanitizer.sanitizeAndNavigate(
            intent = validIntent,
            navController = unconfiguredNavController,
            activity = activity,
            securityStorageManager = storageManager,
        )

        // IntentSanitizer must catch the navigation exception without crashing
        assertFalse("Navigation exception must be caught gracefully", handled)
        // Intent must still be cleared
        assertFalse(activity.intent.hasExtra(SshService.EXTRA_PROFILE_ID))
    }
}
