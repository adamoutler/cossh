package com.adamoutler.ssh.util

import com.adamoutler.ssh.data.Protocol

/**
 * Parsed target representation for Quick Connect ad-hoc sessions.
 */
data class QuickConnectTarget(
    val host: String,
    val port: Int,
    val username: String,
    val protocol: Protocol,
)

/**
 * Hardened parser for ad-hoc Quick Connect destination strings.
 *
 * Supported formats:
 * - `hostname`
 * - `user@hostname`
 * - `hostname:port`
 * - `user@hostname:port`
 * - `ssh://[user@]hostname[:port]`
 * - `telnet://[user@]hostname[:port]`
 * - IPv6 addresses: `[::1]`, `user@[::1]:2222`
 */
object QuickConnectParser {

    private val FORBIDDEN_CHARS = setOf(' ', '\t', '\r', '\n', ';', '&', '|', '`', '$', '<', '>', '"', '\'')

    fun parse(input: String, defaultProtocol: Protocol = Protocol.SSH): Result<QuickConnectTarget> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Target address cannot be empty"))
        }

        if (trimmed.any { it in FORBIDDEN_CHARS }) {
            return Result.failure(IllegalArgumentException("Address contains invalid or disallowed characters"))
        }

        var protocol = defaultProtocol
        var remainder = trimmed

        if (remainder.startsWith("ssh://", ignoreCase = true)) {
            protocol = Protocol.SSH
            remainder = remainder.substring(6)
        } else if (remainder.startsWith("telnet://", ignoreCase = true)) {
            protocol = Protocol.TELNET
            remainder = remainder.substring(9)
        }

        if (remainder.isEmpty()) {
            return Result.failure(IllegalArgumentException("Host component missing in address"))
        }

        var username = ""
        val atIndex = remainder.lastIndexOf('@')
        if (atIndex != -1) {
            username = remainder.substring(0, atIndex)
            remainder = remainder.substring(atIndex + 1)
            if (remainder.isEmpty()) {
                return Result.failure(IllegalArgumentException("Host missing after user@"))
            }
        }

        var host: String
        var port: Int = if (protocol == Protocol.TELNET) 23 else 22

        if (remainder.startsWith("[")) {
            // IPv6 bracketed notation: [::1] or [::1]:port
            val closeBracketIndex = remainder.indexOf(']')
            if (closeBracketIndex == -1) {
                return Result.failure(IllegalArgumentException("Unmatched IPv6 bracket in address"))
            }
            host = remainder.substring(1, closeBracketIndex)
            val afterBracket = remainder.substring(closeBracketIndex + 1)
            if (afterBracket.startsWith(":")) {
                val portStr = afterBracket.substring(1)
                val parsedPort = portStr.toIntOrNull()
                    ?: return Result.failure(IllegalArgumentException("Invalid port: $portStr"))
                if (parsedPort !in 1..65535) {
                    return Result.failure(IllegalArgumentException("Port must be between 1 and 65535"))
                }
                port = parsedPort
            } else if (afterBracket.isNotEmpty()) {
                return Result.failure(IllegalArgumentException("Unexpected trailing characters after IPv6 bracket: $afterBracket"))
            }
        } else {
            // Standard host[:port]
            val colonIndex = remainder.lastIndexOf(':')
            if (colonIndex != -1) {
                host = remainder.substring(0, colonIndex)
                val portStr = remainder.substring(colonIndex + 1)
                val parsedPort = portStr.toIntOrNull()
                    ?: return Result.failure(IllegalArgumentException("Invalid port: $portStr"))
                if (parsedPort !in 1..65535) {
                    return Result.failure(IllegalArgumentException("Port must be between 1 and 65535"))
                }
                port = parsedPort
            } else {
                host = remainder
            }
        }

        if (host.isEmpty()) {
            return Result.failure(IllegalArgumentException("Host cannot be empty"))
        }

        return Result.success(
            QuickConnectTarget(
                host = host,
                port = port,
                username = username,
                protocol = protocol,
            ),
        )
    }
}
