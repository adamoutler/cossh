package com.adamoutler.ssh.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TerminalUrlSanitizerTest {

    @Test
    fun testValidHttpsUrl() {
        val result = TerminalUrlSanitizer.sanitize("https://github.com/adamoutler/cossh")
        assertNotNull(result)
        assertEquals("https", result?.scheme)
        assertEquals("github.com", result?.host)
        assertTrue(result?.isSecure == true)
        assertFalse(result?.isDirectIp == true)
        assertFalse(result?.isPunycode == true)
    }

    @Test
    fun testStripTrailingPunctuation() {
        val inputs = listOf(
            "https://example.com/api/v1).",
            "(https://example.com/api/v1)",
            "https://example.com/api/v1,",
            "'https://example.com/api/v1'",
            "\"https://example.com/api/v1\":",
            "<https://example.com/api/v1>"
        )

        for (input in inputs) {
            val urls = TerminalUrlSanitizer.extractValidUrls(input)
            assertEquals(1, urls.size)
            assertEquals("https://example.com/api/v1", urls[0].rawUrl)
        }
    }

    @Test
    fun testRejectHostileSchemes() {
        val hostileUrls = listOf(
            "file:///data/data/com.adamoutler.cobaltssh/databases/keys.db",
            "content://com.android.providers.downloads/1",
            "intent:#Intent;action=android.intent.action.VIEW;end",
            "javascript:alert(document.cookie)",
            "data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==",
            "tel:+1234567890",
            "mailto:admin@example.com"
        )

        for (hostile in hostileUrls) {
            assertNull("Should reject scheme: $hostile", TerminalUrlSanitizer.sanitize(hostile))
        }
    }

    @Test
    fun testRejectUserinfoInjection() {
        val attackUrls = listOf(
            "https://trusted.bank.com@attacker-site.com/login",
            "https://admin:password@192.168.1.1:8080/shell",
            "https://user@legit.org/page"
        )

        for (attack in attackUrls) {
            assertNull("Should reject userinfo injection: $attack", TerminalUrlSanitizer.sanitize(attack))
        }
    }

    @Test
    fun testStripBidiOverrides() {
        // Embed Left-to-Right override (\u202D) and Right-to-Left override (\u202E)
        val bidiInjected = "https://\u202Egro.tige\u202C.com/download"
        val result = TerminalUrlSanitizer.sanitize(bidiInjected)
        assertNotNull(result)
        assertFalse(result?.rawUrl?.contains("\u202E") == true)
    }

    @Test
    fun testDetectIdnPunycode() {
        val punycodeUrl = "https://xn--e1afmkfd.org/login"
        val result = TerminalUrlSanitizer.sanitize(punycodeUrl)
        assertNotNull(result)
        assertTrue(result?.isPunycode == true)
        assertEquals("xn--e1afmkfd.org", result?.punycodeHost)
    }

    @Test
    fun testDetectDirectIpAddress() {
        val ipUrl = "http://192.168.1.100:8080/dashboard"
        val result = TerminalUrlSanitizer.sanitize(ipUrl)
        assertNotNull(result)
        assertTrue(result?.isDirectIp == true)
        assertFalse(result?.isSecure == true)
        assertEquals(8080, result?.port)
    }

    @Test
    fun testMaxLengthBoundsCheck() {
        val excessiveUrl = "https://example.com/" + "a".repeat(2050)
        assertNull(TerminalUrlSanitizer.sanitize(excessiveUrl))
    }
}
