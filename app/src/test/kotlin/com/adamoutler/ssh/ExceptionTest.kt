package com.adamoutler.ssh

import org.junit.Test
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient

class ExceptionTest {
    @Test
    fun testException() {
        try {
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
                override fun logStackTraceWithMessage(tag: String?, msg: String?, e: java.lang.Exception?) {}
                override fun logStackTrace(tag: String?, e: java.lang.Exception?) {}
            }
            TerminalSession("/system/bin/sh", "/", arrayOf("-c", "cat"), arrayOf("TERM=xterm-256color"), 100, client)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}
