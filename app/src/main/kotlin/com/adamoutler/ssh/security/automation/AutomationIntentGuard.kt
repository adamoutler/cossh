package com.adamoutler.ssh.security.automation

import android.content.Context
import android.content.Intent
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.network.SshService
import com.adamoutler.ssh.security.audit.AuditEventType
import com.adamoutler.ssh.security.audit.SecurityAuditLogManager
import com.adamoutler.ssh.ui.navigation.IntentSanitizer
import kotlinx.coroutines.runBlocking
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Result of automation intent validation.
 */
sealed interface AutomationValidationResult {
    data class Authenticated(
        val profileId: String,
        val action: String,
        val timestampMs: Long,
        val nonce: String,
    ) : AutomationValidationResult

    data class Rejected(
        val reason: String,
        val hostileExtrasFound: List<String> = emptyList(),
    ) : AutomationValidationResult
}

/**
 * Security boundary verifying external automation intents (`ACTION_EXECUTE_AUTOMATION`).
 *
 * Enforces strict cryptographic and architectural invariants:
 * - Constant-time HMAC-SHA256 signature verification over canonical payload
 * - Replay prevention via bounded LRU nonce cache
 * - Timestamp freshness bounds (+/- 60,000ms clock skew)
 * - Zero external command/credential injection (rejects hostile override extras)
 * - Strict alphanumeric formatting bounds on identifiers and nonces
 * - Verification against encrypted profile storage
 * - Comprehensive security audit logging for both successful and rejected attempts
 */
class AutomationIntentGuard(
    private val securityStorageManager: SecurityStorageManager,
    private val auditLogManager: SecurityAuditLogManager? = null,
    private val nonceCache: NonceReplayCache = NonceReplayCache(),
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    companion object {
        const val ACTION_EXECUTE_AUTOMATION = "com.adamoutler.ssh.ACTION_EXECUTE_AUTOMATION"
        const val EXTRA_PROFILE_ID = "com.adamoutler.ssh.EXTRA_PROFILE_ID"
        const val EXTRA_NONCE = "com.adamoutler.ssh.EXTRA_NONCE"
        const val EXTRA_TIMESTAMP = "com.adamoutler.ssh.EXTRA_TIMESTAMP"
        const val EXTRA_SIGNATURE = "com.adamoutler.ssh.EXTRA_SIGNATURE"
        const val EXTRA_TOKEN = "com.adamoutler.ssh.EXTRA_TOKEN"
        const val EXTRA_ACTION = "com.adamoutler.ssh.EXTRA_ACTION"

        const val ACTION_CONNECT = "CONNECT"
        const val ACTION_LAUNCH = "LAUNCH"

        const val MAX_CLOCK_SKEW_MS = 60_000L

        val IDENTIFIER_REGEX = Regex("^[a-zA-Z0-9_-]{1,64}$")
        val NONCE_REGEX = Regex("^[a-zA-Z0-9_-]{1,128}$")

        val HOSTILE_EXTRA_KEYS = setOf(
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

        val SUPPORTED_ACTIONS = setOf(
            ACTION_EXECUTE_AUTOMATION,
            ACTION_CONNECT,
            ACTION_LAUNCH,
            IntentSanitizer.ACTION_LAUNCH_PROFILE,
        )

        /**
         * Builds canonical payload string: `$timestampMs|$nonce|$profileId|$action`
         */
        fun buildCanonicalString(
            timestampMs: Long,
            nonce: String,
            profileId: String,
            action: String,
        ): String = "$timestampMs|$nonce|$profileId|$action"

        /**
         * Computes HMAC-SHA256 signature over the canonical payload.
         */
        fun computeHmacSha256(
            secretKey: ByteArray,
            timestampMs: Long,
            nonce: String,
            profileId: String,
            action: String,
        ): ByteArray {
            val canonical = buildCanonicalString(timestampMs, nonce, profileId, action)
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secretKey, "HmacSHA256"))
            return mac.doFinal(canonical.toByteArray(StandardCharsets.UTF_8))
        }

        /**
         * Computes HMAC-SHA256 signature and formats as hex string.
         */
        fun computeHmacSha256Hex(
            secretKey: ByteArray,
            timestampMs: Long,
            nonce: String,
            profileId: String,
            action: String,
        ): String {
            val bytes = computeHmacSha256(secretKey, timestampMs, nonce, profileId, action)
            return bytes.joinToString("") { "%02x".format(it) }
        }

        /**
         * Convenience factory to create a cryptographically signed automation intent.
         */
        fun createSignedIntent(
            context: Context,
            profileId: String,
            secretKey: ByteArray,
            action: String = ACTION_EXECUTE_AUTOMATION,
            timestampMs: Long = System.currentTimeMillis(),
            nonce: String = UUID.randomUUID().toString(),
        ): Intent {
            val signature = computeHmacSha256Hex(secretKey, timestampMs, nonce, profileId, action)
            return Intent(ACTION_EXECUTE_AUTOMATION).apply {
                setClass(context, com.adamoutler.ssh.MainActivity::class.java)
                putExtra(EXTRA_PROFILE_ID, profileId)
                putExtra(EXTRA_NONCE, nonce)
                putExtra(EXTRA_TIMESTAMP, timestampMs)
                putExtra(EXTRA_SIGNATURE, signature)
                putExtra(EXTRA_ACTION, action)
            }
        }
    }

    /**
     * Validates and verifies an incoming automation intent.
     *
     * @param intent Incoming intent to inspect.
     * @return [AutomationValidationResult.Authenticated] if valid, or [AutomationValidationResult.Rejected].
     */
    fun validate(intent: Intent?): AutomationValidationResult {
        if (intent == null) {
            return reject("Null intent", null, null)
        }

        // 1. Validate intent action
        if (intent.action != ACTION_EXECUTE_AUTOMATION) {
            return reject("Invalid or missing intent action: '${intent.action}'", null, null)
        }

        // 2. Reject hostile override extras
        val hostileFound = intent.extras?.keySet()?.filter { key ->
            HOSTILE_EXTRA_KEYS.contains(key.lowercase())
        } ?: emptyList()
        if (hostileFound.isNotEmpty()) {
            return reject(
                reason = "Hostile override extras detected: $hostileFound",
                profileId = null,
                action = null,
                hostileKeys = hostileFound,
            )
        }

        // 3. Extract required extras
        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
            ?: intent.getStringExtra(IntentSanitizer.EXTRA_PROFILE_ID)
            ?: intent.getStringExtra(SshService.EXTRA_PROFILE_ID)
            ?: intent.getStringExtra("profileId")
            ?: intent.getStringExtra("profile_id")

        if (profileId.isNullOrEmpty()) {
            return reject("Missing profileId extra", null, null)
        }

        val nonce = intent.getStringExtra(EXTRA_NONCE)
            ?: intent.getStringExtra("nonce")

        if (nonce.isNullOrEmpty()) {
            return reject("Missing nonce extra", profileId, null)
        }

        val timestampMs = extractTimestampMs(intent)
        if (timestampMs <= 0L) {
            return reject("Missing or invalid timestamp extra", profileId, null)
        }

        val signatureBytes = extractSignatureBytes(intent)
        if (signatureBytes == null || signatureBytes.isEmpty()) {
            return reject("Missing or malformed token/signature extra", profileId, null)
        }

        // 4. Validate identifier and nonce formats
        if (!IDENTIFIER_REGEX.matches(profileId)) {
            return reject("Malformed profileId format: failed alphanumeric regex", profileId, null)
        }

        if (!NONCE_REGEX.matches(nonce)) {
            return reject("Malformed nonce format: failed alphanumeric regex", profileId, null)
        }

        // 5. Validate action extra
        val action = intent.getStringExtra(EXTRA_ACTION)
            ?: intent.getStringExtra("action")
            ?: intent.action
            ?: ACTION_EXECUTE_AUTOMATION

        if (action !in SUPPORTED_ACTIONS) {
            return reject("Unsupported automation action: '$action'", profileId, action)
        }

        // 6. Check timestamp freshness
        val nowMs = clock()
        val skewPast = nowMs - timestampMs
        val skewFuture = timestampMs - nowMs

        if (skewPast > MAX_CLOCK_SKEW_MS) {
            return reject("Expired timestamp: age=${skewPast}ms exceeds ${MAX_CLOCK_SKEW_MS}ms", profileId, action)
        }
        if (skewFuture > MAX_CLOCK_SKEW_MS) {
            return reject("Future timestamp: drift=${skewFuture}ms exceeds ${MAX_CLOCK_SKEW_MS}ms", profileId, action)
        }

        // 7. Check nonce replay
        if (!nonceCache.recordNonce(nonce, nowMs)) {
            return reject("Replayed nonce detected: '$nonce'", profileId, action)
        }

        // 8. Verify HMAC-SHA256 signature
        val secretKey = securityStorageManager.getAutomationSecretKey()
        if (secretKey == null || secretKey.isEmpty()) {
            return reject("No automation secret key configured in storage", profileId, action)
        }

        val expectedSignature = computeHmacSha256(secretKey, timestampMs, nonce, profileId, action)
        if (!MessageDigest.isEqual(expectedSignature, signatureBytes)) {
            return reject("HMAC-SHA256 signature verification failed", profileId, action)
        }

        // 9. Verify profile existence in secure storage
        val profile = securityStorageManager.getProfile(profileId)
        if (profile == null) {
            return reject("Profile '$profileId' does not exist in storage", profileId, action)
        }

        // 10. Success: log authenticated event
        logAuditEvent(
            AuditEventType.AUTOMATION_INTENT_AUTHENTICATED,
            mapOf(
                "profile_id" to profileId,
                "action" to action,
                "nonce" to nonce,
                "timestamp_ms" to timestampMs.toString(),
            ),
        )

        return AutomationValidationResult.Authenticated(
            profileId = profileId,
            action = action,
            timestampMs = timestampMs,
            nonce = nonce,
        )
    }

    /**
     * Checks whether an incoming intent is valid.
     */
    fun isValid(intent: Intent?): Boolean = validate(intent) is AutomationValidationResult.Authenticated

    private fun extractTimestampMs(intent: Intent): Long {
        if (intent.hasExtra(EXTRA_TIMESTAMP)) {
            val longVal = intent.getLongExtra(EXTRA_TIMESTAMP, 0L)
            if (longVal > 0L) return longVal
            val stringVal = intent.getStringExtra(EXTRA_TIMESTAMP)
            stringVal?.toLongOrNull()?.let { return it }
        }
        if (intent.hasExtra("timestamp")) {
            val longVal = intent.getLongExtra("timestamp", 0L)
            if (longVal > 0L) return longVal
            val stringVal = intent.getStringExtra("timestamp")
            stringVal?.toLongOrNull()?.let { return it }
        }
        if (intent.hasExtra("timestampMs")) {
            val longVal = intent.getLongExtra("timestampMs", 0L)
            if (longVal > 0L) return longVal
            val stringVal = intent.getStringExtra("timestampMs")
            stringVal?.toLongOrNull()?.let { return it }
        }
        return 0L
    }

    private fun extractSignatureBytes(intent: Intent): ByteArray? {
        val rawBytes = intent.getByteArrayExtra(EXTRA_SIGNATURE)
            ?: intent.getByteArrayExtra(EXTRA_TOKEN)
            ?: intent.getByteArrayExtra("signature")
            ?: intent.getByteArrayExtra("token")
        if (rawBytes != null && rawBytes.isNotEmpty()) {
            return rawBytes
        }

        val sigString = intent.getStringExtra(EXTRA_SIGNATURE)
            ?: intent.getStringExtra(EXTRA_TOKEN)
            ?: intent.getStringExtra("signature")
            ?: intent.getStringExtra("token")
            ?: intent.getStringExtra("hmac")
            ?: return null

        if (sigString.isEmpty()) return null

        // Hex string decoding (64 hex characters for SHA-256)
        if (sigString.length == 64 && sigString.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
            try {
                return sigString.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            } catch (_: Exception) {
                // Fall through to Base64
            }
        }

        // Base64 decoding
        return try {
            Base64.getDecoder().decode(sigString)
        } catch (_: Exception) {
            try {
                Base64.getUrlDecoder().decode(sigString)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun reject(
        reason: String,
        profileId: String?,
        action: String?,
        hostileKeys: List<String> = emptyList(),
    ): AutomationValidationResult.Rejected {
        val payload = buildMap {
            put("reason", reason)
            if (!profileId.isNullOrEmpty()) put("profile_id", profileId)
            if (!action.isNullOrEmpty()) put("action", action)
            if (hostileKeys.isNotEmpty()) put("hostile_extras", hostileKeys.joinToString(","))
        }
        logAuditEvent(AuditEventType.AUTOMATION_INTENT_REJECTED, payload)
        return AutomationValidationResult.Rejected(reason, hostileKeys)
    }

    private fun logAuditEvent(eventType: AuditEventType, payload: Map<String, String>) {
        val manager = auditLogManager ?: SecurityAuditLogManager.getInstanceOrNull() ?: return
        try {
            runBlocking {
                manager.logger.appendEvent(eventType, payload)
            }
        } catch (_: Exception) {
            // Audit logging failure should not crash intent validation
        }
    }
}
