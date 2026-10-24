package com.adamoutler.ssh.security.audit

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SecurityAuditLogManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var logFile: File
    private lateinit var logger: SecurityAuditLogger
    private lateinit var testPrefs: SharedPreferences
    private lateinit var manager: SecurityAuditLogManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        logFile = tempFolder.newFile("audit_chain_test.jsonl")
        logger = SecurityAuditLogger(logFile)
        testPrefs = context.getSharedPreferences("test_audit_anchors", Context.MODE_PRIVATE)
        testPrefs.edit().clear().commit()
        manager = SecurityAuditLogManager(
            context = context,
            logger = logger,
            injectedPrefs = testPrefs,
        )
    }

    @Test
    fun testLogSessionLifecycle() = runBlocking {
        val startRecord = manager.logSessionStart(
            profileId = "prof-1",
            host = "192.168.1.50",
            port = 22,
            username = "root",
        )
        assertEquals(AuditEventType.SESSION_CONNECTED, startRecord.eventType)
        assertEquals("192.168.1.50", startRecord.payload["host"])

        val endRecord = manager.logSessionEnd(
            profileId = "prof-1",
            host = "192.168.1.50",
            reason = "User logged out",
        )
        assertEquals(AuditEventType.SESSION_TERMINATED, endRecord.eventType)
        assertEquals(2L, endRecord.sequenceNumber)

        val integrity = manager.verifyIntegrity()
        assertTrue(integrity is AuditIntegrityResult.Valid)
        val valid = integrity as AuditIntegrityResult.Valid
        assertEquals(2L, valid.totalEntries)
        assertEquals(endRecord.entryHash, valid.headHash)
    }

    @Test
    fun testLogAuthResultSuccessAndFailure() = runBlocking {
        val successRecord = manager.logAuthResult(
            profileId = "prof-2",
            host = "remote.host.com",
            username = "dev",
            authType = "PASSWORD",
            success = true,
        )
        assertEquals(AuditEventType.SESSION_CONNECTED, successRecord.eventType)
        assertEquals("true", successRecord.payload["auth_success"])

        val failRecord = manager.logAuthResult(
            profileId = "prof-2",
            host = "remote.host.com",
            username = "dev",
            authType = "PASSWORD",
            success = false,
            failureReason = "Bad credentials",
        )
        assertEquals(AuditEventType.AUTH_FAILURE, failRecord.eventType)
        assertEquals("false", failRecord.payload["auth_success"])
        assertEquals("Bad credentials", failRecord.payload["failure_reason"])

        val records = manager.getRecords()
        assertEquals(2, records.size)
    }

    @Test
    fun testLogHostKeyVerification() = runBlocking {
        val acceptRecord = manager.logHostKeyVerification(
            host = "server.local",
            port = 2222,
            keyType = "ssh-ed25519",
            fingerprint = "SHA256:abc123xyz",
            accepted = true,
            isKeyChanged = false,
        )
        assertEquals(AuditEventType.HOST_KEY_VERIFIED, acceptRecord.eventType)
        assertEquals("true", acceptRecord.payload["accepted"])

        val rejectRecord = manager.logHostKeyVerification(
            host = "server.local",
            port = 2222,
            keyType = "ssh-ed25519",
            fingerprint = "SHA256:mismatch",
            accepted = false,
            isKeyChanged = true,
        )
        assertEquals(AuditEventType.HOST_KEY_REJECTED, rejectRecord.eventType)
        assertEquals("false", rejectRecord.payload["accepted"])
    }

    @Test
    fun testLogKeyOperationAndAppLock() = runBlocking {
        val genRecord = manager.logKeyOperation(
            operation = "GENERATE_KEYPAIR",
            keyAlias = "id_ed25519_primary",
            keyType = "ED25519",
        )
        assertEquals(AuditEventType.KEY_PAIR_GENERATED, genRecord.eventType)

        val appLockRecord = manager.logAppLockEvent(
            eventType = AuditEventType.APP_LOCK_CONFIGURED,
            detail = "Biometrics enabled",
        )
        assertEquals(AuditEventType.APP_LOCK_CONFIGURED, appLockRecord.eventType)
        assertEquals("Biometrics enabled", appLockRecord.payload["detail"])
    }

    @Test
    fun testExportLogAsJsonLd() = runBlocking {
        manager.logSessionStart("p1", "h1", 22, "u1")
        manager.logAppLockEvent(AuditEventType.APP_LOCK_UNLOCKED)

        val jsonLd = manager.exportLogAsJsonLd()
        assertTrue(jsonLd.contains("https://schema.org"))
        assertTrue(jsonLd.contains("ItemList"))
        assertTrue(jsonLd.contains("CoSSH Security Audit Log"))
        assertTrue(jsonLd.contains("SESSION_CONNECTED"))
        assertTrue(jsonLd.contains("APP_LOCK_UNLOCKED"))
    }
}
