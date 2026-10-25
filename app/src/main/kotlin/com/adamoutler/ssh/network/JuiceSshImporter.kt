package com.adamoutler.ssh.network

import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.Protocol
import com.adamoutler.ssh.util.AppLog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID

/**
 * Hardened validator for hostnames, IP addresses, ports, and usernames.
 * Neutralizes control characters, terminal escape sequences, and injection vectors.
 */
object HostValidator {
    const val MAX_HOST_LENGTH = 255
    const val MAX_USERNAME_LENGTH = 128
    const val MIN_PORT = 1
    const val MAX_PORT = 65535

    private val FORBIDDEN_HOST_CHARS = setOf('\r', '\n', '\u0000', ';', '&', '|', '`', '$', '<', '>', '"', '\'', ' ', '\t')
    private val FORBIDDEN_USER_CHARS = setOf('\r', '\n', '\u0000', ';', '&', '|', '`', '$', '<', '>', '"', '\'', ' ', '\t')

    /**
     * Strips control characters (\u0000..\u001F, \u007F) and trims leading/trailing whitespace.
     */
    fun stripControlChars(input: String?): String {
        if (input == null) return ""
        return input.filter { it >= ' ' && it != '\u007F' }.trim()
    }

    /**
     * Validates whether a given host string is a valid hostname, IPv4, or IPv6 address.
     */
    fun isValidHost(rawHost: String?): Boolean {
        if (rawHost.isNullOrBlank()) return false
        val host = stripControlChars(rawHost)
        if (host.isEmpty() || host.length > MAX_HOST_LENGTH) return false
        if (host.any { it in FORBIDDEN_HOST_CHARS }) return false

        // Check bracketed IPv6 notation (e.g., [::1] or [2001:db8::1])
        val unbracketed = if (host.startsWith("[") && host.endsWith("]")) {
            host.substring(1, host.length - 1)
        } else {
            host
        }
        if (unbracketed.isEmpty()) return false

        // IPv4 format
        val ipv4Regex = Regex("^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.){3}(25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)$")
        if (ipv4Regex.matches(host)) return true

        // IPv6 format (hex colons)
        if (unbracketed.contains(":") && unbracketed.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' }) {
            return true
        }

        // Hostname / domain format (RFC 1123)
        val hostRegex = Regex("^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$")
        return hostRegex.matches(host)
    }

    /**
     * Validates port number bounds (1..65535).
     */
    fun isValidPort(port: Int): Boolean = port in MIN_PORT..MAX_PORT

    /**
     * Validates username string against length and disallowed control/injection characters.
     */
    fun isValidUsername(rawUsername: String?): Boolean {
        if (rawUsername.isNullOrEmpty()) return true
        val username = stripControlChars(rawUsername)
        if (username.length > MAX_USERNAME_LENGTH) return false
        if (username.any { it in FORBIDDEN_USER_CHARS }) return false
        return true
    }
}

/**
 * Strategy for resolving profile nickname collisions during backup import.
 */
enum class ImportCollisionStrategy {
    SKIP,
    OVERWRITE,
    RENAME,
}

/**
 * Result structure of a JuiceSSH or portable profile import operation.
 */
data class JuiceSshImportResult(
    val profiles: List<ConnectionProfile>,
    val importedProfiles: List<ConnectionProfile>,
    val skippedProfiles: List<String>,
    val warnings: List<String>,
)

/**
 * Hardened importer for JuiceSSH JSON backup structures.
 *
 * Security Invariants:
 * 1. Payload Bounds: Maximum 1 MB payload size to eliminate OOM/DoS attack vectors.
 * 2. Profile Bounds: Maximum 500 connections parsed per payload.
 * 3. Input Sanitization: Strict control character stripping (\r, \n, \u0000) on all string fields.
 * 4. Host Validation: Strict RFC 1123, IPv4, and IPv6 host verification.
 * 5. Collision Handling: Deterministic resolution strategies (SKIP, OVERWRITE, RENAME).
 */
object JuiceSshImporter {
    private const val TAG = "JuiceSshImporter"

    const val MAX_PAYLOAD_BYTES = 1024 * 1024 // 1 MB
    const val MAX_ENTRY_COUNT = 500

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Parses a JuiceSSH JSON backup string and merges with existing profiles using the specified strategy.
     */
    fun import(
        json: String,
        existingProfiles: List<ConnectionProfile> = emptyList(),
        strategy: ImportCollisionStrategy = ImportCollisionStrategy.SKIP,
    ): JuiceSshImportResult {
        val byteCount = json.toByteArray(Charsets.UTF_8).size
        if (byteCount > MAX_PAYLOAD_BYTES) {
            val warning = "Payload size ($byteCount bytes) exceeds maximum allowed limit of 1 MB ($MAX_PAYLOAD_BYTES bytes)"
            AppLog.w(TAG, warning)
            return JuiceSshImportResult(
                profiles = existingProfiles,
                importedProfiles = emptyList(),
                skippedProfiles = listOf("PAYLOAD_SIZE_EXCEEDED"),
                warnings = listOf(warning),
            )
        }

        val jsonElement = try {
            jsonParser.parseToJsonElement(json)
        } catch (e: Exception) {
            val warning = "Invalid JSON structure: ${e.message}"
            AppLog.w(TAG, warning)
            return JuiceSshImportResult(
                profiles = existingProfiles,
                importedProfiles = emptyList(),
                skippedProfiles = emptyList(),
                warnings = listOf(warning),
            )
        }

        val warnings = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val groupMap = mutableMapOf<String, String>()

        val connectionsArray: JsonArray? = when (jsonElement) {
            is JsonObject -> {
                val groups = jsonElement["groups"]?.jsonArray
                if (groups != null) {
                    for (groupElem in groups) {
                        if (groupElem is JsonObject) {
                            val id = groupElem["id"]?.jsonPrimitive?.contentOrNull?.let { HostValidator.stripControlChars(it) }
                            val name = groupElem["name"]?.jsonPrimitive?.contentOrNull?.let { HostValidator.stripControlChars(it) }
                            if (!id.isNullOrBlank() && !name.isNullOrBlank()) {
                                groupMap[id] = name
                            }
                        }
                    }
                }
                jsonElement["connections"]?.jsonArray
            }

            is JsonArray -> jsonElement

            else -> null
        }

        if (connectionsArray == null) {
            warnings.add("No 'connections' array found in JSON payload")
            return JuiceSshImportResult(
                profiles = existingProfiles,
                importedProfiles = emptyList(),
                skippedProfiles = emptyList(),
                warnings = warnings,
            )
        }

        val parsedProfiles = mutableListOf<ConnectionProfile>()

        for ((index, element) in connectionsArray.withIndex()) {
            if (index >= MAX_ENTRY_COUNT) {
                if (index == MAX_ENTRY_COUNT) {
                    warnings.add("Exceeded maximum entry limit of $MAX_ENTRY_COUNT connections; remaining entries skipped.")
                }
                val rawName = (element as? JsonObject)?.let {
                    (it["name"] ?: it["nickname"])?.jsonPrimitive?.contentOrNull
                } ?: "connection_$index"
                skipped.add(rawName)
                continue
            }

            if (element !is JsonObject) {
                warnings.add("Skipping non-object connection entry at index $index")
                continue
            }

            val rawName = (element["name"] ?: element["nickname"])?.jsonPrimitive?.contentOrNull
            val rawAddress = (element["address"] ?: element["host"] ?: element["hostname"])?.jsonPrimitive?.contentOrNull
            val rawPort = element["port"]?.jsonPrimitive?.contentOrNull
            val rawUser = (element["username"] ?: element["user"])?.jsonPrimitive?.contentOrNull
            val rawType = (element["type"] ?: element["protocol"])?.jsonPrimitive?.contentOrNull
            val rawGroup = (element["group"] ?: element["groupId"] ?: element["group_id"])?.jsonPrimitive?.contentOrNull

            val cleanName = HostValidator.stripControlChars(rawName)
            val cleanHost = HostValidator.stripControlChars(rawAddress)
            val cleanUser = HostValidator.stripControlChars(rawUser)
            val port = rawPort?.toIntOrNull() ?: 22

            if (!HostValidator.isValidHost(cleanHost)) {
                val skippedIdentifier = cleanName.ifBlank { cleanHost.ifBlank { "entry_$index" } }
                warnings.add("Skipping connection '$skippedIdentifier' with invalid host: '$cleanHost'")
                skipped.add(skippedIdentifier)
                continue
            }

            if (!HostValidator.isValidPort(port)) {
                val skippedIdentifier = cleanName.ifBlank { cleanHost }
                warnings.add("Skipping connection '$skippedIdentifier' with invalid port: $port")
                skipped.add(skippedIdentifier)
                continue
            }

            if (!HostValidator.isValidUsername(cleanUser)) {
                val skippedIdentifier = cleanName.ifBlank { cleanHost }
                warnings.add("Skipping connection '$skippedIdentifier' with invalid username: '$cleanUser'")
                skipped.add(skippedIdentifier)
                continue
            }

            val cleanType = HostValidator.stripControlChars(rawType).uppercase()
            val protocol = if (cleanType == "TELNET") Protocol.TELNET else Protocol.SSH

            val cleanGroupRef = HostValidator.stripControlChars(rawGroup)
            val resolvedFolder = if (cleanGroupRef.isNotBlank()) {
                groupMap[cleanGroupRef] ?: cleanGroupRef
            } else {
                null
            }

            val profile = ConnectionProfile(
                id = UUID.randomUUID().toString(),
                nickname = cleanName.ifBlank { cleanHost },
                host = cleanHost,
                port = port,
                username = cleanUser,
                protocol = protocol,
                folderId = resolvedFolder,
            )
            parsedProfiles.add(profile)
        }

        // Apply Collision Resolution Strategy
        val workingProfiles = existingProfiles.toMutableList()
        val importedProfiles = mutableListOf<ConnectionProfile>()

        for (candidate in parsedProfiles) {
            val existingIndex = workingProfiles.indexOfFirst {
                it.nickname.equals(candidate.nickname, ignoreCase = true)
            }

            if (existingIndex == -1) {
                workingProfiles.add(candidate)
                importedProfiles.add(candidate)
            } else {
                when (strategy) {
                    ImportCollisionStrategy.SKIP -> {
                        skipped.add(candidate.nickname)
                    }

                    ImportCollisionStrategy.OVERWRITE -> {
                        val existing = workingProfiles[existingIndex]
                        val merged = candidate.copy(
                            id = existing.id,
                            sortOrder = existing.sortOrder,
                        )
                        workingProfiles[existingIndex] = merged
                        importedProfiles.add(merged)
                    }

                    ImportCollisionStrategy.RENAME -> {
                        var counter = 1
                        var newNickname = "${candidate.nickname} ($counter)"
                        while (workingProfiles.any { it.nickname.equals(newNickname, ignoreCase = true) }) {
                            counter++
                            newNickname = "${candidate.nickname} ($counter)"
                        }
                        val renamedCandidate = candidate.copy(nickname = newNickname)
                        workingProfiles.add(renamedCandidate)
                        importedProfiles.add(renamedCandidate)
                    }
                }
            }
        }

        return JuiceSshImportResult(
            profiles = workingProfiles,
            importedProfiles = importedProfiles,
            skippedProfiles = skipped,
            warnings = warnings,
        )
    }

    /**
     * Parses an input stream of JuiceSSH JSON data with streaming size limit enforcement.
     */
    fun import(
        inputStream: InputStream,
        existingProfiles: List<ConnectionProfile> = emptyList(),
        strategy: ImportCollisionStrategy = ImportCollisionStrategy.SKIP,
    ): JuiceSshImportResult {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(4096)
        var totalBytes = 0

        while (true) {
            val bytesRead = inputStream.read(chunk, 0, chunk.size)
            if (bytesRead == -1) break
            totalBytes += bytesRead
            if (totalBytes > MAX_PAYLOAD_BYTES) {
                val warning = "InputStream exceeds maximum allowed limit of 1 MB ($MAX_PAYLOAD_BYTES bytes)"
                AppLog.w(TAG, warning)
                return JuiceSshImportResult(
                    profiles = existingProfiles,
                    importedProfiles = emptyList(),
                    skippedProfiles = listOf("PAYLOAD_SIZE_EXCEEDED"),
                    warnings = listOf(warning),
                )
            }
            buffer.write(chunk, 0, bytesRead)
        }

        val jsonString = buffer.toString(Charsets.UTF_8.name())
        return import(jsonString, existingProfiles, strategy)
    }

    /**
     * Alias for import method for API parity.
     */
    fun parse(
        json: String,
        existingProfiles: List<ConnectionProfile> = emptyList(),
        strategy: ImportCollisionStrategy = ImportCollisionStrategy.SKIP,
    ): JuiceSshImportResult = import(json, existingProfiles, strategy)
}
