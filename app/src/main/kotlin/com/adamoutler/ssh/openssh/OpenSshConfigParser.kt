package com.adamoutler.ssh.openssh

import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.IdentityProfile
import com.adamoutler.ssh.data.Protocol
import com.adamoutler.ssh.util.AppLog
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.util.UUID

/**
 * Result of parsing an OpenSSH `~/.ssh/config` file.
 */
data class OpenSshParseResult(
    val profiles: List<ConnectionProfile>,
    val sanitizedDirectives: List<String>,
    val skippedHosts: List<String>,
)

/**
 * Hardened, zero-dependency OpenSSH `~/.ssh/config` parser.
 *
 * Security Invariants:
 * 1. Strict Directive Allowlist: Only benign connection parameters are parsed.
 * 2. RCE Vector Neutralization: Execution directives (`ProxyCommand`, `LocalCommand`,
 *    `PermitLocalCommand`, `PKCS11Provider`, `Match`) are strictly stripped and logged.
 * 3. Path Traversal Neutralization: `IdentityFile` paths are stripped of directory components
 *    (e.g., `../../etc/passwd` -> `passwd`) and matched against pre-existing cryptographic identities.
 * 4. Input Bounds: Max 256 KB file size, 4,096 char line limit, 250 max host entries.
 */
object OpenSshConfigParser {
    private const val TAG = "OpenSshConfigParser"

    const val MAX_FILE_BYTES = 256 * 1024 // 256 KB
    const val MAX_LINE_LENGTH = 4096
    const val MAX_HOST_ENTRIES = 250

    private val ALLOWED_DIRECTIVES = setOf(
        "host",
        "hostname",
        "port",
        "user",
        "proxyjump",
        "serveraliveinterval",
        "serveralivecountmax",
        "connecttimeout",
        "identityfile",
    )

    private val REJECTED_RCE_DIRECTIVES = setOf(
        "proxycommand",
        "localcommand",
        "permitlocalcommand",
        "pkcs11provider",
        "match",
    )

    private class HostStanza {
        val hostPatterns = mutableListOf<String>()
        var hostName: String? = null
        var port: Int? = null
        var user: String? = null
        var identityFileName: String? = null
        var proxyJump: String? = null
        var serverAliveInterval: Int? = null
    }

    /**
     * Parses an OpenSSH configuration stream.
     */
    fun parse(
        inputStream: InputStream,
        existingIdentities: List<IdentityProfile> = emptyList(),
        existingProfiles: List<ConnectionProfile> = emptyList(),
    ): OpenSshParseResult {
        val sanitized = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val identitiesByName = existingIdentities.associateBy { it.name.lowercase() }

        var totalBytesRead = 0
        val stanzas = mutableListOf<HostStanza>()
        var currentStanza: HostStanza? = null

        val globalDefaults = HostStanza()

        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        var lineNum = 0

        while (true) {
            val rawLine = reader.readLine() ?: break
            lineNum++
            totalBytesRead += rawLine.length + 1
            if (totalBytesRead > MAX_FILE_BYTES) {
                AppLog.w(TAG, "Config exceeds maximum size limit of $MAX_FILE_BYTES bytes; stopping parse.")
                break
            }

            if (rawLine.length > MAX_LINE_LENGTH) {
                AppLog.w(TAG, "Line $lineNum exceeds maximum length ($MAX_LINE_LENGTH chars); skipped.")
                continue
            }

            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

            // Parse directive key and value (separated by whitespace or '=')
            val parts = trimmed.split(Regex("[\\s=]+", RegexOption.IGNORE_CASE), limit = 2)
            if (parts.isEmpty()) continue

            val directive = parts[0].lowercase()
            val value = if (parts.size > 1) parts[1].trim().removeSurrounding("\"") else ""

            if (directive in REJECTED_RCE_DIRECTIVES) {
                val warning = "Rejected dangerous directive '$directive' at line $lineNum"
                AppLog.w(TAG, warning)
                sanitized.add(warning)
                continue
            }

            if (directive !in ALLOWED_DIRECTIVES) {
                // Ignore unrecognized non-whitelisted directive safely
                continue
            }

            if (directive == "host") {
                val patterns = value.split(Regex("\\s+")).filter { it.isNotEmpty() }
                if (patterns.contains("*")) {
                    currentStanza = globalDefaults
                } else {
                    if (stanzas.size >= MAX_HOST_ENTRIES) {
                        AppLog.w(TAG, "Reached maximum host entries limit ($MAX_HOST_ENTRIES); skipping remainder.")
                        skipped.addAll(patterns)
                        continue
                    }
                    val stanza = HostStanza()
                    stanza.hostPatterns.addAll(patterns)
                    stanzas.add(stanza)
                    currentStanza = stanza
                }
                continue
            }

            val target = currentStanza ?: globalDefaults
            when (directive) {
                "hostname" -> target.hostName = value
                "port" -> target.port = value.toIntOrNull()?.coerceIn(1, 65535)
                "user" -> target.user = value
                "serveraliveinterval" -> target.serverAliveInterval = value.toIntOrNull()?.coerceAtLeast(0)
                "proxyjump" -> target.proxyJump = value
                "identityfile" -> {
                    // Path Traversal Neutralization: Extract strictly the filename basename
                    val sanitizedFilename = File(value).name.trim()
                    if (sanitizedFilename.isNotEmpty()) {
                        target.identityFileName = sanitizedFilename
                    }
                }
            }
        }

        // Pass 2: Build ConnectionProfile instances
        val parsedProfiles = mutableListOf<ConnectionProfile>()
        val parsedByNickname = mutableMapOf<String, ConnectionProfile>()

        for (stanza in stanzas) {
            for (pattern in stanza.hostPatterns) {
                if (pattern.contains("*") || pattern.contains("?")) {
                    skipped.add(pattern)
                    continue
                }

                val nickname = pattern
                val host = stanza.hostName ?: globalDefaults.hostName ?: pattern
                val port = stanza.port ?: globalDefaults.port ?: 22
                val username = stanza.user ?: globalDefaults.user ?: ""
                val keepAlive = stanza.serverAliveInterval ?: globalDefaults.serverAliveInterval ?: 0

                var identityId: String? = null
                var authType = AuthType.PASSWORD

                val keyName = stanza.identityFileName ?: globalDefaults.identityFileName
                if (keyName != null) {
                    val matchedIdentity = identitiesByName[keyName.lowercase()]
                    if (matchedIdentity != null) {
                        identityId = matchedIdentity.id
                        authType = AuthType.KEY
                    }
                }

                val profile = ConnectionProfile(
                    id = UUID.randomUUID().toString(),
                    nickname = nickname,
                    host = host,
                    port = port,
                    username = username,
                    protocol = Protocol.SSH,
                    authType = authType,
                    identityId = identityId,
                    keepAliveIntervalSeconds = keepAlive,
                )
                parsedProfiles.add(profile)
                parsedByNickname[nickname.lowercase()] = profile
            }
        }

        // Pass 3: Resolve ProxyJump linking and cycle guards
        val existingByNickname = existingProfiles.associateBy { it.nickname.lowercase() }
        val finalProfiles = mutableListOf<ConnectionProfile>()

        for (profile in parsedProfiles) {
            val matchingStanza = stanzas.find { it.hostPatterns.any { pat -> pat.equals(profile.nickname, ignoreCase = true) } }
            val jumpTarget = matchingStanza?.proxyJump ?: globalDefaults.proxyJump

            var resolvedJumpHostId: String? = null
            if (!jumpTarget.isNullOrBlank()) {
                val jumpAlias = jumpTarget.split(",").firstOrNull()?.trim()?.lowercase()
                if (jumpAlias != null) {
                    val resolvedTarget = parsedByNickname[jumpAlias] ?: existingByNickname[jumpAlias]
                    if (resolvedTarget != null && resolvedTarget.id != profile.id) {
                        resolvedJumpHostId = resolvedTarget.id
                    }
                }
            }

            finalProfiles.add(profile.copy(jumpHostProfileId = resolvedJumpHostId))
        }

        return OpenSshParseResult(
            profiles = finalProfiles,
            sanitizedDirectives = sanitized,
            skippedHosts = skipped,
        )
    }
}
