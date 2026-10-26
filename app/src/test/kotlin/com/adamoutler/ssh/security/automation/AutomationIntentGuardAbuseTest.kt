package com.adamoutler.ssh.security.automation

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.security.audit.AuditEventType
import com.adamoutler.ssh.security.audit.SecurityAuditLogManager
import com.adamoutler.ssh.security.audit.SecurityAuditLogger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutomationIntentGuardAbuseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var storageManager: SecurityStorageManager
    private lateinit var auditLogger: SecurityAuditLogger
    private lateinit var auditManager: SecurityAuditLogManager
    private lateinit var nonceCache: NonceReplayCache
    private lateinit var guard: AutomationIntentGuard

    private var simulatedTimeMs: Long = 1_000_000_000L
    private val validProfileId = "prod-server-01"
    private lateinit var secretKey: ByteArray

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences("test_sec_storage_automation", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        storageManager = SecurityStorageManager(context, prefs)
        secretKey = storageManager.generateOrGetAutomationSecretKey()

        val logFile = tempFolder.newFile("audit_chain_automation.jsonl")
        auditLogger = SecurityAuditLogger(logFile) { simulatedTimeMs }
        val auditPrefs = context.getSharedPreferences("test_audit_automation", Context.MODE_PRIVATE)
        auditPrefs.edit().clear().commit()
        auditManager = SecurityAuditLogManager(context, auditLogger, auditPrefs)

        // Save a valid profile
        val profile = ConnectionProfile(
            id = validProfileId,
            nickname = "Production Node",
            host = "192.168.1.100",
            port = 22,
        )
        storageManager.saveProfile(profile)

        nonceCache = NonceReplayCache()
        guard = AutomationIntentGuard(
            securityStorageManager = storageManager,
            auditLogManager = auditManager,
            nonceCache = nonceCache,
            clock = { simulatedTimeMs },
        )
    }

    private fun createValidIntent(
        profileId: String = validProfileId,
        action: String = AutomationIntentGuard.ACTION_EXECUTE_AUTOMATION,
        timestampMs: Long = simulatedTimeMs,
        nonce: String = UUID.randomUUID().toString(),
        key: ByteArray = secretKey,
    ): Intent {
        val sigHex = AutomationIntentGuard.computeHmacSha256Hex(
            secretKey = key,
            timestampMs = timestampMs,
            nonce = nonce,
            profileId = profileId,
            action = action,
        )
        return Intent(AutomationIntentGuard.ACTION_EXECUTE_AUTOMATION).apply {
            putExtra(AutomationIntentGuard.EXTRA_PROFILE_ID, profileId)
            putExtra(AutomationIntentGuard.EXTRA_NONCE, nonce)
            putExtra(AutomationIntentGuard.EXTRA_TIMESTAMP, timestampMs)
            putExtra(AutomationIntentGuard.EXTRA_SIGNATURE, sigHex)
            putExtra(AutomationIntentGuard.EXTRA_ACTION, action)
        }
    }

    @Test
    fun `validate rejects null intent`() {
        val result = guard.validate(null)
        assertTrue(result is AutomationValidationResult.Rejected)
        assertFalse(guard.isValid(null))
    }

    @Test
    fun `validate rejects invalid or non-matching intent action`() {
        val intent = createValidIntent().apply {
            action = Intent.ACTION_VIEW
        }
        val result = guard.validate(intent)
        assertTrue(result is AutomationValidationResult.Rejected)
        val rej = result as AutomationValidationResult.Rejected
        assertTrue(rej.reason.contains("Invalid or missing intent action"))
        assertFalse(guard.isValid(intent))

        intent.action = null
        val nullActionResult = guard.validate(intent)
        assertTrue(nullActionResult is AutomationValidationResult.Rejected)
    }

    @Test
    fun `validate strictly rejects hostile override extras`() {
        val hostileKeys = listOf(
            "command",
            "cmd",
            "exec",
            "host",
            "hostname",
            "port",
            "password",
            "user",
            "username",
        )

        for (hostileKey in hostileKeys) {
            val intent = createValidIntent()
            intent.putExtra(hostileKey, "malicious_payload")
            val result = guard.validate(intent)
            assertTrue("Expected rejection for hostile key '$hostileKey'", result is AutomationValidationResult.Rejected)
            val rej = result as AutomationValidationResult.Rejected
            assertTrue(rej.hostileExtrasFound.contains(hostileKey))
            assertFalse(guard.isValid(intent))
        }

        // Mixed-case attack
        val mixedCaseIntent = createValidIntent().apply {
            putExtra("COMMAND", "cat /etc/shadow")
        }
        val mixedResult = guard.validate(mixedCaseIntent)
        assertTrue(mixedResult is AutomationValidationResult.Rejected)
    }

    @Test
    fun `validate rejects missing token extras`() {
        // Missing profileId
        val noProfile = createValidIntent().apply {
            removeExtra(AutomationIntentGuard.EXTRA_PROFILE_ID)
        }
        assertTrue(guard.validate(noProfile) is AutomationValidationResult.Rejected)

        // Missing nonce
        val noNonce = createValidIntent().apply {
            removeExtra(AutomationIntentGuard.EXTRA_NONCE)
        }
        assertTrue(guard.validate(noNonce) is AutomationValidationResult.Rejected)

        // Missing timestamp
        val noTime = createValidIntent().apply {
            removeExtra(AutomationIntentGuard.EXTRA_TIMESTAMP)
        }
        assertTrue(guard.validate(noTime) is AutomationValidationResult.Rejected)

        // Missing signature
        val noSig = createValidIntent().apply {
            removeExtra(AutomationIntentGuard.EXTRA_SIGNATURE)
        }
        assertTrue(guard.validate(noSig) is AutomationValidationResult.Rejected)
    }

    @Test
    fun `validate rejects malformed profileId format`() {
        val traversalIntent = createValidIntent(profileId = "../../etc/passwd")
        assertTrue(guard.validate(traversalIntent) is AutomationValidationResult.Rejected)

        val injectionIntent = createValidIntent(profileId = "prof;rm -rf /")
        assertTrue(guard.validate(injectionIntent) is AutomationValidationResult.Rejected)

        val overlongIntent = createValidIntent(profileId = "a".repeat(65))
        assertTrue(guard.validate(overlongIntent) is AutomationValidationResult.Rejected)
    }

    @Test
    fun `validate rejects malformed nonce format`() {
        val badNonceIntent = createValidIntent(nonce = "nonce;drop table")
        assertTrue(guard.validate(badNonceIntent) is AutomationValidationResult.Rejected)

        val spaceNonceIntent = createValidIntent(nonce = "bad nonce with space")
        assertTrue(guard.validate(spaceNonceIntent) is AutomationValidationResult.Rejected)

        val overlongNonceIntent = createValidIntent(nonce = "n".repeat(129))
        assertTrue(guard.validate(overlongNonceIntent) is AutomationValidationResult.Rejected)
    }

    @Test
    fun `validate rejects unsupported automation actions`() {
        val badAction = "FORMAT_STORAGE"
        val intent = createValidIntent(action = badAction)
        val result = guard.validate(intent)
        assertTrue(result is AutomationValidationResult.Rejected)
        val rej = result as AutomationValidationResult.Rejected
        assertTrue(rej.reason.contains("Unsupported automation action"))
    }

    @Test
    fun `validate rejects expired timestamp`() {
        // 60,001 ms in the past
        val expiredTime = simulatedTimeMs - 60_001L
        val intent = createValidIntent(timestampMs = expiredTime)
        val result = guard.validate(intent)
        assertTrue(result is AutomationValidationResult.Rejected)
        val rej = result as AutomationValidationResult.Rejected
        assertTrue(rej.reason.contains("Expired timestamp"))
    }

    @Test
    fun `validate rejects future timestamp exceeding clock skew tolerance`() {
        // 60,001 ms in the future
        val futureTime = simulatedTimeMs + 60_001L
        val intent = createValidIntent(timestampMs = futureTime)
        val result = guard.validate(intent)
        assertTrue(result is AutomationValidationResult.Rejected)
        val rej = result as AutomationValidationResult.Rejected
        assertTrue(rej.reason.contains("Future timestamp"))
    }

    @Test
    fun `validate rejects replayed nonce`() {
        val nonce = "replay-nonce-test-123"
        val intent1 = createValidIntent(nonce = nonce)
        val firstResult = guard.validate(intent1)
        assertTrue("First attempt should authenticate", firstResult is AutomationValidationResult.Authenticated)

        val intent2 = createValidIntent(nonce = nonce)
        val secondResult = guard.validate(intent2)
        assertTrue("Replayed nonce must be rejected", secondResult is AutomationValidationResult.Rejected)
        val rej = secondResult as AutomationValidationResult.Rejected
        assertTrue(rej.reason.contains("Replayed nonce"))
    }

    @Test
    fun `validate purges expired nonces after TTL`() {
        val nonce = "ttl-nonce-test-456"
        val intent1 = createValidIntent(nonce = nonce, timestampMs = simulatedTimeMs)
        assertTrue(guard.validate(intent1) is AutomationValidationResult.Authenticated)

        // Advance clock past TTL (60,000ms)
        simulatedTimeMs += 65_000L
        val intent2 = createValidIntent(nonce = nonce, timestampMs = simulatedTimeMs)
        val resultAfterTtl = guard.validate(intent2)
        assertTrue("Nonce should be accepted after previous entry expired from TTL cache", resultAfterTtl is AutomationValidationResult.Authenticated)
    }

    @Test
    fun `validate rejects invalid HMAC-SHA256 signature`() {
        // 1. Wrong secret key
        val wrongKey = ByteArray(32) { 0xFF.toByte() }
        val wrongKeyIntent = createValidIntent(key = wrongKey)
        val res1 = guard.validate(wrongKeyIntent)
        assertTrue(res1 is AutomationValidationResult.Rejected)
        assertTrue((res1 as AutomationValidationResult.Rejected).reason.contains("HMAC-SHA256 signature verification failed"))

        // 2. Tampered profile ID in payload
        val tamperedIntent = createValidIntent()
        tamperedIntent.putExtra(AutomationIntentGuard.EXTRA_PROFILE_ID, "attacker-profile")
        val res2 = guard.validate(tamperedIntent)
        assertTrue(res2 is AutomationValidationResult.Rejected)

        // 3. Tampered timestamp
        val tamperedTimeIntent = createValidIntent()
        tamperedTimeIntent.putExtra(AutomationIntentGuard.EXTRA_TIMESTAMP, simulatedTimeMs + 10L)
        val res3 = guard.validate(tamperedTimeIntent)
        assertTrue(res3 is AutomationValidationResult.Rejected)
    }

    @Test
    fun `validate rejects nonexistent profile ID in storage`() {
        val missingId = "ghost-profile-id"
        val intent = createValidIntent(profileId = missingId)
        val result = guard.validate(intent)
        assertTrue(result is AutomationValidationResult.Rejected)
        val rej = result as AutomationValidationResult.Rejected
        assertTrue(rej.reason.contains("does not exist in storage"))
    }

    @Test
    fun `validate authenticates valid signed intent`() {
        val nonce = "fresh-valid-nonce-99"
        val intent = createValidIntent(nonce = nonce)
        val result = guard.validate(intent)
        assertTrue(result is AutomationValidationResult.Authenticated)
        val auth = result as AutomationValidationResult.Authenticated
        assertEquals(validProfileId, auth.profileId)
        assertEquals(AutomationIntentGuard.ACTION_EXECUTE_AUTOMATION, auth.action)
        assertEquals(simulatedTimeMs, auth.timestampMs)
        assertEquals(nonce, auth.nonce)

        val freshIntent = createValidIntent(nonce = "fresh-valid-nonce-100")
        assertTrue(guard.isValid(freshIntent))
    }

    @Test
    fun `validate logs audit events on authentication and rejection`() = runBlocking {
        // Authenticated attempt
        val validNonce = "audit-auth-nonce"
        val validIntent = createValidIntent(nonce = validNonce)
        guard.validate(validIntent)

        // Rejected attempt (hostile extra)
        val hostileIntent = createValidIntent().apply {
            putExtra("password", "leaked")
        }
        guard.validate(hostileIntent)

        val records = auditManager.getRecords()
        assertTrue(records.any { it.eventType == AuditEventType.AUTOMATION_INTENT_AUTHENTICATED && it.payload["nonce"] == validNonce })
        assertTrue(records.any { it.eventType == AuditEventType.AUTOMATION_INTENT_REJECTED && it.payload["hostile_extras"]?.contains("password") == true })
    }

    @Test
    fun `createSignedIntent factory helper produces valid intent`() {
        val intent = AutomationIntentGuard.createSignedIntent(
            context = context,
            profileId = validProfileId,
            secretKey = secretKey,
            timestampMs = simulatedTimeMs,
        )
        assertNotNull(intent)
        assertEquals(AutomationIntentGuard.ACTION_EXECUTE_AUTOMATION, intent.action)
        assertTrue(guard.isValid(intent))
    }

    @Test
    fun `nonce cache enforces bounded LRU capacity`() {
        val smallCache = NonceReplayCache(maxCapacity = 10, ttlMs = 60_000L)
        for (i in 1..10) {
            assertTrue(smallCache.recordNonce("nonce-$i", simulatedTimeMs))
        }
        assertEquals(10, smallCache.size())

        // 11th entry evicts eldest (nonce-1)
        assertTrue(smallCache.recordNonce("nonce-11", simulatedTimeMs))
        assertEquals(10, smallCache.size())
        assertFalse(smallCache.contains("nonce-1", simulatedTimeMs))
        assertTrue(smallCache.contains("nonce-11", simulatedTimeMs))
    }
}
