package com.adamoutler.ssh.network

import java.io.OutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient

object SshSessionProvider {
    var ptyOutputStream: OutputStream? = null
    
    var terminalSession: TerminalSession? = null
    var onScreenUpdated: (() -> Unit)? = null
    var getContext: (() -> android.content.Context?)? = null
    var mockTestTranscript: String? = null
    var isHeadlessTest: Boolean = false

    val terminalSessionClient = object : TerminalSessionClient {
        override fun onTextChanged(session: TerminalSession) {
            onScreenUpdated?.invoke()
        }
        override fun onTitleChanged(session: TerminalSession) {}
        override fun onSessionFinished(session: TerminalSession) {}
        override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
            val context = getContext?.invoke() ?: return
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
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
    }

    private val _activeConnections = MutableStateFlow<Set<String>>(emptySet())
    val activeConnections: StateFlow<Set<String>> = _activeConnections.asStateFlow()

    fun addConnection(profileId: String) {
        _activeConnections.value = _activeConnections.value + profileId
    }

    fun removeConnection(profileId: String) {
        _activeConnections.value = _activeConnections.value - profileId
    }

    fun clearConnections() {
        _activeConnections.value = emptySet()
    }

    fun getOrCreateSession(): TerminalSession? {
        if (terminalSession == null) {
            terminalSession = try {
                val session = TerminalSession("/system/bin/sh", "/", arrayOf("-c", "cat"), arrayOf(), 100, terminalSessionClient)
                val initialText = "Welcome to CoSSH Terminal\r\n\u001B[32mANSI Color Support Active!\u001B[0m\r\n"
                session.emulator?.append(initialText.toByteArray(), initialText.length)
                session
            } catch (e: Throwable) {
                null
            }
        }
        return terminalSession
    }

    fun clearSession() {
        terminalSession = null
        ptyOutputStream = null
    }
}
