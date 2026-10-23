package com.adamoutler.ssh.util

import android.net.Uri
import java.net.IDN
import java.net.URI
import java.util.regex.Pattern

/**
 * Data class representing a verified, sanitized URL extracted from terminal output.
 */
data class SanitizedUrl(
    val rawUrl: String,
    val uri: Uri,
    val scheme: String,
    val host: String,
    val port: Int,
    val isSecure: Boolean,
    val isDirectIp: Boolean,
    val isPunycode: Boolean,
    val punycodeHost: String,
    val unicodeHost: String,
)

/**
 * Security-hardened utility for extracting, sanitizing, and validating URLs from untrusted terminal buffers.
 *
 * Defense Invariants:
 * - Scheme Pinner: Only allows "http" and "https" schemes. All dangerous schemes (file:, content:, intent:, javascript:, data:) are rejected.
 * - Authority Neutralization: Strict rejection of userinfo/credential spoofing (e.g. user@evil.com).
 * - Unicode Bidi Stripping: Neutralizes Trojan Source and bidirectional overrides.
 * - IDN Homoglyph Detection: Detects Punycode lookalike domains (xn--).
 * - Direct IP Identification: Identifies raw IP addresses lacking standard certificate parity.
 */
object TerminalUrlSanitizer {
    const val MAX_URL_LENGTH = 2048

    private val BIDI_CONTROL_REGEX = Regex("[\u200E\u200F\u202A-\u202E\u2066-\u2069\u0000-\u001F\u007F]")

    // Strict RFC HTTP/HTTPS pattern with word boundary
    private val URL_PATTERN = Pattern.compile(
        "\\bhttps?://[a-zA-Z0-9.-]+(?::\\d{1,5})?(?:/[^\\s<>\"'\u0000-\u001F\u007F]*)?",
        Pattern.CASE_INSENSITIVE
    )

    private val TRAILING_PUNCTUATION = charArrayOf(')', ']', '}', '.', ',', ';', ':', '\'', '"', '>')

    private val IPV4_PATTERN = Pattern.compile(
        "^(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    )

    /**
     * Extracts and validates all valid HTTP/HTTPS URLs from raw terminal text.
     */
    fun extractValidUrls(rawText: String): List<SanitizedUrl> {
        val sanitizedText = rawText.replace(BIDI_CONTROL_REGEX, "")
        val matcher = URL_PATTERN.matcher(sanitizedText)
        val results = mutableListOf<SanitizedUrl>()

        while (matcher.find()) {
            val candidate = matcher.group()
            if (candidate.length <= MAX_URL_LENGTH) {
                sanitize(candidate)?.let { results.add(it) }
            }
        }
        return results
    }

    /**
     * Extracts and validates the first valid HTTP/HTTPS URL from raw terminal text or word.
     */
    fun extractFirstUrl(rawText: String): SanitizedUrl? {
        return extractValidUrls(rawText).firstOrNull() ?: sanitize(rawText)
    }

    /**
     * Sanitizes and validates a candidate URL string.
     * Returns a [SanitizedUrl] if valid and safe, or null if invalid or hostile.
     */
    fun sanitize(candidate: String): SanitizedUrl? {
        var cleaned = candidate.replace(BIDI_CONTROL_REGEX, "").trim()
        if (cleaned.length > MAX_URL_LENGTH || cleaned.isEmpty()) return null

        // Strip trailing punctuation often adjacent to URLs in terminal output
        while (cleaned.isNotEmpty() && cleaned.last() in TRAILING_PUNCTUATION) {
            cleaned = cleaned.dropLast(1)
        }
        if (cleaned.isEmpty()) return null

        // Dual parser validation: both Android Uri and Java URI must agree
        val androidUri = try {
            Uri.parse(cleaned)
        } catch (_: Exception) {
            return null
        }

        val javaUri = try {
            URI(cleaned)
        } catch (_: Exception) {
            return null
        }

        // 1. Strict scheme allowlist: http and https only
        val scheme = androidUri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (javaUri.scheme?.lowercase() != scheme) return null

        // 2. Reject userinfo and authority obfuscation
        if (!androidUri.userInfo.isNullOrEmpty() || javaUri.userInfo != null) return null
        val authority = androidUri.authority ?: return null
        if (authority.contains("@") || cleaned.substringBefore("/").contains("@")) return null

        // 3. Host validation
        val host = androidUri.host ?: return null
        if (host.isBlank() || host.contains(" ") || host.contains("/") || host.contains("\\")) return null

        // 4. Port validation
        val port = androidUri.port

        // 5. Homoglyph & Punycode detection
        val asciiHost = try {
            IDN.toASCII(host)
        } catch (_: Exception) {
            return null
        }

        val unicodeHost = try {
            IDN.toUnicode(host)
        } catch (_: Exception) {
            return null
        }

        val isPunycode = asciiHost.startsWith("xn--", ignoreCase = true) ||
            asciiHost.contains(".xn--", ignoreCase = true)

        val isDirectIp = IPV4_PATTERN.matcher(host).matches() || host.startsWith("[")

        return SanitizedUrl(
            rawUrl = cleaned,
            uri = androidUri,
            scheme = scheme,
            host = host,
            port = port,
            isSecure = scheme == "https",
            isDirectIp = isDirectIp,
            isPunycode = isPunycode,
            punycodeHost = asciiHost,
            unicodeHost = unicodeHost
        )
    }
}
