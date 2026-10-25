package com.adamoutler.ssh.network

import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.Protocol
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/**
 * Sanitized profile metadata model for portable, leak-proof JSON exports.
 * Strictly omits all secrets (passwords, private keys, keystore reference IDs).
 */
@Serializable
data class SanitizedProfileMetadata(
    val nickname: String,
    val host: String,
    val port: Int = 22,
    val username: String = "",
    val protocol: String = "SSH",
    val authType: String = "PASSWORD",
    val groupId: String? = null,
    val useMosh: Boolean = false,
    val keepAliveIntervalSeconds: Int = 0,
)

/**
 * Hardened exporter for portable OpenSSH configurations and sanitized JSON manifests.
 *
 * Security Invariants:
 * 1. Zero Secret Leakage: Passwords, private keys, and cryptographic references are never serialized.
 * 2. Directive Injection Defense: Control characters and newlines (\r, \n, \u0000) are rigorously stripped
 *    to prevent malicious injection of OpenSSH execution directives (e.g., ProxyCommand, LocalCommand).
 * 3. Round-Trip Parity: Output format verified for seamless bi-directional import via OpenSshConfigParser.
 */
object PortableProfileExporter {

    private val jsonFormat = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Exports SSH connection profiles to standard OpenSSH `~/.ssh/config` stanza blocks.
     * Non-SSH profiles (e.g. Telnet) are safely omitted.
     */
    fun exportToOpenSshConfig(profiles: List<ConnectionProfile>): String {
        val sshProfiles = profiles.filter { it.protocol == Protocol.SSH }
        if (sshProfiles.isEmpty()) return ""

        val profilesById = sshProfiles.associateBy { it.id }
        val sb = StringBuilder()

        for ((index, profile) in sshProfiles.withIndex()) {
            if (index > 0) {
                sb.append("\n")
            }

            val rawAlias = profile.nickname.ifBlank { profile.host }
            val cleanAlias = sanitizeAlias(rawAlias)
            val cleanHost = HostValidator.stripControlChars(profile.host)
            val cleanUser = HostValidator.stripControlChars(profile.username)

            sb.append("Host ").append(cleanAlias).append("\n")
            sb.append("    HostName ").append(cleanHost).append("\n")

            if (cleanUser.isNotEmpty()) {
                sb.append("    User ").append(cleanUser).append("\n")
            }

            if (profile.port in 1..65535 && profile.port != 22) {
                sb.append("    Port ").append(profile.port).append("\n")
            }

            if (profile.keepAliveIntervalSeconds > 0) {
                sb.append("    ServerAliveInterval ").append(profile.keepAliveIntervalSeconds).append("\n")
            }

            if (!profile.jumpHostProfileId.isNullOrBlank()) {
                val jumpTarget = profilesById[profile.jumpHostProfileId]
                if (jumpTarget != null && jumpTarget.id != profile.id) {
                    val jumpAlias = sanitizeAlias(jumpTarget.nickname.ifBlank { jumpTarget.host })
                    sb.append("    ProxyJump ").append(jumpAlias).append("\n")
                }
            }
        }

        return sb.toString()
    }

    /**
     * Serializes connection profiles to a clean, portable JSON format.
     * Guaranteed to exclude passwords, ciphertexts, and private keys.
     */
    fun exportToSanitizedJson(profiles: List<ConnectionProfile>): String {
        val sanitized = profiles.map { profile ->
            SanitizedProfileMetadata(
                nickname = HostValidator.stripControlChars(profile.nickname).ifBlank { HostValidator.stripControlChars(profile.host) },
                host = HostValidator.stripControlChars(profile.host),
                port = if (profile.port in 1..65535) profile.port else 22,
                username = HostValidator.stripControlChars(profile.username),
                protocol = profile.protocol.name,
                authType = profile.authType.name,
                groupId = profile.folderId?.let { HostValidator.stripControlChars(it).ifBlank { null } },
                useMosh = false,
                keepAliveIntervalSeconds = profile.keepAliveIntervalSeconds.coerceAtLeast(0),
            )
        }
        return jsonFormat.encodeToString(sanitized)
    }

    /**
     * Imports connection profiles from sanitized JSON export.
     */
    fun importFromSanitizedJson(json: String): List<ConnectionProfile> {
        val element = try {
            jsonFormat.parseToJsonElement(json)
        } catch (e: Exception) {
            return emptyList()
        }

        val array = when (element) {
            is JsonArray -> element
            is JsonObject -> (element["connections"] ?: element["profiles"])?.jsonArray ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }

        return array.mapNotNull { item ->
            if (item !is JsonObject) return@mapNotNull null
            val nickname = (item["nickname"] ?: item["name"])?.jsonPrimitive?.contentOrNull?.let { HostValidator.stripControlChars(it) } ?: ""
            val host = (item["host"] ?: item["address"])?.jsonPrimitive?.contentOrNull?.let { HostValidator.stripControlChars(it) } ?: ""
            if (!HostValidator.isValidHost(host)) return@mapNotNull null

            val port = item["port"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 22
            if (!HostValidator.isValidPort(port)) return@mapNotNull null

            val username = (item["username"] ?: item["user"])?.jsonPrimitive?.contentOrNull?.let { HostValidator.stripControlChars(it) } ?: ""
            if (!HostValidator.isValidUsername(username)) return@mapNotNull null

            val protocolStr = (item["protocol"] ?: item["type"])?.jsonPrimitive?.contentOrNull ?: "SSH"
            val protocol = if (protocolStr.equals("TELNET", ignoreCase = true)) Protocol.TELNET else Protocol.SSH

            val authTypeStr = item["authType"]?.jsonPrimitive?.contentOrNull ?: "PASSWORD"
            val authType = if (authTypeStr.equals("KEY", ignoreCase = true)) AuthType.KEY else AuthType.PASSWORD

            val groupId = (item["groupId"] ?: item["group"])?.jsonPrimitive?.contentOrNull?.let { HostValidator.stripControlChars(it).ifBlank { null } }
            val keepAlive = item["keepAliveIntervalSeconds"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0

            ConnectionProfile(
                id = UUID.randomUUID().toString(),
                nickname = nickname.ifBlank { host },
                host = host,
                port = port,
                username = username,
                protocol = protocol,
                authType = authType,
                folderId = groupId,
                keepAliveIntervalSeconds = keepAlive,
            )
        }
    }

    /**
     * Sanitizes a host pattern or alias to guarantee valid OpenSSH tokenization.
     * Spaces are converted to hyphens and control characters are stripped.
     */
    fun sanitizeAlias(rawAlias: String): String {
        val stripped = HostValidator.stripControlChars(rawAlias)
        val noWhitespace = stripped.replace(Regex("\\s+"), "-")
        val clean = noWhitespace.filter { it.isLetterOrDigit() || it in setOf('-', '_', '.', ':') }
        return clean.ifBlank { "host" }
    }
}
