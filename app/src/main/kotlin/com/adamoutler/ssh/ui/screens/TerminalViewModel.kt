package com.adamoutler.ssh.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import com.adamoutler.ssh.network.ConnectionStateRepository
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient

class TerminalViewModel : ViewModel(), TerminalSessionClient {
    private val sessions = mutableMapOf<String, TerminalSession>()
    var getContext: (() -> Context)? = null

    fun getOrCreateSession(profileId: String, context: Context): TerminalSession {
        getContext = { context }
        return sessions.getOrPut(profileId) {
            TerminalSession(
                "/system/bin/sh", "/",
                arrayOf("sh", "-c", "exec sleep 2147483647"),
                arrayOf("TERM=xterm-256color"),
                0,
                this
            )
        }
    }

    override fun onTextChanged(session: TerminalSession) {}
    override fun onTitleChanged(session: TerminalSession) {}
    override fun onSessionFinished(session: TerminalSession) {}
    
    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        val context = getContext?.invoke() ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Terminal text", text.trimEnd())
        clipboard.setPrimaryClip(clip)
    }

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

    override fun onCleared() {
        super.onCleared()
        sessions.values.forEach { it.finishIfRunning() }
        sessions.clear()
        getContext = null
    }
}
