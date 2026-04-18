package com.adamoutler.ssh.network

import android.os.Handler
import android.os.Looper
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
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

data class ActiveSession(
    val profileId: String,
    var ptyOutputStream: OutputStream? = null,
    var sshShell: net.schmizz.sshj.connection.channel.direct.Session.Shell? = null,
    var terminalSession: TerminalSession? = null,
    var firstSshOutputReceived: Boolean = false,
    var mockTestTranscript: String? = null,
    val connectedAt: Long = System.currentTimeMillis()
)

object SshSessionProvider {
    val sessions = ConcurrentHashMap<String, ActiveSession>()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var _onScreenUpdated: ((String) -> Unit)? = null
    var onScreenUpdated: ((String) -> Unit)?
        get() = _onScreenUpdated
        set(value) { _onScreenUpdated = value }
        
    var getContext: (() -> android.content.Context?)? = null

    fun postScreenUpdate(profileId: String) {
        _onScreenUpdated?.let { callback ->
            if (Looper.myLooper() == Looper.getMainLooper()) {
                callback(profileId)
            } else {
                mainHandler.post { callback(profileId) }
            }
        }
    }

    var isHeadlessTest: Boolean = false

    private fun getProfileIdForSession(session: TerminalSession): String? {
        return sessions.entries.find { it.value.terminalSession === session }?.key
    }

    val terminalSessionClient = object : TerminalSessionClient {
        override fun onTextChanged(session: TerminalSession) {
            getProfileIdForSession(session)?.let { profileId ->
                postScreenUpdate(profileId)
            }
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

    fun getOrCreateSession(profileId: String): ActiveSession {
        return sessions.getOrPut(profileId) {
            val newSession = ActiveSession(profileId = profileId)
            if (!isHeadlessTest) {
                newSession.terminalSession = try {
                    val termSession = TerminalSession(
                        "/system/bin/sh", "/",
                        arrayOf("sh", "-c", "exec sleep 2147483647"),
                        arrayOf("TERM=xterm-256color"),
                        0,
                        terminalSessionClient
                    )
                    termSession
                } catch (e: Throwable) {
                    android.util.Log.e("SshSessionProvider", "Failed to create TerminalSession", e)
                    null
                }
            }
            newSession
        }
    }

    fun clearSession(profileId: String) {
        sessions.remove(profileId)
    }
}
