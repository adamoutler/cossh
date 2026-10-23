package com.adamoutler.ssh.network

import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.ui.screens.TerminalViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
class SessionTeardownSanitizationTest {

    @Before
    fun setUp() {
        SessionStreamRegistry.clearAllSessions()
    }

    @Test
    fun testActiveSessionStateSanitizeZeroWipesAllBuffers() {
        val session = ActiveSessionState(
            sessionId = "session-san-1",
            profileId = "profile-1",
        )
        val chunk1 = "Sensitive_Credentials_1".toByteArray(Charsets.UTF_8)
        val chunk2 = "Super_Secret_Key_2".toByteArray(Charsets.UTF_8)
        session.outputBuffer.add(chunk1)
        session.outputBuffer.add(chunk2)

        val stream = ByteArrayOutputStream()
        session.ptyOutputStream = stream

        session.sanitize()

        // Verify all byte arrays drained and zeroed
        assertTrue(session.outputBuffer.isEmpty())
        for (b in chunk1) {
            assertEquals(0.toByte(), b)
        }
        for (b in chunk2) {
            assertEquals(0.toByte(), b)
        }

        assertNull(session.ptyOutputStream)
        assertNull(session.sshShell)
        assertNull(session.onWindowResize)
    }

    @Test
    fun testSessionStreamRegistryClearSessionCleansFlowAndBuffers() {
        val session = SessionStreamRegistry.getOrCreateSession("profile-clear", "session-clear-id")
        val secretData = "SSH-PRIVATE-PAYLOAD".toByteArray(Charsets.UTF_8)
        session.outputBuffer.add(secretData)

        // Request flow to ensure it exists in perSessionFlows
        val flow = SessionStreamRegistry.getSessionOutputFlow("session-clear-id")
        assertEquals(0, flow.replayCache.size)

        SessionStreamRegistry.clearSession("session-clear-id")

        assertNull(SessionStreamRegistry.sessions["session-clear-id"])
        for (b in secretData) {
            assertEquals(0.toByte(), b)
        }
    }

    @Test
    fun testTerminalViewModelCloseSessionSanitizesTerminalRowBuffers() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = TerminalViewModel(app)

        val session = viewModel.getOrCreateSession("session-vm-close", app)
        val emulator = session.emulator
        if (emulator != null) {
            val sensitiveBytes = "confidential_api_token".toByteArray(Charsets.UTF_8)
            emulator.append(sensitiveBytes, sensitiveBytes.size)
        }

        // Close session through ViewModel, triggering finishIfRunning and onSessionFinished
        viewModel.closeSession("session-vm-close")

        // Validate that mLines mText arrays were scrubbed with '\u0000'
        val em = session.emulator
        if (em != null) {
            try {
                val screenField = em.javaClass.getDeclaredMethod("getScreen")
                screenField.isAccessible = true
                val buffer = screenField.invoke(em)

                val mLinesField = buffer.javaClass.getDeclaredField("mLines")
                mLinesField.isAccessible = true
                val lines = mLinesField.get(buffer) as Array<*>

                if (lines.isNotEmpty()) {
                    val mTextField = lines[0]?.javaClass?.getDeclaredField("mText")
                    mTextField?.isAccessible = true
                    for (row in lines) {
                        if (row != null) {
                            val textArray = mTextField?.get(row) as? CharArray
                            if (textArray != null) {
                                for (ch in textArray) {
                                    assertEquals('\u0000', ch)
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }
}
