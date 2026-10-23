package com.adamoutler.ssh.crypto

import android.content.Context
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class KnownHostRecord(
    val id: String = UUID.randomUUID().toString(),
    val rawHostPattern: String,
    val displayHost: String,
    val isHashed: Boolean,
    val keyType: String,
    val fingerprint: String,
    val publicKeyBase64: String,
    val rawLine: String,
    val saltBase64: String? = null,
    val hashBase64: String? = null,
)

open class KnownHostsManager(
    val knownHostsFile: File,
) {
    constructor(context: Context) : this(File(context.filesDir, "ssh_known_hosts"))

    private val lock = Any()

    /**
     * Parses the known_hosts file and returns all valid entries.
     */
    open fun getEntries(): List<KnownHostRecord> = synchronized(lock) {
        if (!knownHostsFile.exists()) return emptyList()
        val records = mutableListOf<KnownHostRecord>()
        try {
            knownHostsFile.forEachLine { line ->
                parseLine(line)?.let { records.add(it) }
            }
        } catch (_: Exception) {
            // Safe fallback on file read errors
        }
        return records
    }

    /**
     * Deletes a single entry by its unique record ID.
     */
    open fun removeEntry(recordId: String): Boolean = removeEntries(setOf(recordId)) > 0

    /**
     * Deletes multiple entries by their unique record IDs.
     */
    open fun removeEntries(recordIds: Set<String>): Int = synchronized(lock) {
        if (!knownHostsFile.exists() || recordIds.isEmpty()) return 0
        val currentRecords = getEntries()
        val toRemove = currentRecords.filter { it.id in recordIds }
        if (toRemove.isEmpty()) return 0

        val linesToKeep = mutableListOf<String>()
        val removedLines = toRemove.map { it.rawLine }.toSet()

        knownHostsFile.forEachLine { line ->
            if (line !in removedLines) {
                linesToKeep.add(line)
            }
        }

        atomicWriteLines(linesToKeep)
        return toRemove.size
    }

    /**
     * Hashes all plaintext host entries using OpenSSH HMAC-SHA1 format (`|1|<salt_b64>|<hash_b64>`).
     * Returns the count of hosts converted.
     */
    open fun hashAllPlaintextHosts(): Int = synchronized(lock) {
        if (!knownHostsFile.exists()) return 0
        var convertedCount = 0
        val newLines = mutableListOf<String>()

        knownHostsFile.forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                newLines.add(line)
                return@forEachLine
            }

            val record = parseLine(line)
            if (record != null && !record.isHashed) {
                val hashedToken = hashHostPattern(record.rawHostPattern)
                newLines.add("$hashedToken ${record.keyType} ${record.publicKeyBase64}")
                convertedCount++
            } else {
                newLines.add(line)
            }
        }

        if (convertedCount > 0) {
            atomicWriteLines(newLines)
        }
        return convertedCount
    }

    /**
     * Thread-safe atomic update or insertion of a host key entry.
     * Accurately removes any previous matching entry (whether plaintext or hashed) before writing.
     */
    open fun saveOrUpdateHostKey(
        formattedHost: String,
        port: Int,
        keyType: String,
        keyBlobBase64: String,
        hashHost: Boolean = true,
    ): Boolean = synchronized(lock) {
        val newHostSpec = if (hashHost) {
            hashHostPattern(formattedHost)
        } else {
            formattedHost
        }
        val newEntryLine = "$newHostSpec $keyType $keyBlobBase64"

        val linesToKeep = mutableListOf<String>()
        if (knownHostsFile.exists()) {
            knownHostsFile.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    linesToKeep.add(line)
                    return@forEachLine
                }
                val record = parseLine(line)
                val matches = if (record != null) {
                    matchesHost(record, formattedHost, port)
                } else {
                    false
                }
                if (!matches) {
                    linesToKeep.add(line)
                }
            }
        }

        linesToKeep.add(newEntryLine)
        atomicWriteLines(linesToKeep)
        return true
    }

    /**
     * Determines whether a given record applies to the candidate host and port.
     * Supports both plain host comparison and HMAC-SHA1 comparison for hashed entries.
     */
    fun matchesHost(record: KnownHostRecord, host: String, port: Int): Boolean {
        val formattedHost = if (port != 22 && !host.startsWith("[")) "[$host]:$port" else host
        val plainHost = if (host.startsWith("[") && host.contains("]:")) {
            host.substring(1, host.indexOf("]:"))
        } else {
            host
        }

        if (record.isHashed) {
            val saltB64 = record.saltBase64 ?: return false
            val hashB64 = record.hashBase64 ?: return false
            return try {
                val salt = Base64.getDecoder().decode(saltB64)
                val expectedHash = Base64.getDecoder().decode(hashB64)

                val candidates = mutableListOf(formattedHost)
                if (port == 22) candidates.add(plainHost)

                candidates.any { candidate ->
                    val computedHash = computeHmacSha1(salt, candidate)
                    MessageDigest.isEqual(computedHash, expectedHash)
                }
            } catch (_: Exception) {
                false
            }
        } else {
            val patterns = record.rawHostPattern.split(",")
            return patterns.contains(formattedHost) || (port == 22 && patterns.contains(plainHost))
        }
    }

    fun parseLine(line: String): KnownHostRecord? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null
        val tokens = trimmed.split("\\s+".toRegex())
        if (tokens.size < 3) return null

        val hostToken = tokens[0]
        val keyType = tokens[1]
        val keyBlob = tokens[2]

        val isHashed = hostToken.startsWith("|1|")
        var saltB64: String? = null
        var hashB64: String? = null
        val displayHost: String

        if (isHashed) {
            val parts = hostToken.split("|")
            if (parts.size >= 4) {
                saltB64 = parts[2]
                hashB64 = parts[3]
                displayHost = "|1|${saltB64.take(8)}... (Hashed Host)"
            } else {
                displayHost = hostToken
            }
        } else {
            displayHost = hostToken
        }

        val fingerprint = try {
            val decoded = Base64.getDecoder().decode(keyBlob)
            val md = MessageDigest.getInstance("SHA-256")
            "SHA256:" + Base64.getEncoder().encodeToString(md.digest(decoded)).trimEnd('=')
        } catch (_: Exception) {
            "SHA256:Unknown"
        }

        val id = UUID.nameUUIDFromBytes(line.toByteArray(StandardCharsets.UTF_8)).toString()

        return KnownHostRecord(
            id = id,
            rawHostPattern = hostToken,
            displayHost = displayHost,
            isHashed = isHashed,
            keyType = keyType,
            fingerprint = fingerprint,
            publicKeyBase64 = keyBlob,
            rawLine = line,
            saltBase64 = saltB64,
            hashBase64 = hashB64,
        )
    }

    fun hashHostPattern(host: String): String {
        val salt = ByteArray(20)
        SecureRandom().nextBytes(salt)
        val hash = computeHmacSha1(salt, host)
        val saltB64 = Base64.getEncoder().encodeToString(salt)
        val hashB64 = Base64.getEncoder().encodeToString(hash)
        return "|1|$saltB64|$hashB64"
    }

    fun computeHmacSha1(salt: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(salt, "HmacSHA1"))
        return mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
    }

    private fun atomicWriteLines(lines: List<String>) {
        val parentDir = knownHostsFile.parentFile ?: File(".")
        parentDir.mkdirs()
        val tempFile = File.createTempFile("ssh_known_hosts_", ".tmp", parentDir)
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
                    knownHostsFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    tempFile.toPath(),
                    knownHostsFile.toPath(),
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
