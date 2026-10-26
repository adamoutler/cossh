package com.adamoutler.ssh.security.audit

import kotlinx.serialization.Serializable

enum class AuditEventType {
    SECURITY_INITIALIZED,
    KEY_PAIR_GENERATED,
    CERTIFICATE_VALIDATED,
    HOST_KEY_VERIFIED,
    HOST_KEY_REJECTED,
    SESSION_CONNECTED,
    SESSION_TERMINATED,
    AUTH_PARTIAL_SUCCESS,
    AUTH_FAILURE,
    SECURITY_CONFIG_MODIFIED,
    APP_LOCK_CONFIGURED,
    APP_LOCK_UNLOCKED,
    APP_LOCK_FAILED,
    AUTOMATION_INTENT_AUTHENTICATED,
    AUTOMATION_INTENT_REJECTED,
}

@Serializable
data class AuditRecord(
    val sequenceNumber: Long,
    val timestampMs: Long,
    val eventType: AuditEventType,
    val payload: Map<String, String>,
    val previousHash: String,
    val entryHash: String,
)

sealed interface AuditIntegrityResult {
    data class Valid(val totalEntries: Long, val headHash: String) : AuditIntegrityResult
    data class Corrupted(val sequenceNumber: Long, val reason: String) : AuditIntegrityResult
    object EmptyLog : AuditIntegrityResult
}
