package com.adamoutler.ssh.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecureCrashHandlerRedactionTest {

    private lateinit var context: Context
    private lateinit var crashHandler: SecureCrashHandler
    private lateinit var crashDir: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        crashHandler = SecureCrashHandler(context, null)
        crashDir = File(context.filesDir, SecureCrashHandler.CRASH_DIR_NAME)
        if (crashDir.exists()) {
            crashDir.listFiles()?.forEach { it.delete() }
        } else {
            crashDir.mkdirs()
        }
    }

    @Test
    fun testRedactString_redactsBearerAndOAuthTokens() {
        val input = "Authorization header contains Bearer ya29.a0AfH6SMB90123456789abcdefghijklmnopqrstuvwxyz and OAuth 9876543210abcdef_token_val"
        val redacted = crashHandler.redactString(input)

        assertFalse("Raw Bearer token must not be present", redacted.contains("ya29.a0AfH6SMB90123456789abcdefghijklmnopqrstuvwxyz"))
        assertFalse("Raw OAuth token must not be present", redacted.contains("9876543210abcdef_token_val"))
        assertTrue("Redacted token marker must be present", redacted.contains("[REDACTED_TOKEN]"))
    }

    @Test
    fun testRedactString_redactsAccessTokenAssignments() {
        val input = "Connection error: access_token=secret_oauth_token_val; refresh_token: secret_refresh_val"
        val redacted = crashHandler.redactString(input)

        assertFalse("access_token value must be redacted", redacted.contains("secret_oauth_token_val"))
        assertFalse("refresh_token value must be redacted", redacted.contains("secret_refresh_val"))
        assertTrue("Token placeholder must be present", redacted.contains("[REDACTED_TOKEN]"))
    }

    @Test
    fun testRedactString_redactsPasswordAssignments() {
        val input = "Failed to login: password=MySecretP@ssword123, passwd: AdminPass456, pwd=rootSecret!"
        val redacted = crashHandler.redactString(input)

        assertFalse("password assignment must be redacted", redacted.contains("MySecretP@ssword123"))
        assertFalse("passwd assignment must be redacted", redacted.contains("AdminPass456"))
        assertFalse("pwd assignment must be redacted", redacted.contains("rootSecret!"))
        assertTrue("Password placeholder must be present", redacted.contains("[REDACTED_PASSWORD]"))
    }

    @Test
    fun testRedactString_redactsIpsKeysAndBase64() {
        val input = "Host 192.168.1.55 presented key: -----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA0fakekeyfakekey\n-----END PUBLIC KEY----- with b64 aGVsbG8gd29ybGQgdGhpcyBpcyBhIHZlcnkgbG9uZyBiYXNlNjQgc3RyaW5nIHRlc3Q="
        val redacted = crashHandler.redactString(input)

        assertFalse("IP address must be redacted", redacted.contains("192.168.1.55"))
        assertTrue("IP placeholder must be present", redacted.contains("[REDACTED_IP]"))
        assertFalse("PEM key must be redacted", redacted.contains("BEGIN PUBLIC KEY"))
        assertTrue("PEM key placeholder must be present", redacted.contains("[REDACTED_KEY]"))
    }

    @Test
    fun testPruneOldCrashLogs_retainsMax5Files() {
        // Create 8 dummy crash files with sequential timestamps
        for (i in 1..8) {
            val file = File(crashDir, "crash_20261020_08000$i.log")
            file.writeText("Crash trace $i")
            file.setLastModified(1000L * i)
        }

        assertEquals(8, crashDir.listFiles()?.size)

        // Prune
        crashHandler.pruneOldCrashLogs(crashDir)

        val remainingFiles = crashDir.listFiles()?.toList() ?: emptyList()
        assertEquals("Only MAX_CRASH_LOGS (5) files must be retained", 5, remainingFiles.size)

        // The 3 oldest files (i = 1, 2, 3) must have been deleted
        assertFalse("Oldest crash file 1 must be deleted", File(crashDir, "crash_20261020_080001.log").exists())
        assertFalse("Oldest crash file 2 must be deleted", File(crashDir, "crash_20261020_080002.log").exists())
        assertFalse("Oldest crash file 3 must be deleted", File(crashDir, "crash_20261020_080003.log").exists())

        // The 5 newest files (i = 4, 5, 6, 7, 8) must remain
        assertTrue("Newest crash file 8 must exist", File(crashDir, "crash_20261020_080008.log").exists())
        assertTrue("Newest crash file 7 must exist", File(crashDir, "crash_20261020_080007.log").exists())
        assertTrue("Newest crash file 6 must exist", File(crashDir, "crash_20261020_080006.log").exists())
    }

    @Test
    fun testSanitizeThrowable_sensitiveExceptionsRedactedCompletely() {
        val cryptoException = java.security.GeneralSecurityException("Secret key generation failed with password=foo")
        val sanitized = crashHandler.sanitizeThrowable(cryptoException)

        assertTrue("Exception message must be fully redacted", sanitized.contains("[REDACTED_EXCEPTION_MESSAGE]"))
        assertFalse("Leaked password must not be present", sanitized.contains("foo"))
        assertTrue("Exception class name must be preserved", sanitized.contains("GeneralSecurityException"))
    }
}
