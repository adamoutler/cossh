package com.adamoutler.ssh.crypto

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64

/**
 * Strategy for resolving conflicts during bulk known_hosts imports.
 */
enum class KnownHostsMergeStrategy {
    STRICT_REJECT_CONFLICTS,
    SKIP_EXISTING,
    OVERWRITE_CHANGED,
}

/**
 * Represents a host key discrepancy detected during import.
 */
data class KnownHostConflict(
    val host: String,
    val existingRecord: KnownHostRecord,
    val incomingRecord: KnownHostRecord,
    val message: String = "Host key conflict detected for $host",
)

/**
 * Result of a bulk known_hosts import operation.
 */
data class KnownHostsImportResult(
    val isSuccess: Boolean,
    val importedCount: Int = 0,
    val skippedCount: Int = 0,
    val overwrittenCount: Int = 0,
    val conflicts: List<KnownHostConflict> = emptyList(),
    val warnings: List<String> = emptyList(),
    val errorMessage: String? = null,
)

/**
 * Hardened bulk import and export utility for OpenSSH known_hosts files.
 *
 * Security Invariants:
 * 1. Payload Bounds: Maximum 1 MB (1,048,576 bytes) input payload to prevent memory exhaustion / DoS.
 * 2. Entry Bounds: Maximum 2,000 parsed entries per import operation.
 * 3. Line Bounds: Maximum 8,192 characters per line.
 * 4. Control Character Neutralization: Strictly rejects lines containing unescaped control characters.
 * 5. Wire Format Validation: Validates OpenSSH uint32 length prefix and key type header in public key blobs.
 * 6. Conflict Detection: Bidirectional host matching across plaintext and HMAC-SHA1 hashed entries.
 */
object KnownHostsBulkPorter {

    const val MAX_PAYLOAD_BYTES: Int = 1024 * 1024 // 1 MB
    const val MAX_ENTRY_COUNT: Int = 2000
    const val MAX_LINE_LENGTH: Int = 8192

    private val FORBIDDEN_CONTROL_CHARS = setOf('\r', '\n', '\u0000', '\u001B', '\u007F')

    /**
     * Verifies that the public key blob is valid Base64 and starts with the OpenSSH
     * wire-format uint32 length header followed by the matching key type string and key data.
     */
    fun validatePublicKeyBlob(keyType: String, base64Blob: String): Boolean {
        if (keyType.isBlank() || base64Blob.isBlank()) return false
        val bytes = try {
            Base64.getDecoder().decode(base64Blob.trim())
        } catch (_: IllegalArgumentException) {
            return false
        }
        if (bytes.size < 4) return false

        val wireLength = ((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            (bytes[3].toInt() and 0xFF)

        val keyTypeBytes = keyType.toByteArray(StandardCharsets.US_ASCII)
        if (wireLength <= 0 || wireLength != keyTypeBytes.size || bytes.size <= 4 + wireLength) {
            return false
        }

        for (i in keyTypeBytes.indices) {
            if (bytes[4 + i] != keyTypeBytes[i]) {
                return false
            }
        }
        return true
    }

    /**
     * Strips control characters (\u0000..\u001F, \u007F, ESC) from comments.
     */
    fun sanitizeComment(comment: String): String {
        return comment.filter { it >= ' ' && it != '\u007F' && it != '\u001B' }.trim()
    }

    /**
     * Checks if a line contains forbidden control characters.
     */
    fun hasControlCharacters(line: String): Boolean {
        for (ch in line) {
            if (ch in FORBIDDEN_CONTROL_CHARS || (ch < ' ' && ch != '\t')) {
                return true
            }
        }
        return false
    }

    /**
     * Formats a [KnownHostRecord] into a canonical OpenSSH known_hosts line.
     */
    fun formatRecordLine(record: KnownHostRecord): String {
        val prefix = when {
            record.isCertAuthority -> "@cert-authority "
            record.isRevoked -> "@revoked "
            else -> ""
        }
        val commentPart = if (!record.comment.isNullOrBlank()) {
            val sanitized = sanitizeComment(record.comment)
            if (sanitized.isNotEmpty()) " $sanitized" else ""
        } else {
            ""
        }
        return "$prefix${record.rawHostPattern} ${record.keyType} ${record.publicKeyBase64}$commentPart"
    }

    /**
     * Exports all records from [KnownHostsManager] into OpenSSH known_hosts string format.
     */
    fun exportToOpenSshFormat(manager: KnownHostsManager): String {
        val entries = manager.getEntries()
        if (entries.isEmpty()) return ""
        val sb = StringBuilder()
        for (entry in entries) {
            sb.append(formatRecordLine(entry)).append("\n")
        }
        return sb.toString()
    }

    /**
     * Streams all records from [KnownHostsManager] into [outputStream] in OpenSSH format.
     */
    fun exportToOpenSshFormat(manager: KnownHostsManager, outputStream: OutputStream) {
        val exported = exportToOpenSshFormat(manager)
        outputStream.write(exported.toByteArray(StandardCharsets.UTF_8))
        outputStream.flush()
    }

    /**
     * Imports known_hosts entries from an [InputStream] with bound-checked streaming.
     */
    fun importKnownHosts(
        inputStream: InputStream,
        manager: KnownHostsManager,
        strategy: KnownHostsMergeStrategy = KnownHostsMergeStrategy.STRICT_REJECT_CONFLICTS,
    ): KnownHostsImportResult {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        var totalBytes = 0
        var bytesRead: Int
        while (inputStream.read(chunk).also { bytesRead = it } != -1) {
            totalBytes += bytesRead
            if (totalBytes > MAX_PAYLOAD_BYTES) {
                return KnownHostsImportResult(
                    isSuccess = false,
                    errorMessage = "Payload size exceeds maximum allowed limit of $MAX_PAYLOAD_BYTES bytes",
                )
            }
            buffer.write(chunk, 0, bytesRead)
        }
        val content = buffer.toString(StandardCharsets.UTF_8.name())
        return importKnownHosts(content, manager, strategy)
    }

    /**
     * Imports known_hosts entries from a string content into [manager] according to [strategy].
     */
    fun importKnownHosts(
        content: String,
        manager: KnownHostsManager,
        strategy: KnownHostsMergeStrategy = KnownHostsMergeStrategy.STRICT_REJECT_CONFLICTS,
    ): KnownHostsImportResult {
        val rawBytes = content.toByteArray(StandardCharsets.UTF_8)
        if (rawBytes.size > MAX_PAYLOAD_BYTES) {
            return KnownHostsImportResult(
                isSuccess = false,
                errorMessage = "Payload size (${rawBytes.size} bytes) exceeds maximum limit of $MAX_PAYLOAD_BYTES bytes",
            )
        }

        val parseOutcome = parseCandidates(content, manager)
        if (!parseOutcome.isValid) {
            return KnownHostsImportResult(
                isSuccess = false,
                errorMessage = parseOutcome.errorMessage,
            )
        }

        val candidates = parseOutcome.candidates
        val warnings = parseOutcome.warnings.toMutableList()
        val initialSkipped = parseOutcome.skippedLines

        if (candidates.isEmpty()) {
            return KnownHostsImportResult(
                isSuccess = true,
                importedCount = 0,
                skippedCount = initialSkipped,
                warnings = warnings,
            )
        }

        val existingRecords = manager.getEntries()
        val eval = evaluateCandidates(candidates, existingRecords, manager, strategy)

        if (eval.conflicts.isNotEmpty() && strategy == KnownHostsMergeStrategy.STRICT_REJECT_CONFLICTS) {
            return KnownHostsImportResult(
                isSuccess = false,
                importedCount = 0,
                skippedCount = initialSkipped + eval.skippedCount,
                overwrittenCount = 0,
                conflicts = eval.conflicts,
                warnings = warnings,
                errorMessage = "Import rejected due to ${eval.conflicts.size} conflict(s)",
            )
        }

        applyImportChanges(manager, eval.toAdd, eval.toReplace)

        return KnownHostsImportResult(
            isSuccess = true,
            importedCount = eval.toAdd.size,
            skippedCount = initialSkipped + eval.skippedCount,
            overwrittenCount = eval.overwrittenCount,
            conflicts = eval.conflicts,
            warnings = warnings,
        )
    }

    private data class ParseOutcome(
        val isValid: Boolean,
        val candidates: List<KnownHostRecord> = emptyList(),
        val warnings: List<String> = emptyList(),
        val skippedLines: Int = 0,
        val errorMessage: String? = null,
    )

    private fun parseCandidates(content: String, manager: KnownHostsManager): ParseOutcome {
        val candidates = mutableListOf<KnownHostRecord>()
        val warnings = mutableListOf<String>()
        var skipped = 0

        val lines = content.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue
            }
            if (line.length > MAX_LINE_LENGTH) {
                warnings.add("Line exceeds maximum length of $MAX_LINE_LENGTH")
                skipped++
                continue
            }
            if (hasControlCharacters(line)) {
                warnings.add("Line contains forbidden control characters")
                skipped++
                continue
            }
            val record = manager.parseLine(line)
            if (record == null) {
                warnings.add("Malformed known_hosts line: ${line.take(40)}")
                skipped++
                continue
            }
            if (!validatePublicKeyBlob(record.keyType, record.publicKeyBase64)) {
                warnings.add("Invalid public key blob for host: ${record.rawHostPattern}")
                skipped++
                continue
            }
            candidates.add(record)
            if (candidates.size > MAX_ENTRY_COUNT) {
                return ParseOutcome(
                    isValid = false,
                    errorMessage = "Entry count exceeds maximum allowed limit of $MAX_ENTRY_COUNT entries",
                )
            }
        }

        return ParseOutcome(
            isValid = true,
            candidates = candidates,
            warnings = warnings,
            skippedLines = skipped,
        )
    }

    private data class EvaluationResult(
        val toAdd: List<KnownHostRecord>,
        val toReplace: Map<String, KnownHostRecord>,
        val conflicts: List<KnownHostConflict>,
        val skippedCount: Int,
        val overwrittenCount: Int,
    )

    private fun evaluateCandidates(
        candidates: List<KnownHostRecord>,
        existingRecords: List<KnownHostRecord>,
        manager: KnownHostsManager,
        strategy: KnownHostsMergeStrategy,
    ): EvaluationResult {
        val toAdd = mutableListOf<KnownHostRecord>()
        val toReplace = mutableMapOf<String, KnownHostRecord>()
        val conflicts = mutableListOf<KnownHostConflict>()
        var skipped = 0
        var overwritten = 0
        val batchProcessed = mutableListOf<KnownHostRecord>()

        for (candidate in candidates) {
            val intraConflict = checkIntraBatchConflict(candidate, batchProcessed, manager)
            if (intraConflict != null) {
                if (intraConflict.isDuplicate) {
                    skipped++
                } else {
                    conflicts.add(intraConflict.conflict!!)
                }
                continue
            }

            val matchingExisting = existingRecords.filter { recordsMatch(manager, candidate, it) }
            val sameKeyType = matchingExisting.find { it.keyType == candidate.keyType }

            if (sameKeyType != null) {
                if (sameKeyType.publicKeyBase64 == candidate.publicKeyBase64) {
                    skipped++
                    batchProcessed.add(candidate)
                } else {
                    val conflict = KnownHostConflict(
                        host = candidate.rawHostPattern,
                        existingRecord = sameKeyType,
                        incomingRecord = candidate,
                        message = "Host key conflict detected for ${candidate.rawHostPattern}",
                    )
                    when (strategy) {
                        KnownHostsMergeStrategy.STRICT_REJECT_CONFLICTS -> {
                            conflicts.add(conflict)
                        }
                        KnownHostsMergeStrategy.SKIP_EXISTING -> {
                            skipped++
                            batchProcessed.add(sameKeyType)
                        }
                        KnownHostsMergeStrategy.OVERWRITE_CHANGED -> {
                            toReplace[sameKeyType.id] = candidate
                            overwritten++
                            batchProcessed.add(candidate)
                        }
                    }
                }
            } else {
                toAdd.add(candidate)
                batchProcessed.add(candidate)
            }
        }

        return EvaluationResult(
            toAdd = toAdd,
            toReplace = toReplace,
            conflicts = conflicts,
            skippedCount = skipped,
            overwrittenCount = overwritten,
        )
    }

    private data class IntraBatchResult(
        val isDuplicate: Boolean,
        val conflict: KnownHostConflict? = null,
    )

    private fun checkIntraBatchConflict(
        candidate: KnownHostRecord,
        batchProcessed: List<KnownHostRecord>,
        manager: KnownHostsManager,
    ): IntraBatchResult? {
        val match = batchProcessed.find { recordsMatch(manager, candidate, it) } ?: return null
        if (match.keyType != candidate.keyType) return null

        return if (match.publicKeyBase64 == candidate.publicKeyBase64) {
            IntraBatchResult(isDuplicate = true)
        } else {
            val conflict = KnownHostConflict(
                host = candidate.rawHostPattern,
                existingRecord = match,
                incomingRecord = candidate,
                message = "Conflict within import payload for host ${candidate.rawHostPattern}",
            )
            IntraBatchResult(isDuplicate = false, conflict = conflict)
        }
    }

    private fun applyImportChanges(
        manager: KnownHostsManager,
        toAdd: List<KnownHostRecord>,
        toReplace: Map<String, KnownHostRecord>,
    ) {
        if (toAdd.isEmpty() && toReplace.isEmpty()) return

        val linesToKeep = mutableListOf<String>()
        if (manager.knownHostsFile.exists()) {
            manager.knownHostsFile.forEachLine { line ->
                val parsed = manager.parseLine(line)
                if (parsed != null && toReplace.containsKey(parsed.id)) {
                    val replacement = toReplace[parsed.id]!!
                    linesToKeep.add(formatRecordLine(replacement))
                } else {
                    linesToKeep.add(line)
                }
            }
        }
        for (record in toAdd) {
            linesToKeep.add(formatRecordLine(record))
        }
        atomicWriteLines(manager.knownHostsFile, linesToKeep)
    }

    /**
     * Matches two records across hostname variations, ports, and HMAC-SHA1 hashed entries.
     */
    fun recordsMatch(manager: KnownHostsManager, incoming: KnownHostRecord, existing: KnownHostRecord): Boolean {
        if (incoming.isCertAuthority != existing.isCertAuthority) return false
        if (incoming.isRevoked != existing.isRevoked) return false

        if (incoming.isCertAuthority || incoming.isRevoked) {
            return incoming.rawHostPattern == existing.rawHostPattern
        }

        if (incoming.rawHostPattern == existing.rawHostPattern) {
            return true
        }

        if (!incoming.isHashed) {
            val patterns = incoming.rawHostPattern.split(",")
            for (pattern in patterns) {
                val (h, p) = parseHostAndPort(pattern)
                if (manager.matchesHost(existing, h, p)) {
                    return true
                }
            }
        }

        if (!existing.isHashed && incoming.isHashed) {
            val patterns = existing.rawHostPattern.split(",")
            for (pattern in patterns) {
                val (h, p) = parseHostAndPort(pattern)
                if (manager.matchesHost(incoming, h, p)) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Splits a host token into hostname and port, handling bracketed [host]:port notation.
     */
    fun parseHostAndPort(pattern: String): Pair<String, Int> {
        val trimmed = pattern.trim()
        if (trimmed.startsWith("[") && trimmed.contains("]:")) {
            val host = trimmed.substring(1, trimmed.indexOf("]:"))
            val portStr = trimmed.substring(trimmed.indexOf("]:") + 2)
            val port = portStr.toIntOrNull() ?: 22
            return Pair(host, port)
        }
        return Pair(trimmed, 22)
    }

    private fun atomicWriteLines(targetFile: File, lines: List<String>) {
        val parentDir = targetFile.parentFile ?: File(".")
        parentDir.mkdirs()
        val tempFile = File.createTempFile("ssh_known_hosts_bulk_", ".tmp", parentDir)
        try {
            tempFile.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                for (line in lines) {
                    writer.write(line)
                    writer.newLine()
                }
            }
            try {
                Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }
}
