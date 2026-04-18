package com.adamoutler.ssh.ui.components

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.network.SshSessionProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class TerminalSessionPersistenceTest {

    @Before
    fun setup() {
        SshSessionProvider.isHeadlessTest = false
        SshSessionProvider.clearSession("test")
    }

    @Test
    fun testTerminalSessionIsPersistedInProvider() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SshSessionProvider.getContext = { context }
        
        // Emulate SshService initializing the session
        val session1 = SshSessionProvider.getOrCreateSession("test").terminalSession
        assertNotNull(session1)
        
        // Emulate SshService receiving output from the network
        val testOutput = "Network output test\r\n"
        session1?.emulator?.append(testOutput.toByteArray(), testOutput.length)
        
        // Emulate TerminalScreen picking up the session (e.g. upon Activity resume)
        val session2 = SshSessionProvider.getOrCreateSession("test").terminalSession
        
        // Assert it is the exact same instance
        assertTrue(session1 === session2)
    }
}
