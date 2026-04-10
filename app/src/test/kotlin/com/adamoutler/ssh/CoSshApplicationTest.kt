package com.adamoutler.ssh

import com.adamoutler.ssh.security.SecureCrashHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowProcess
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoSshApplicationTest {

    @Test
    fun testSecureCrashHandlerIsRegistered() {
        val app = RuntimeEnvironment.getApplication() as CoSshApplication
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        assertTrue("SecureCrashHandler should be registered as default uncaught exception handler", defaultHandler is SecureCrashHandler)
    }

    @Test
    fun testSecureCrashHandlerRedactsAndWritesToDisk() {
        val app = RuntimeEnvironment.getApplication() as CoSshApplication
        val crashHandler = SecureCrashHandler(app, null)
        var processKilled = false
        crashHandler.processKiller = { processKilled = true }
        
        val fakeException = java.security.GeneralSecurityException("Failed to load key: -----BEGIN PRIVATE KEY----- fakekey -----END PRIVATE KEY----- for 192.168.1.1")
        
        try {
            crashHandler.uncaughtException(Thread.currentThread(), fakeException)
        } catch (e: Exception) {
            // Test exception
        }

        assertTrue("Process killer should be invoked", processKilled)

        val crashDir = File(app.filesDir, SecureCrashHandler.CRASH_DIR_NAME)
        assertTrue(crashDir.exists())
        
        val files = crashDir.listFiles()
        assertTrue("Crash file should be created", files != null && files.isNotEmpty())
        
        val crashFile = files!!.first()
        val content = crashFile.readText()
        
        assertTrue("Exception type should be present", content.contains("java.security.GeneralSecurityException"))
        assertTrue("Sensitive exception messages should be completely redacted", content.contains("[REDACTED_EXCEPTION_MESSAGE]"))
        assertTrue("Original key and IP should NOT be present", !content.contains("192.168.1.1"))
    }
}
