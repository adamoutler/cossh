package com.adamoutler.ssh.ui.screens

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Looper
import android.os.NetworkOnMainThreadException
import android.os.StrictMode
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.crypto.SettingsManager
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.network.ActiveSessionState
import com.adamoutler.ssh.network.ConnectionStateRepository
import com.termux.terminal.TerminalSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class TerminalViewModelTest {

    private lateinit var application: Application
    private lateinit var viewModel: TerminalViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        application = ApplicationProvider.getApplicationContext()
        viewModel = TerminalViewModel(application)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test initFontSize uses profile size`() {
        val storageManager = SecurityStorageManager(application)
        val profile = ConnectionProfile(id = "test_profile", nickname = "Test", host = "host", fontSize = 24)
        storageManager.saveProfile(profile)

        viewModel.initFontSize("test_profile")
        assertEquals(24, viewModel.fontSizeFlow.value)
    }

    @Test
    fun `test initFontSize uses global size when profile size is null`() {
        val storageManager = SecurityStorageManager(application)
        val profile = ConnectionProfile(id = "test_profile", nickname = "Test", host = "host", fontSize = null)
        storageManager.saveProfile(profile)
        val settingsManager = SettingsManager(application)
        settingsManager.globalFontSize = 18

        viewModel.initFontSize("test_profile")
        assertEquals(18, viewModel.fontSizeFlow.value)
    }

    @Test
    fun `test updateFontSize coerces minimum and updates flow`() = runTest {
        viewModel.updateFontSize(30)
        assertEquals(30, viewModel.fontSizeFlow.value)

        viewModel.updateFontSize(2) // Should coerce to 4
        assertEquals(4, viewModel.fontSizeFlow.value)
    }

    @Test
    fun `test getOrCreateSession creates new session and stores it`() {
        val session = viewModel.getOrCreateSession("session-123", application)
        assertNotNull(session)

        val session2 = viewModel.getOrCreateSession("session-123", application)
        assertEquals(session, session2) // Should return same instance

        assertNotNull(TerminalViewModel.activeSessionsRef?.get("session-123"))
    }

    @Test
    fun `test onCopyTextToClipboard sets primary clip`() {
        viewModel.getContext = { application }
        val session = viewModel.getOrCreateSession("session-123", application)

        viewModel.onCopyTextToClipboard(session, "test clip text   ")

        val clipboard = application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        assertNotNull(clip)
        assertEquals("test clip text", clip?.getItemAt(0)?.text.toString())
    }

    @Test
    fun `test no-op methods do not crash`() {
        val session = TerminalSession("sh", "/", arrayOf(), arrayOf(), 0, viewModel)
        // Set some text to test the memory scrubbing reflection in onSessionFinished
        session.write("sensitive data")
        viewModel.onTextChanged(session)
        viewModel.onTitleChanged(session)
        viewModel.onSessionFinished(session)
        viewModel.onBell(session)
        viewModel.onColorsChanged(session)
        viewModel.onTerminalCursorStateChange(true)
        assertEquals(0, viewModel.getTerminalCursorStyle())
        viewModel.logError("tag", "msg")
        viewModel.logWarn("tag", "msg")
        viewModel.logInfo("tag", "msg")
        viewModel.logDebug("tag", "msg")
        viewModel.logVerbose("tag", "msg")
        viewModel.logStackTraceWithMessage("tag", "msg", Exception())
        viewModel.logStackTrace("tag", Exception())
    }

    @Test
    fun `test onPasteTextFromClipboard pastes text`() {
        viewModel.getContext = { application }
        val sessionId = "session-123"
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
            profileId = "test-profile",
            ptyOutputStream = outputStream,
        )
        ConnectionStateRepository.sessions[sessionId] = activeSession

        try {
            val clipboard = application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Terminal text", "pasted text\n")
            clipboard.setPrimaryClip(clip)

            viewModel.onPasteTextFromClipboard(session)

            assertTrue(latch.await(5, TimeUnit.SECONDS))
            assertEquals("pasted text\r", outputStream.toString("UTF-8"))
        } finally {
            ConnectionStateRepository.sessions.remove(sessionId)
        }
    }

    @Test
    fun `test onPasteTextFromClipboard with StrictMode thread detection executes without crashing`() {
        val oldPolicy = StrictMode.getThreadPolicy()
        try {
            // Enable StrictMode network detection on calling thread (Main Thread)
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectNetwork()
                    .penaltyDeath()
                    .build()
            )

            viewModel.getContext = { application }
            val sessionId = "strictmode-session"
            val session = viewModel.getOrCreateSession(sessionId, application)

            val latch = CountDownLatch(1)
            var writeExecutedOnBackground = false
            val threadAssertStream = object : ByteArrayOutputStream() {
                override fun write(b: ByteArray, off: Int, len: Int) {
                    if (Looper.myLooper() == Looper.getMainLooper()) {
                        throw NetworkOnMainThreadException()
                    }
                    writeExecutedOnBackground = true
                    super.write(b, off, len)
                }

                override fun flush() {
                    if (Looper.myLooper() == Looper.getMainLooper()) {
                        throw NetworkOnMainThreadException()
                    }
                    super.flush()
                    latch.countDown()
                }
            }

            val activeSession = ActiveSessionState(
                sessionId = sessionId,
                profileId = "profile-strict",
                ptyOutputStream = threadAssertStream,
            )
            ConnectionStateRepository.sessions[sessionId] = activeSession

            val clipboard = application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Terminal text", "echo 'strictmode safe'\n")
            clipboard.setPrimaryClip(clip)

            // Must be called on Main Thread - must not throw NetworkOnMainThreadException
            viewModel.onPasteTextFromClipboard(session)

            val completed = latch.await(5, TimeUnit.SECONDS)
            assertTrue("Socket write should complete asynchronously on Dispatchers.IO", completed)
            assertTrue("Write must have executed on a background thread, not main thread", writeExecutedOnBackground)
            assertEquals("echo 'strictmode safe'\r", threadAssertStream.toString("UTF-8"))
        } finally {
            StrictMode.setThreadPolicy(oldPolicy)
            ConnectionStateRepository.sessions.remove("strictmode-session")
        }
    }

    @Test
    fun `test onPasteTextFromClipboard safely coerces complex ClipData with newline replacement`() {
        viewModel.getContext = { application }
        val sessionId = "complex-clip-session"
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
            profileId = "profile-complex",
            ptyOutputStream = outputStream,
        )
        ConnectionStateRepository.sessions[sessionId] = activeSession

        try {
            val clipboard = application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

            // Complex ClipData: HTML text with mixed newlines (\r\n and \n)
            val clip = ClipData.newHtmlText(
                "html_clip",
                "first line\r\nsecond line\nthird line",
                "<p>first line</p><p>second line</p><p>third line</p>",
            )
            clipboard.setPrimaryClip(clip)

            viewModel.onPasteTextFromClipboard(session)

            val completed = latch.await(5, TimeUnit.SECONDS)
            assertTrue("Paste should write to output stream", completed)
            // Verify newlines are converted to \r for PTY execution
            val result = outputStream.toString("UTF-8")
            assertEquals("first line\rsecond line\rthird line", result)
        } finally {
            ConnectionStateRepository.sessions.remove(sessionId)
        }
    }

    @Test
    fun `test onPasteTextFromClipboard handles null and empty clip gracefully`() {
        viewModel.getContext = { application }
        val sessionId = "empty-clip-session"
        val session = viewModel.getOrCreateSession(sessionId, application)

        val outputStream = ByteArrayOutputStream()
        val activeSession = ActiveSessionState(
            sessionId = sessionId,
            profileId = "profile-empty",
            ptyOutputStream = outputStream,
        )
        ConnectionStateRepository.sessions[sessionId] = activeSession

        try {
            val clipboard = application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

            // Empty text clip
            clipboard.setPrimaryClip(ClipData.newPlainText("empty", ""))
            viewModel.onPasteTextFromClipboard(session)

            // Primary clip null: clearPrimaryClip on API 28+
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
                viewModel.onPasteTextFromClipboard(session)
            }

            // Verify nothing was written to the stream
            assertEquals(0, outputStream.size())
        } finally {
            ConnectionStateRepository.sessions.remove(sessionId)
        }
    }

    @Test
    fun `test onCleared clears sessions`() {
        viewModel.getContext = { application }
        viewModel.getOrCreateSession("session-123", application)

        val onClearedMethod = androidx.lifecycle.ViewModel::class.java.getDeclaredMethod("onCleared")
        onClearedMethod.isAccessible = true
        onClearedMethod.invoke(viewModel)

        assertEquals(null, viewModel.getContext)
        // Sessions map should be empty, getOrCreateSession will create a new one instead of returning cached
        val session2 = viewModel.getOrCreateSession("session-123", application)
        assertNotNull(session2)
    }
}
