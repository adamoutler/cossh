package com.adamoutler.ssh.ui.components

import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.robolectric.RobolectricTestRunner
import com.termux.terminal.TerminalSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class TerminalScreenCopyTest {

    @Test
    fun testTerminalCopyTextStripsTrailingSpaces() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        
        com.adamoutler.ssh.network.SshSessionProvider.getContext = { context }
        val client = com.adamoutler.ssh.network.SshSessionProvider.terminalSessionClient
        
        val dummySession = try {
            TerminalSession("/system/bin/sh", "/", arrayOf(), arrayOf("TERM=xterm-256color"), 100, client)
        } catch (e: Throwable) {
            null
        }
        
        val originalText = "User copied text   \n  \n"
        if (dummySession != null) {
            client.onCopyTextToClipboard(dummySession, originalText)
        }
        
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboardManager.primaryClip
        
        assertNotNull(clipData)
        val copiedText = clipData?.getItemAt(0)?.text?.toString()
        assertEquals("User copied text", copiedText)
    }
}