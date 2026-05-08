package com.adamoutler.ssh.security

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.security.GeneralSecurityException

@RunWith(RobolectricTestRunner::class)
class SecureCrashHandlerTest {

    private lateinit var context: Context
    private lateinit var crashDir: File
    private var originalHandler: Thread.UncaughtExceptionHandler? = null
    private var defaultHandlerCalled = false
    private var processKillerCalled = false
    private lateinit var secureCrashHandler: SecureCrashHandler

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        crashDir = File(context.filesDir, SecureCrashHandler.CRASH_DIR_NAME)
        if (crashDir.exists()) {
            crashDir.deleteRecursively()
        }

        originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        defaultHandlerCalled = false
        processKillerCalled = false

        val dummyHandler = Thread.UncaughtExceptionHandler { _, _ -> defaultHandlerCalled = true }

        secureCrashHandler = SecureCrashHandler(context, dummyHandler).apply {
            processKiller = { processKillerCalled = true }
        }
    }

    @After
    fun teardown() {
        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
        if (crashDir.exists()) {
            crashDir.deleteRecursively()
        }
    }

    @Test
    fun `test uncaughtException sanitizes and writes log`() {
        val thread = Thread.currentThread()
        val sensitiveException = Exception("Failed with IP 192.168.1.100 and base64: AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
        
        secureCrashHandler.uncaughtException(thread, sensitiveException)

        // Verify killer was called
        assertTrue("Process killer should have been called", processKillerCalled)

        // Verify a file was written
        val files = crashDir.listFiles()
        assertTrue("Crash directory should exist and contain a file", files != null && files.isNotEmpty())

        val logContent = files!![0].readText()
        assertTrue("Log should contain sanitized IP", logContent.contains("[REDACTED_IP]"))
        assertTrue("Log should contain sanitized Base64", logContent.contains("[REDACTED_B64]"))
        assertFalse("Log should NOT contain the actual IP", logContent.contains("192.168.1.100"))
        assertFalse("Log should NOT contain the actual base64", logContent.contains("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="))
    }

    @Test
    fun `test isSensitiveException completely redacts message`() {
        val thread = Thread.currentThread()
        val securityException = GeneralSecurityException("My super secret key failure: 123456")
        
        secureCrashHandler.uncaughtException(thread, securityException)

        // Verify killer was called
        assertTrue("Process killer should have been called", processKillerCalled)

        val files = crashDir.listFiles()
        assertTrue("Crash directory should exist and contain a file", files != null && files.isNotEmpty())

        val logContent = files!![0].readText()
        assertTrue("Log should contain REDACTED_EXCEPTION_MESSAGE", logContent.contains("[REDACTED_EXCEPTION_MESSAGE]"))
        assertFalse("Log should NOT contain the secret", logContent.contains("123456"))
    }

    @Test
    fun `test recursive cause redaction`() {
        val thread = Thread.currentThread()
        val cause = GeneralSecurityException("Secret Crypto Key")
        val wrapper = RuntimeException("Wrapper exception", cause)
        
        secureCrashHandler.uncaughtException(thread, wrapper)

        val files = crashDir.listFiles()
        val logContent = files!![0].readText()
        
        // The cause is a GeneralSecurityException, so its message should be [REDACTED]
        assertTrue("Cause should be redacted", logContent.contains("Message: [REDACTED]"))
        assertFalse("Log should NOT contain the secret", logContent.contains("Secret Crypto Key"))
    }
    
    @Test
    fun `test PEM redaction`() {
        val thread = Thread.currentThread()
        val pemString = "-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDE\n-----END PRIVATE KEY-----"
        val exception = Exception("Failed loading: $pemString")
        
        secureCrashHandler.uncaughtException(thread, exception)

        val files = crashDir.listFiles()
        val logContent = files!![0].readText()
        
        assertTrue("PEM block should be redacted", logContent.contains("[REDACTED_KEY]"))
        assertFalse("Log should NOT contain the private key", logContent.contains("MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDE"))
    }
    
    @Test
    fun `test fallback if crash handler throws exception`() {
        // We can force the crash handler to throw an exception by giving it a bad context
        val badContext = RuntimeEnvironment.getApplication().applicationContext // Normally ok, let's break it using reflection or null
        // Easier: simulate failure writing by making directory read-only? No, better to mock or pass null. 
        // Actually, let's just make the crash directory a file so mkdirs fails
        crashDir.parentFile?.mkdirs()
        crashDir.createNewFile() // Now crashDir is a file, so it can't be a directory
        
        val thread = Thread.currentThread()
        val exception = Exception("Test")
        
        secureCrashHandler.uncaughtException(thread, exception)
        
        // Verify process killer still called
        assertTrue("Process killer should have been called even on failure", processKillerCalled)
    }
}
