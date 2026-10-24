package com.adamoutler.ssh.security.audit

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.adamoutler.ssh.crypto.handleKeystoreExceptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File

/**
 * Singleton facade managing tamper-evident security audit logging.
 * Anchors the terminal head pointer (last sequence number and last entry hash)
 * into hardware Keystore-backed EncryptedSharedPreferences to detect rollback attacks.
 */
class SecurityAuditLogManager(
    context: Context,
    val logger: SecurityAuditLogger = SecurityAuditLogger(
        File(context.applicationContext.filesDir, "security_audit/audit_chain.jsonl")
    ),
    injectedPrefs: SharedPreferences? = null,
) {
    companion object {
        private const val PREFS_FILE = "security_audit_anchor_prefs"
        private const val KEY_HEAD_SEQUENCE = "audit_head_sequence"
        private const val KEY_HEAD_HASH = "audit_head_hash"

        @Volatile
        private var instance: SecurityAuditLogManager? = null

        fun getInstance(context: Context): SecurityAuditLogManager =
            instance ?: synchronized(this) {
                instance ?: SecurityAuditLogManager(context.applicationContext).also {
                    instance = it
                }
            }

        fun getInstanceOrNull(): SecurityAuditLogManager? = instance

        fun resetInstanceForTesting(mockManager: SecurityAuditLogManager? = null) {
            instance = mockManager
        }

        private fun createEncryptedPrefs(appContext: Context): SharedPreferences {
            return try {
                val masterKey = try {
                    MasterKey.Builder(appContext)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .setRequestStrongBoxBacked(true)
                        .build()
                } catch (e: Exception) {
                    MasterKey.Builder(appContext)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()
                }

                EncryptedSharedPreferences.create(
                    appContext,
                    PREFS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            } catch (e: Exception) {
                val isRobolectric = System.getProperty("robolectric.logging") != null ||
                    android.os.Build.FINGERPRINT.contains("robolectric")
                if (isRobolectric) {
                    return appContext.getSharedPreferences("${PREFS_FILE}_fallback", Context.MODE_PRIVATE)
                }
                e.handleKeystoreExceptions("Failed to initialize Keystore anchor for security audit log.")
            }
        }
    }

    private val anchoredPrefs: SharedPreferences =
        injectedPrefs ?: createEncryptedPrefs(context.applicationContext)

    private fun persistHeadAnchor(record: AuditRecord) {
        try {
            anchoredPrefs.edit()
                .putLong(KEY_HEAD_SEQUENCE, record.sequenceNumber)
                .putString(KEY_HEAD_HASH, record.entryHash)
                .apply()
        } catch (_: Exception) {
            // Anchor persistence failure should not crash operation
        }
    }

    suspend fun logSessionStart(
        profileId: String,
        host: String,
        port: Int,
        username: String,
    ): AuditRecord = withContext(Dispatchers.IO) {
        val payload = mapOf(
            "profile_id" to profileId,
            "host" to host,
            "port" to port.toString(),
            "username" to username,
        )
        val record = logger.appendEvent(AuditEventType.SESSION_CONNECTED, payload)
        persistHeadAnchor(record)
        record
    }

    suspend fun logSessionEnd(
        profileId: String,
        host: String,
        reason: String? = null,
    ): AuditRecord = withContext(Dispatchers.IO) {
        val payload = buildMap {
            put("profile_id", profileId)
            put("host", host)
            if (!reason.isNullOrEmpty()) put("reason", reason)
        }
        val record = logger.appendEvent(AuditEventType.SESSION_TERMINATED, payload)
        persistHeadAnchor(record)
        record
    }

    suspend fun logAuthResult(
        profileId: String,
        host: String,
        username: String,
        authType: String,
        success: Boolean,
        failureReason: String? = null,
    ): AuditRecord = withContext(Dispatchers.IO) {
        val event = if (success) AuditEventType.SESSION_CONNECTED else AuditEventType.AUTH_FAILURE
        val payload = buildMap {
            put("profile_id", profileId)
            put("host", host)
            put("username", username)
            put("auth_type", authType)
            put("auth_success", success.toString())
            if (!failureReason.isNullOrEmpty()) put("failure_reason", failureReason)
        }
        val record = logger.appendEvent(event, payload)
        persistHeadAnchor(record)
        record
    }

    suspend fun logHostKeyVerification(
        host: String,
        port: Int,
        keyType: String,
        fingerprint: String,
        accepted: Boolean,
        isKeyChanged: Boolean,
    ): AuditRecord = withContext(Dispatchers.IO) {
        val event = if (accepted) AuditEventType.HOST_KEY_VERIFIED else AuditEventType.HOST_KEY_REJECTED
        val payload = mapOf(
            "host" to host,
            "port" to port.toString(),
            "key_type" to keyType,
            "fingerprint" to fingerprint,
            "is_key_changed" to isKeyChanged.toString(),
            "accepted" to accepted.toString(),
        )
        val record = logger.appendEvent(event, payload)
        persistHeadAnchor(record)
        record
    }

    suspend fun logKeyOperation(
        operation: String,
        keyAlias: String,
        keyType: String? = null,
    ): AuditRecord = withContext(Dispatchers.IO) {
        val event = if (operation.contains("GENERATE", ignoreCase = true)) {
            AuditEventType.KEY_PAIR_GENERATED
        } else {
            AuditEventType.SECURITY_CONFIG_MODIFIED
        }
        val payload = buildMap {
            put("operation", operation)
            put("key_alias", keyAlias)
            if (!keyType.isNullOrEmpty()) put("key_type", keyType)
        }
        val record = logger.appendEvent(event, payload)
        persistHeadAnchor(record)
        record
    }

    suspend fun logAppLockEvent(
        eventType: AuditEventType,
        detail: String? = null,
    ): AuditRecord = withContext(Dispatchers.IO) {
        val payload = buildMap {
            if (!detail.isNullOrEmpty()) put("detail", detail)
        }
        val record = logger.appendEvent(eventType, payload)
        persistHeadAnchor(record)
        record
    }

    suspend fun verifyIntegrity(): AuditIntegrityResult = withContext(Dispatchers.IO) {
        val headSeq = anchoredPrefs.getLong(KEY_HEAD_SEQUENCE, 0L)
        val headHash = anchoredPrefs.getString(KEY_HEAD_HASH, null)
        logger.verifyChainIntegrity(
            expectedHeadSequence = if (headSeq > 0L) headSeq else null,
            expectedHeadHash = headHash,
        )
    }

    suspend fun getRecords(): List<AuditRecord> = withContext(Dispatchers.IO) {
        logger.getAllRecords()
    }

    suspend fun exportLogAsJsonLd(): String = withContext(Dispatchers.IO) {
        val records = logger.getAllRecords()
        val jsonLd = buildJsonObject {
            put("@context", "https://schema.org")
            put("@type", "ItemList")
            put("name", "CoSSH Security Audit Log")
            put("numberOfItems", records.size)
            putJsonArray("itemListElement") {
                records.forEach { record ->
                    add(
                        buildJsonObject {
                            put("@type", "ListItem")
                            put("position", record.sequenceNumber)
                            putJsonObject("item") {
                                put("@type", "Action")
                                put("actionStatus", record.eventType.name)
                                put("startTime", record.timestampMs)
                                put("entryHash", record.entryHash)
                                put("previousHash", record.previousHash)
                                putJsonObject("payload") {
                                    record.payload.forEach { (k, v) -> put(k, v) }
                                }
                            }
                        }
                    )
                }
            }
        }
        jsonLd.toString()
    }
}
