package com.adamoutler.ssh

import org.junit.Test
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.lang.Exception

class ReflectionTest {
    @Test
    fun testSession() {
        val client = object : TerminalSessionClient {
            override fun onTextChanged(session: TerminalSession) {}
            override fun onTitleChanged(session: TerminalSession) {}
            override fun onSessionFinished(session: TerminalSession) {}
            override fun onCopyTextToClipboard(session: TerminalSession, text: String) {}
            override fun onPasteTextFromClipboard(session: TerminalSession) {}
            override fun onBell(session: TerminalSession) {}
            override fun onColorsChanged(session: TerminalSession) {}
            override fun onTerminalCursorStateChange(state: Boolean) {}
            override fun getTerminalCursorStyle(): Int = 0
            override fun logError(tag: String?, msg: String?) {}
            override fun logWarn(tag: String?, msg: String?) {}
            override fun logInfo(tag: String?, msg: String?) {}
            override fun logDebug(tag: String?, msg: String?) {}
            override fun logVerbose(tag: String?, msg: String?) {}
            override fun logStackTraceWithMessage(tag: String?, msg: String?, e: Exception?) {}
            override fun logStackTrace(tag: String?, e: Exception?) {}
        }
        try {
            val session = TerminalSession("sh", "", arrayOf(), arrayOf(), 100, client)
            println("SESSION CREATED")
        } catch(e: Exception) {
            println("ERROR: " + e)
        }
    }
}
