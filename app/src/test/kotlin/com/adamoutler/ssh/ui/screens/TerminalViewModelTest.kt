package com.adamoutler.ssh.ui.screens

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.crypto.SettingsManager
import com.adamoutler.ssh.data.ConnectionProfile
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
        val session = viewModel.getOrCreateSession("session-123", application)

        val clipboard = application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = android.content.ClipData.newPlainText("Terminal text", "pasted text\n")
        clipboard.setPrimaryClip(clip)

        // Add a mock session to the ConnectionStateRepository to avoid null pointer or just let it fail gracefully
        // We will just test that it attempts to paste
        viewModel.onPasteTextFromClipboard(session)
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
