package com.adamoutler.ssh.security.audit

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Append-only, tamper-evident audit logger implementing continuous SHA-256 hash chaining.
 * Every entry is anchored to the hash of the preceding entry:
 *   H_n = SHA-256(CanonicalBytes(R_n, H_{n-1}))
 * Writes are committed directly to disk using FileChannel.force(true) to guarantee fsync.
 */
class SecurityAuditLogger(
    val auditFile: File,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    companion object {
        const val GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000"

        fun canonicalBytes(
            sequenceNumber: Long,
            timestampMs: Long,
            eventType: AuditEventType,
            previousHash: String,
            payload: Map<String, String>,
        ): ByteArray {
            val sortedPayload = payload.toSortedMap().entries.joinToString("&") { "${it.key}=${it.value}" }
            val canonicalString = "$sequenceNumber|$timestampMs|${eventType.name}|$previousHash|$sortedPayload"
            return canonicalString.toByteArray(Charsets.UTF_8)
        }

        fun computeSha256Hex(bytes: ByteArray): String {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(bytes)
            return digest.joinToString("") { "%02x".format(it) }
        }
    }

    private val mutex = Mutex()
    private var lastSequenceNumber: Long = 0L
    private var lastHeadHash: String = GENESIS_HASH
    private var isInitialized = false

    private fun ensureInitializedLocked() {
        if (isInitialized) return
        auditFile.parentFile?.mkdirs()
        if (auditFile.exists() && auditFile.length() > 0L) {
            try {
                var lastRecord: AuditRecord? = null
                auditFile.forEachLine { line ->
                    if (line.isNotBlank()) {
                        lastRecord = Json.decodeFromString<AuditRecord>(line.trim())
                    }
                }
                lastRecord?.let {
                    lastSequenceNumber = it.sequenceNumber
                    lastHeadHash = it.entryHash
                }
            } catch (_: Exception) {
                // If existing log has read error, state will be detected by verifyChainIntegrity
            }
        }
        isInitialized = true
    }

    /**
     * Appends a new audit event to the tamper-evident chain atomically.
     */
    suspend fun appendEvent(
        eventType: AuditEventType,
        payload: Map<String, String> = emptyMap(),
    ): AuditRecord = mutex.withLock {
        ensureInitializedLocked()

        val nextSequence = lastSequenceNumber + 1
        val prevHash = lastHeadHash
        val timestamp = clock()

        val canonical = canonicalBytes(nextSequence, timestamp, eventType, prevHash, payload)
        val entryHash = computeSha256Hex(canonical)

        val record = AuditRecord(
            sequenceNumber = nextSequence,
            timestampMs = timestamp,
            eventType = eventType,
            payload = payload,
            previousHash = prevHash,
            entryHash = entryHash,
        )

        val jsonLine = Json.encodeToString(record) + "\n"
        val lineBytes = jsonLine.toByteArray(Charsets.UTF_8)

        auditFile.parentFile?.mkdirs()
        FileOutputStream(auditFile, true).use { fos ->
            fos.channel.use { channel ->
                val buffer = ByteBuffer.wrap(lineBytes)
                while (buffer.hasRemaining()) {
                    channel.write(buffer)
                }
                channel.force(true)
            }
        }

        lastSequenceNumber = nextSequence
        lastHeadHash = entryHash

        return record
    }

    /**
     * Verifies the entire cryptographic hash chain from genesis to head.
     * Optionally compares the terminal entry against an anchored Keystore head.
     */
    suspend fun verifyChainIntegrity(
        expectedHeadSequence: Long? = null,
        expectedHeadHash: String? = null,
    ): AuditIntegrityResult = mutex.withLock {
        if (!auditFile.exists() || auditFile.length() == 0L) {
            return if (expectedHeadSequence != null && expectedHeadSequence > 0L) {
                AuditIntegrityResult.Corrupted(
                    sequenceNumber = 0L,
                    reason = "Log file is missing or empty but expected head was sequence $expectedHeadSequence (rollback detected)",
                )
            } else {
                AuditIntegrityResult.EmptyLog
            }
        }

        var runningHash = GENESIS_HASH
        var runningSequence = 0L
        var totalEntries = 0L

        try {
            val lines = auditFile.readLines()
            if (lines.isEmpty()) {
                return if (expectedHeadSequence != null && expectedHeadSequence > 0L) {
                    AuditIntegrityResult.Corrupted(0L, "Empty log file with non-zero expected head")
                } else {
                    AuditIntegrityResult.EmptyLog
                }
            }

            for (line in lines) {
                if (line.isBlank()) continue

                val record = try {
                    Json.decodeFromString<AuditRecord>(line.trim())
                } catch (e: Exception) {
                    return AuditIntegrityResult.Corrupted(
                        sequenceNumber = runningSequence + 1,
                        reason = "Malformed JSON record: ${e.message}",
                    )
                }

                // 1. Verify strictly sequential sequence numbers
                if (record.sequenceNumber != runningSequence + 1) {
                    return AuditIntegrityResult.Corrupted(
                        sequenceNumber = record.sequenceNumber,
                        reason = "Sequence gap or disorder: expected ${runningSequence + 1}, got ${record.sequenceNumber}",
                    )
                }

                // 2. Verify previousHash chaining
                if (record.previousHash != runningHash) {
                    return AuditIntegrityResult.Corrupted(
                        sequenceNumber = record.sequenceNumber,
                        reason = "Chain break: previousHash '${record.previousHash}' does not match expected parent '$runningHash'",
                    )
                }

                // 3. Verify deterministic canonical entry hash
                val canonical = canonicalBytes(
                    record.sequenceNumber,
                    record.timestampMs,
                    record.eventType,
                    record.previousHash,
                    record.payload,
                )
                val expectedHash = computeSha256Hex(canonical)
                if (record.entryHash != expectedHash) {
                    return AuditIntegrityResult.Corrupted(
                        sequenceNumber = record.sequenceNumber,
                        reason = "Payload tampering detected: entryHash '${record.entryHash}' does not match canonical recomputed '$expectedHash'",
                    )
                }

                runningSequence = record.sequenceNumber
                runningHash = record.entryHash
                totalEntries++
            }

            // 4. Verify head anchor matching if supplied
            if (expectedHeadSequence != null && expectedHeadSequence != runningSequence) {
                return AuditIntegrityResult.Corrupted(
                    sequenceNumber = runningSequence,
                    reason = "Rollback detected: expected terminal sequence $expectedHeadSequence, but log ended at $runningSequence",
                )
            }

            if (expectedHeadHash != null && expectedHeadHash != runningHash) {
                return AuditIntegrityResult.Corrupted(
                    sequenceNumber = runningSequence,
                    reason = "Terminal head hash mismatch: expected '$expectedHeadHash', got '$runningHash'",
                )
            }

            return AuditIntegrityResult.Valid(
                totalEntries = totalEntries,
                headHash = runningHash,
            )
        } catch (e: Exception) {
            return AuditIntegrityResult.Corrupted(
                sequenceNumber = runningSequence,
                reason = "I/O or validation failure: ${e.message}",
            )
        }
    }

    /**
     * Reads all audit records from disk in chronological sequence order.
     */
    suspend fun getAllRecords(): List<AuditRecord> = mutex.withLock {
        if (!auditFile.exists()) return emptyList()
        val records = mutableListOf<AuditRecord>()
        try {
            auditFile.forEachLine { line ->
                if (line.isNotBlank()) {
                    records.add(Json.decodeFromString(line.trim()))
                }
            }
        } catch (_: Exception) {
            // Ignore corrupted trailing lines on read
        }
        return records
    }
}
