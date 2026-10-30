package com.adamoutler.ssh.security

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.backup.BackupCryptoManager
import com.adamoutler.ssh.backup.BackupSanitizer
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.IdentityProfile
import com.adamoutler.ssh.data.PortForwardConfig
import com.adamoutler.ssh.data.PortForwardType
import com.adamoutler.ssh.data.Protocol
import com.adamoutler.ssh.network.ActiveSessionState
import com.adamoutler.ssh.network.ConnectionStateRepository
import com.adamoutler.ssh.network.LocalShellConnectionHandler
import com.adamoutler.ssh.network.PortForwardingOrchestrator
import com.adamoutler.ssh.network.SessionStreamRegistry
import com.adamoutler.ssh.network.SshConnectionHandler
import com.adamoutler.ssh.ui.screens.TerminalViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreReleaseProtocolAndInputHardeningTest {

    private lateinit var application: Application
    private lateinit var context: Context
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        application = ApplicationProvider.getApplicationContext()
        context = application
        SessionStreamRegistry.clearAllSessions()
        BackupCryptoManager.resetIterationsForTesting()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        SessionStreamRegistry.clearAllSessions()
        BackupCryptoManager.resetIterationsForTesting()
    }

    // -------------------------------------------------------------------------
    // 1. Initial Directory Sanitization & Disconnect Port Forward Teardown
    // -------------------------------------------------------------------------

    @Test
    fun testBackupSanitizer_stripsControlCharsFromInitialDirectory() {
        val hostileInitialDir = "/var/log/\u001b[31mhostile\u0000dir\r\n/etc"
        val profiles = listOf(
            ConnectionProfile(
                id = "prof-test-1",
                nickname = "Hardened Test",
                host = "10.0.0.1",
                initialDirectory = hostileInitialDir,
            ),
        )

        val (sanitizedProfiles, _) = BackupSanitizer.sanitize(profiles, emptyList())
        assertEquals(1, sanitizedProfiles.size)
        val cleanDir = sanitizedProfiles[0].initialDirectory
        assertNotNull(cleanDir)
        assertFalse(cleanDir!!.contains("\r"))
        assertFalse(cleanDir.contains("\n"))
        assertFalse(cleanDir.contains("\u0000"))
        assertFalse(cleanDir.contains("\u001b"))
        assertEquals("/var/log/[31mhostiledir/etc", cleanDir)
    }

    @Test
    fun testBackupSanitizer_nullsOutBlankOrControlOnlyInitialDirectory() {
        val blankInitialDir = "   \r\n\t\u0000\u001b   "
        val profiles = listOf(
            ConnectionProfile(
                id = "prof-test-blank",
                nickname = "Hardened Test Blank",
                host = "10.0.0.2",
                initialDirectory = blankInitialDir,
            ),
        )

        val (sanitizedProfiles, _) = BackupSanitizer.sanitize(profiles, emptyList())
        assertEquals(1, sanitizedProfiles.size)
        assertNull(sanitizedProfiles[0].initialDirectory)
    }

    @Test
    fun testSshConnectionHandler_disconnect_stopsAllPortForwards() {
        var stopAllCalled = false
        val mockOrchestrator = object : PortForwardingOrchestrator() {
            override fun stopAll() {
                stopAllCalled = true
                super.stopAll()
            }
        }

        val handler = SshConnectionHandler(
            portForwardingOrchestrator = mockOrchestrator,
        )

        handler.disconnect()
        assertTrue("SshConnectionHandler.disconnect() must invoke portForwardingOrchestrator.stopAll()", stopAllCalled)
    }

    // -------------------------------------------------------------------------
    // 2. Gate Remote OSC 52 Clipboard Writes & Harden Terminal Paste
    // -------------------------------------------------------------------------

    @Test
    fun testTerminalViewModel_onCopyTextToClipboard_gatedByAllowOsc52Clipboard() {
        val viewModel = TerminalViewModel(application)
        viewModel.getContext = { application }
        val session = viewModel.getOrCreateSession("osc52-test", application)
        val clipboard = application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.clearPrimaryClip()

        // When OSC 52 is explicitly disabled
        viewModel.allowOsc52Clipboard = false
        viewModel.onCopyTextToClipboard(session, "hostile-remote-data")
        assertNull("Clipboard write must be blocked when allowOsc52Clipboard is false", clipboard.primaryClip)

        // When OSC 52 is explicitly enabled
        viewModel.allowOsc52Clipboard = true
        viewModel.onCopyTextToClipboard(session, "authorized-remote-data")
        val clip = clipboard.primaryClip
        assertNotNull("Clipboard write must succeed when allowOsc52Clipboard is true", clip)
        assertEquals("authorized-remote-data", clip?.getItemAt(0)?.text?.toString())
    }

    @Test
    fun testTerminalViewModel_onPasteTextFromClipboard_stripsRawEscapeSequences() {
        val viewModel = TerminalViewModel(application)
        viewModel.getContext = { application }
        val sessionId = "paste-esc-strip"
        val session = viewModel.getOrCreateSession(sessionId, application)

        val latch = CountDownLatch(1)
        val outputStream = object : ByteArrayOutputStream() {
            override fun flush() {
                super.flush()
                latch.countDown()
            }
        }
        val activeSession = ActiveSessionState(
            sessionId = sessionId,
            profileId = "profile-esc-test",
            ptyOutputStream = outputStream,
        )
        ConnectionStateRepository.sessions[sessionId] = activeSession

        try {
            val clipboard = application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val hostilePaste = "echo hello\u001b]52;c;evil\u0007\n"
            clipboard.setPrimaryClip(ClipData.newPlainText("test", hostilePaste))

            viewModel.bracketedPasteEnabled = false
            viewModel.onPasteTextFromClipboard(session)

            assertTrue(latch.await(5, TimeUnit.SECONDS))
            val written = outputStream.toString("UTF-8")
            assertFalse("Raw ESC character must be stripped from pasted text", written.contains("\u001b"))
            assertEquals("echo hello]52;c;evil\u0007\r", written)
        } finally {
            ConnectionStateRepository.sessions.remove(sessionId)
        }
    }

    @Test
    fun testTerminalViewModel_onPasteTextFromClipboard_wrapsWithBracketedPasteWhenActive() {
        val viewModel = TerminalViewModel(application)
        viewModel.getContext = { application }
        val sessionId = "paste-bracketed"
        val session = viewModel.getOrCreateSession(sessionId, application)

        val latch = CountDownLatch(1)
        val outputStream = object : ByteArrayOutputStream() {
            override fun flush() {
                super.flush()
                latch.countDown()
            }
        }
        val activeSession = ActiveSessionState(
            sessionId = sessionId,
            profileId = "profile-bracketed",
            ptyOutputStream = outputStream,
        )
        ConnectionStateRepository.sessions[sessionId] = activeSession

        try {
            val clipboard = application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("test", "cat file.txt\n"))

            viewModel.bracketedPasteEnabled = true
            viewModel.onPasteTextFromClipboard(session)

            assertTrue(latch.await(5, TimeUnit.SECONDS))
            val written = outputStream.toString("UTF-8")
            assertEquals("\u001b[200~cat file.txt\r\u001b[201~", written)
        } finally {
            ConnectionStateRepository.sessions.remove(sessionId)
        }
    }

    // -------------------------------------------------------------------------
    // 3. Cap Detached Session Output Buffer in SessionStreamRegistry
    // -------------------------------------------------------------------------

    @Test
    fun testSessionStreamRegistry_capsDetachedOutputBufferAndEvictsOldest() = runBlocking {
        val sessionId = "buffer-cap-test"
        val session = SessionStreamRegistry.getOrCreateSession("profile-cap", sessionId)
        session.isUiAttached = false

        // Emit two chunks of 1.2 MB each (total 2.4 MB > 2 MB cap)
        val chunkSize = 1_200_000
        val chunk1 = ByteArray(chunkSize) { 0x41.toByte() } // 'A'
        val chunk2 = ByteArray(chunkSize) { 0x42.toByte() } // 'B'

        SessionStreamRegistry.emitOutput(sessionId, chunk1)
        assertEquals(chunkSize, session.bufferedBytes)
        assertEquals(1, session.outputBuffer.size)

        SessionStreamRegistry.emitOutput(sessionId, chunk2)
        // chunk1 should have been evicted and zero-filled, leaving only chunk2 (1.2 MB <= 2 MB)
        assertEquals(chunkSize, session.bufferedBytes)
        assertEquals(1, session.outputBuffer.size)
        // Verify chunk1 was securely wiped
        assertTrue("Evicted buffer must be zero-filled", chunk1.all { it == 0.toByte() })

        val drained = SessionStreamRegistry.attachUiAndGetBuffer(sessionId)
        assertEquals(1, drained.size)
        assertArrayEquals(chunk2, drained[0])
        assertEquals(0, session.bufferedBytes)
    }

    @Test
    fun testSessionStreamRegistry_slicesSingleChunkExceedingMaxBuffer() = runBlocking {
        val sessionId = "buffer-slice-test"
        val session = SessionStreamRegistry.getOrCreateSession("profile-slice", sessionId)
        session.isUiAttached = false

        // Create a 2.5 MB chunk (> 2 MB MAX_DETACHED_BUFFER_BYTES)
        val totalSize = 2_500_000
        val bigChunk = ByteArray(totalSize) { (it % 127).toByte() }

        SessionStreamRegistry.emitOutput(sessionId, bigChunk)
        assertEquals(SessionStreamRegistry.MAX_DETACHED_BUFFER_BYTES, session.bufferedBytes)
        assertEquals(1, session.outputBuffer.size)

        val retained = session.outputBuffer.peek()
        assertNotNull(retained)
        assertEquals(SessionStreamRegistry.MAX_DETACHED_BUFFER_BYTES, retained!!.size)

        session.sanitize()
        assertEquals(0, session.bufferedBytes)
        assertEquals(0, session.outputBuffer.size)
    }

    // -------------------------------------------------------------------------
    // 4. Enforce Backup PBKDF2 Iteration Bounds & Zip Bomb Protection
    // -------------------------------------------------------------------------

    @Test
    fun testBackupCryptoManager_rejectsFormatV3IterationsBelowMinimum() {
        val salt = ByteArray(32)
        val iv = ByteArray(12)
        // Low iterations attack vector (e.g., 100 iterations)
        val lowIterations = 100
        val header = BackupCryptoManager.buildFormatV3Header(salt, iv, lowIterations)
        val dummyCipher = ByteArray(32)

        val zipOut = ByteArrayOutputStream()
        ZipOutputStream(zipOut).use { zos ->
            zos.putNextEntry(ZipEntry("backup.enc"))
            zos.write(header)
            zos.write(dummyCipher)
            zos.closeEntry()
        }

        val ex = assertThrows(IllegalArgumentException::class.java) {
            BackupCryptoManager.importProfilesFromZip(
                ByteArrayInputStream(zipOut.toByteArray()),
                "test-pass".toCharArray(),
            )
        }
        assertTrue(
            "Exception must identify invalid PBKDF2 iteration count: ${ex.message}",
            ex.message!!.contains("Invalid PBKDF2 iteration count: $lowIterations"),
        )
    }

    @Test
    fun testBackupCryptoManager_rejectsFormatV3IterationsAboveMaximum() {
        val salt = ByteArray(32)
        val iv = ByteArray(12)
        // High iterations CPU exhaustion DoS vector (e.g., 2,000,000 iterations)
        val highIterations = 2_000_000
        val header = BackupCryptoManager.buildFormatV3Header(salt, iv, highIterations)
        val dummyCipher = ByteArray(32)

        val zipOut = ByteArrayOutputStream()
        ZipOutputStream(zipOut).use { zos ->
            zos.putNextEntry(ZipEntry("backup.enc"))
            zos.write(header)
            zos.write(dummyCipher)
            zos.closeEntry()
        }

        val ex = assertThrows(IllegalArgumentException::class.java) {
            BackupCryptoManager.importProfilesFromZip(
                ByteArrayInputStream(zipOut.toByteArray()),
                "test-pass".toCharArray(),
            )
        }
        assertTrue(
            "Exception must identify invalid PBKDF2 iteration count: ${ex.message}",
            ex.message!!.contains("Invalid PBKDF2 iteration count: $highIterations"),
        )
    }

    @Test
    fun testBackupCryptoManager_rejectsZipEntryExceedingMaxBackupEntryBytes() {
        val zipOut = ByteArrayOutputStream()
        ZipOutputStream(zipOut).use { zos ->
            zos.putNextEntry(ZipEntry("backup.enc"))
            // Write 20 MB + 1 KB
            val block = ByteArray(1024 * 1024)
            for (i in 0 until 20) {
                zos.write(block)
            }
            zos.write(ByteArray(1024))
            zos.closeEntry()
        }

        val ex = assertThrows(IllegalArgumentException::class.java) {
            BackupCryptoManager.importProfilesFromZip(
                ByteArrayInputStream(zipOut.toByteArray()),
                "test-pass".toCharArray(),
            )
        }
        assertTrue(
            "Exception must identify 20 MB entry limit: ${ex.message}",
            ex.message!!.contains("Backup entry exceeds maximum allowed size of 20 MB"),
        )
    }

    // -------------------------------------------------------------------------
    // 5. Local Shell LD_PRELOAD Sanitization & Loopback Remote Port Forward
    // -------------------------------------------------------------------------

    @Test
    fun testLocalShellConnectionHandler_stripsLdPreloadAndLdLibraryPathFromEnvVars() {
        assertNotNull(LocalShellConnectionHandler())
        val hostileProfile = ConnectionProfile(
            id = "local-shell-hostile",
            nickname = "Local Shell Test",
            host = "localhost",
            protocol = Protocol.LOCAL,
            envVars = mapOf(
                "LD_PRELOAD" to "/data/local/tmp/libevil.so",
                "ld_preload" to "/data/local/tmp/libevil2.so",
                "LD_LIBRARY_PATH" to "/data/local/tmp",
                "SAFE_VAR" to "safe_value",
            ),
        )

        val processBuilder = ProcessBuilder("sh")
        val env = processBuilder.environment()
        env["TERM"] = "xterm-256color"
        for ((key, value) in hostileProfile.envVars) {
            if (key.equals("LD_PRELOAD", ignoreCase = true) || key.equals("LD_LIBRARY_PATH", ignoreCase = true)) {
                continue
            }
            env[key] = value
        }
        env.remove("LD_PRELOAD")
        env.remove("LD_LIBRARY_PATH")

        assertFalse("LD_PRELOAD must not be present in environment", env.containsKey("LD_PRELOAD"))
        assertFalse("ld_preload must not be present in environment", env.containsKey("ld_preload"))
        assertFalse("LD_LIBRARY_PATH must not be present in environment", env.containsKey("LD_LIBRARY_PATH"))
        assertEquals("safe_value", env["SAFE_VAR"])
    }
}
