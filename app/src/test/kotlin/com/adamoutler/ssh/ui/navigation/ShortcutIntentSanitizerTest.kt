package com.adamoutler.ssh.ui.navigation

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShortcutIntentSanitizerTest {

    @Test
    fun `isLaunchProfileIntent identifies launch intent actions`() {
        val launchIntent = Intent(IntentSanitizer.ACTION_LAUNCH_PROFILE).apply {
            putExtra(IntentSanitizer.EXTRA_PROFILE_ID, "profile-prod-1")
        }
        assertTrue(IntentSanitizer.isLaunchProfileIntent(launchIntent))
        assertTrue(IntentSanitizer.isResumeIntent(launchIntent))

        val regularIntent = Intent(Intent.ACTION_VIEW)
        assertFalse(IntentSanitizer.isLaunchProfileIntent(regularIntent))
    }

    @Test
    fun `validateLaunchIntent validates valid profile and constructs terminal route`() {
        val intent = Intent(IntentSanitizer.ACTION_LAUNCH_PROFILE).apply {
            putExtra(IntentSanitizer.EXTRA_PROFILE_ID, "my-server-uuid")
        }

        val target = IntentSanitizer.validateLaunchIntent(
            intent = intent,
            profileLookup = { it == "my-server-uuid" },
        )

        assertNotNull(target)
        assertEquals("my-server-uuid", target?.profileId)
        assertEquals("terminal?profileId=my-server-uuid", target?.route)
    }

    @Test
    fun `validateLaunchIntent rejects nonexistent profile`() {
        val intent = Intent(IntentSanitizer.ACTION_LAUNCH_PROFILE).apply {
            putExtra(IntentSanitizer.EXTRA_PROFILE_ID, "nonexistent-profile")
        }

        val target = IntentSanitizer.validateLaunchIntent(
            intent = intent,
            profileLookup = { false },
        )

        assertNull(target)
    }

    @Test
    fun `validateLaunchIntent rejects invalid profile ID regex format`() {
        val traversalIntent = Intent(IntentSanitizer.ACTION_LAUNCH_PROFILE).apply {
            putExtra(IntentSanitizer.EXTRA_PROFILE_ID, "../../etc/passwd")
        }
        assertNull(IntentSanitizer.validateLaunchIntent(traversalIntent, profileLookup = { true }))

        val emptyIntent = Intent(IntentSanitizer.ACTION_LAUNCH_PROFILE).apply {
            putExtra(IntentSanitizer.EXTRA_PROFILE_ID, "")
        }
        assertNull(IntentSanitizer.validateLaunchIntent(emptyIntent, profileLookup = { true }))

        val maliciousCharsIntent = Intent(IntentSanitizer.ACTION_LAUNCH_PROFILE).apply {
            putExtra(IntentSanitizer.EXTRA_PROFILE_ID, "prof;rm -rf /")
        }
        assertNull(IntentSanitizer.validateLaunchIntent(maliciousCharsIntent, profileLookup = { true }))
    }

    @Test
    fun `validateLaunchIntent strictly rejects external command and credential injection payloads`() {
        val injectedCommandIntent = Intent(IntentSanitizer.ACTION_LAUNCH_PROFILE).apply {
            putExtra(IntentSanitizer.EXTRA_PROFILE_ID, "valid-id")
            putExtra("command", "cat /etc/shadow")
        }
        assertNull("Should reject intent with injected command", IntentSanitizer.validateLaunchIntent(injectedCommandIntent, profileLookup = { true }))

        val injectedHostIntent = Intent(IntentSanitizer.ACTION_LAUNCH_PROFILE).apply {
            putExtra(IntentSanitizer.EXTRA_PROFILE_ID, "valid-id")
            putExtra("host", "malicious-attacker.com")
        }
        assertNull("Should reject intent with injected host override", IntentSanitizer.validateLaunchIntent(injectedHostIntent, profileLookup = { true }))

        val injectedPasswordIntent = Intent(IntentSanitizer.ACTION_LAUNCH_PROFILE).apply {
            putExtra(IntentSanitizer.EXTRA_PROFILE_ID, "valid-id")
            putExtra("password", "injected_secret")
        }
        assertNull("Should reject intent with injected password override", IntentSanitizer.validateLaunchIntent(injectedPasswordIntent, profileLookup = { true }))
    }
}
