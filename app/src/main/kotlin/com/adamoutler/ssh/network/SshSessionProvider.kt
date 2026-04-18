package com.adamoutler.ssh.network

import android.os.Handler
import android.os.Looper
import java.io.OutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient

sealed interface ConnectionState {
    object Connecting : ConnectionState
    object Connected : ConnectionState
    data class Error(val message: String) : ConnectionState
}

object SshSessionProvider {
    var ptyOutputStream: OutputStream? = null
    var activeSshSession: net.schmizz.sshj.connection.channel.direct.Session.Shell? = null
    
    var terminalSession: TerminalSession? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var _onScreenUpdated: (() -> Unit)? = null
    var onScreenUpdated: (() -> Unit)?
        get() = _onScreenUpdated
        set(value) { _onScreenUpdated = value }
    var getContext: (() -> android.content.Context?)? = null

    /**
     * Safely invoke the screen update callback on the main thread.
     */
    fun postScreenUpdate() {
        _onScreenUpdated?.let { callback ->
            if (Looper.myLooper() == Looper.getMainLooper()) {
                callback()
            } else {
                mainHandler.post { callback() }
            }
        }
    }
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

    private val _connectionStates = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
    val connectionStates: StateFlow<Map<String, ConnectionState>> = _connectionStates.asStateFlow()

    private val _activeConnections = MutableStateFlow<Set<String>>(emptySet())
    val activeConnections: StateFlow<Set<String>> = _activeConnections.asStateFlow()

    fun updateConnectionState(profileId: String, state: ConnectionState) {
        val newMap = _connectionStates.value.toMutableMap()
        newMap[profileId] = state
        _connectionStates.value = newMap
        
        if (state == ConnectionState.Connected) {
            _activeConnections.value = _activeConnections.value + profileId
        } else {
            _activeConnections.value = _activeConnections.value - profileId
        }
    }

    fun clearConnectionState(profileId: String) {
        val newMap = _connectionStates.value.toMutableMap()
        newMap.remove(profileId)
        _connectionStates.value = newMap
    }

    fun addConnection(profileId: String) {
        _activeConnections.value = _activeConnections.value + profileId
    }

    fun removeConnection(profileId: String) {
        _activeConnections.value = _activeConnections.value - profileId
    }

    fun clearConnections() {
        _activeConnections.value = emptySet()
    }

    /** Track whether we've received the first SSH output to clear subprocess artifacts. */
    @Volatile
    var firstSshOutputReceived = false

    fun getOrCreateSession(): TerminalSession? {
        if (isHeadlessTest) return null
        if (terminalSession == null) {
            terminalSession = try {
                // Use 'sh -c exec sleep' as a silent blocking process to keep TerminalSession alive.
                // Key design decisions:
                //   - argv[0] must be "sh" (toybox multi-call binary uses argv[0] to pick the applet)
                //   - 'exec' replaces the shell with sleep (1 process, not 2)
                //   - sleep does NOT read stdin, preventing echo feedback loops through the PTY
                //   - The subprocess output (if any) will be cleared on first SSH data arrival
                val session = TerminalSession(
                    "/system/bin/sh", "/",
                    arrayOf("sh", "-c", "exec sleep 2147483647"),
                    arrayOf("TERM=xterm-256color"),
                    0, // transcript rows = 0: no scrollback needed since SSH server manages its own
                    terminalSessionClient
                )
                firstSshOutputReceived = false
                session
            } catch (e: Throwable) {
                android.util.Log.e("SshSessionProvider", "Failed to create TerminalSession", e)
                null
            }
        }
        return terminalSession
    }

    fun clearSession() {
        terminalSession = null
        ptyOutputStream = null
        firstSshOutputReceived = false
    }
}
