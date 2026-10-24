package com.adamoutler.ssh.ui.navigation

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.navigation.NavController
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.network.ConnectionStateRepository
import com.adamoutler.ssh.network.SshService

/**
 * Validates, sanitizes, and safely handles external intents and deep-links intended to resume
 * or navigate to an active SSH terminal session.
 *
 * Enforces strict security bounds:
 * - Intent action verification (`com.adamoutler.ssh.RESUME_$sessionId`)
 * - Format whitelisting via regex (`^[a-zA-Z0-9_-]{1,64}$`)
 * - Verification against active memory session registry
 * - Verification against secure encrypted profile storage
 * - URI encoding of route query arguments to prevent route parameter injection
 * - Proactive clearing of activity intent extras to prevent replay attacks on activity recreation
 */
object IntentSanitizer {
    private const val TAG = "IntentSanitizer"
    const val RESUME_ACTION_PREFIX = "com.adamoutler.ssh.RESUME_"
    const val ACTION_LAUNCH_PROFILE = "com.adamoutler.ssh.LAUNCH_PROFILE"
    const val EXTRA_PROFILE_ID = "com.adamoutler.ssh.EXTRA_PROFILE_ID"
    val IDENTIFIER_REGEX = Regex("^[a-zA-Z0-9_-]{1,64}$")

    /**
     * Checks if the intent represents an attempt to resume or navigate to a terminal session or launch a profile shortcut.
     */
    fun isResumeIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        val action = intent.action
        return (action != null && (action.startsWith(RESUME_ACTION_PREFIX) || action == ACTION_LAUNCH_PROFILE)) ||
            intent.hasExtra(SshService.EXTRA_PROFILE_ID) ||
            intent.hasExtra(EXTRA_PROFILE_ID) ||
            intent.hasExtra(SshService.EXTRA_SESSION_ID)
    }

    /**
     * Checks if the intent is specifically a home-screen or automation launch profile shortcut.
     */
    fun isLaunchProfileIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        return intent.action == ACTION_LAUNCH_PROFILE ||
            (intent.hasExtra(EXTRA_PROFILE_ID) && !intent.hasExtra(SshService.EXTRA_SESSION_ID))
    }

    /**
     * Validates and sanitizes the parameters of a session resume intent.
     *
     * @param intent The incoming intent to inspect.
     * @param securityStorageManager Storage manager for profile existence validation.
     * @param sessionLookup Function to verify active session existence (defaults to [ConnectionStateRepository.sessions]).
     * @param profileLookup Function to verify profile existence (defaults to [SecurityStorageManager.getProfile]).
     * @return [SanitizedResumeTarget] containing validated identifiers and route, or null if validation fails.
     */
    fun validateResumeIntent(
        intent: Intent?,
        securityStorageManager: SecurityStorageManager? = null,
        sessionLookup: (String) -> Boolean = { ConnectionStateRepository.sessions.containsKey(it) },
        profileLookup: (String) -> Boolean = { profileId ->
            securityStorageManager?.getProfile(profileId) != null
        },
    ): SanitizedResumeTarget? {
        if (intent == null) return null

        val profileId = intent.getStringExtra(SshService.EXTRA_PROFILE_ID)
        val sessionId = intent.getStringExtra(SshService.EXTRA_SESSION_ID)

        if (profileId.isNullOrEmpty() || sessionId.isNullOrEmpty()) {
            Log.w(TAG, "Rejected resume intent: missing required extras ($profileId / $sessionId)")
            return null
        }

        if (!IDENTIFIER_REGEX.matches(profileId)) {
            Log.w(TAG, "Rejected resume intent: profileId failed regex format validation")
            return null
        }

        if (!IDENTIFIER_REGEX.matches(sessionId)) {
            Log.w(TAG, "Rejected resume intent: sessionId failed regex format validation")
            return null
        }

        val expectedAction = "$RESUME_ACTION_PREFIX$sessionId"
        if (intent.action != expectedAction) {
            Log.w(TAG, "Rejected resume intent: action '${intent.action}' does not match expected '$expectedAction'")
            return null
        }

        if (!sessionLookup(sessionId)) {
            Log.w(TAG, "Rejected resume intent: sessionId '$sessionId' is not an active session")
            return null
        }

        if (!profileLookup(profileId)) {
            Log.w(TAG, "Rejected resume intent: profileId '$profileId' does not exist in storage")
            return null
        }

        val route = "terminal?profileId=${Uri.encode(profileId)}&sessionId=${Uri.encode(sessionId)}"
        return SanitizedResumeTarget(profileId = profileId, sessionId = sessionId, route = route)
    }

    /**
     * Validates and sanitizes an incoming profile launch shortcut intent.
     * Enforces strict regex validation, stored profile existence, and neutralizes any
     * attempted command, hostname, or credential injection in untrusted extras.
     */
    fun validateLaunchIntent(
        intent: Intent?,
        securityStorageManager: SecurityStorageManager? = null,
        profileLookup: (String) -> Boolean = { profileId ->
            securityStorageManager?.getProfile(profileId) != null
        },
    ): SanitizedResumeTarget? {
        if (intent == null) return null

        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
            ?: intent.getStringExtra(SshService.EXTRA_PROFILE_ID)

        if (profileId.isNullOrEmpty()) {
            Log.w(TAG, "Rejected launch intent: missing profileId extra")
            return null
        }

        if (!IDENTIFIER_REGEX.matches(profileId)) {
            Log.w(TAG, "Rejected launch intent: profileId failed regex format validation")
            return null
        }

        // Security Invariant: Zero External Injection
        // Explicitly check for hostile injection attempts in untrusted extras
        val hostileKeys = listOf("command", "cmd", "exec", "host", "hostname", "port", "password", "user", "username")
        val injectedKeys = hostileKeys.filter { intent.hasExtra(it) }
        if (injectedKeys.isNotEmpty()) {
            Log.w(TAG, "Security Alert: Rejected launch intent with hostile injection extras: $injectedKeys")
            return null
        }

        if (!profileLookup(profileId)) {
            Log.w(TAG, "Rejected launch intent: profileId '$profileId' does not exist in storage")
            return null
        }

        val route = "terminal?profileId=${Uri.encode(profileId)}"
        return SanitizedResumeTarget(profileId = profileId, sessionId = "", route = route)
    }

    /**
     * Inspects, clears, validates, and safely navigates for an incoming resume or launch intent.
     *
     * @param intent The incoming intent to process.
     * @param navController Navigation controller for routing.
     * @param activity Hosting activity whose intent is cleared to prevent replay.
     * @param securityStorageManager Storage manager to check profile existence.
     * @param sessionLookup Active session lookup predicate.
     * @param profileLookup Stored profile lookup predicate.
     * @return true if the intent was valid, verified, and navigation succeeded; false otherwise.
     */
    fun sanitizeAndNavigate(
        intent: Intent?,
        navController: NavController,
        activity: Activity? = null,
        securityStorageManager: SecurityStorageManager? = null,
        sessionLookup: (String) -> Boolean = { ConnectionStateRepository.sessions.containsKey(it) },
        profileLookup: (String) -> Boolean = { profileId ->
            securityStorageManager?.getProfile(profileId) != null
        },
    ): Boolean {
        if (intent == null || !isResumeIntent(intent)) {
            return false
        }

        // Clear processed intent immediately on the activity to prevent replay on activity recreation
        activity?.intent = Intent()

        val target = if (isLaunchProfileIntent(intent)) {
            validateLaunchIntent(
                intent = intent,
                securityStorageManager = securityStorageManager,
                profileLookup = profileLookup,
            )
        } else {
            validateResumeIntent(
                intent = intent,
                securityStorageManager = securityStorageManager,
                sessionLookup = sessionLookup,
                profileLookup = profileLookup,
            )
        } ?: return false

        return try {
            navController.navigate(target.route) {
                popUpTo("connectionList") { inclusive = false }
                launchSingleTop = true
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Navigation failed unexpectedly for route: ${target.route}", e)
            false
        }
    }

    /**
     * Verified data holder for sanitized navigation target parameters.
     */
    data class SanitizedResumeTarget(
        val profileId: String,
        val sessionId: String,
        val route: String,
    )
}
