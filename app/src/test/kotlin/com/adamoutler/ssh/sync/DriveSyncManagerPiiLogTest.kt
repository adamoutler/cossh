package com.adamoutler.ssh.sync

import android.app.Activity
import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DriveSyncManagerPiiLogTest {

    @Test
    fun testDriveSyncManagerSource_DoesNotLogPIIOrEmail() {
        val possiblePaths = listOf(
            File("src/main/kotlin/com/adamoutler/ssh/sync/DriveSyncManager.kt"),
            File("app/src/main/kotlin/com/adamoutler/ssh/sync/DriveSyncManager.kt"),
        )
        val sourceFile = possiblePaths.firstOrNull { it.exists() }
        assertTrue("DriveSyncManager.kt source file should exist", sourceFile != null && sourceFile.exists())
        checkNotNull(sourceFile)

        val content = sourceFile.readText()

        // PII assertion 1: Must not log "Auth successful for:" with account ID / email
        assertFalse(
            "DriveSyncManager must not contain 'Auth successful for:' PII logging",
            content.contains("Auth successful for:"),
        )

        // PII assertion 2: Must not reference credential id in log format
        assertFalse(
            "DriveSyncManager must not interpolate googleIdTokenCredential.id",
            content.contains("googleIdTokenCredential.id"),
        )

        // PII assertion 3: Verify sanitized logging exists
        assertTrue(
            "DriveSyncManager must log sanitized 'DriveSyncManager: Auth successful'",
            content.contains("DriveSyncManager: Auth successful"),
        )
    }

    @Test
    fun testDriveSyncManager_StdoutNeverEmitsPIIOrEmail() {
        val originalOut = System.out
        val baos = ByteArrayOutputStream()
        try {
            System.setOut(PrintStream(baos))

            // Invoke handleAuthorizationResult with cancelled/invalid intents to trigger log paths
            DriveSyncManager.handleAuthorizationResult(
                requestCode = 1001,
                resultCode = Activity.RESULT_CANCELED,
                data = null,
            )

            DriveSyncManager.handleAuthorizationResult(
                requestCode = 1001,
                resultCode = Activity.RESULT_OK,
                data = Intent(),
            )

            val capturedLogs = baos.toString()

            // Verify no PII string exists
            assertFalse(
                "Captured stdout logs must not contain 'Auth successful for:'",
                capturedLogs.contains("Auth successful for:"),
            )

            // Verify no email address pattern exists in captured logs
            val emailRegex = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
            assertFalse(
                "Captured stdout logs must not contain email addresses",
                emailRegex.containsMatchIn(capturedLogs),
            )
        } finally {
            System.setOut(originalOut)
        }
    }
}
