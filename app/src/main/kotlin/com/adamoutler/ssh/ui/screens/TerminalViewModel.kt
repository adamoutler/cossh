package com.adamoutler.ssh.ui.screens

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.crypto.SettingsManager
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class TerminalViewModel(application: Application) :
    AndroidViewModel(application),
    TerminalSessionClient {
    private val sessions = mutableMapOf<String, TerminalSession>()

    companion object {
        // Exposed for testing
        var activeSessionsRef: Map<String, TerminalSession>? = null
    }

    var getContext: (() -> Context)? = null

    private val storageManager = SecurityStorageManager(application)
    private val settingsManager = SettingsManager(application)

    private val _fontSizeFlow = MutableStateFlow(14)
    val fontSizeFlow = _fontSizeFlow.asStateFlow()

    private var activeProfileId: String? = null

    init {
        activeSessionsRef = sessions
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _fontSizeFlow
                .debounce(500)
                .distinctUntilChanged()
                .collect { newSize ->
                    saveFontSize(newSize)
                }
        }
    }

    fun initFontSize(profileId: String) {
        activeProfileId = profileId
        val profile = storageManager.getProfile(profileId)
        _fontSizeFlow.value = profile?.fontSize ?: settingsManager.globalFontSize
    }

    private fun saveFontSize(size: Int) {
        activeProfileId?.let { profileId ->
            val profile = storageManager.getProfile(profileId)
            if (profile != null) {
                storageManager.saveProfile(profile.copy(fontSize = size))
            }
        }
        settingsManager.globalFontSize = size
    }

    fun updateFontSize(newSize: Int) {
        // Removed upper limit per user request. No regulation on max font size.
        _fontSizeFlow.value = newSize.coerceAtLeast(4)
    }

    fun getOrCreateSession(sessionId: String, context: Context): TerminalSession {
        getContext = { context }
        return sessions.getOrPut(sessionId) {
            TerminalSession(
                "/system/bin/sh",
                "/",
                arrayOf("sh", "-c", "stty -echo 2>/dev/null; exec sleep 2147483647"),
                arrayOf("TERM=xterm-256color"),
                0,
                this,
            )
        }
    }

    override fun onTextChanged(session: TerminalSession) { /* No-op */ }
    override fun onTitleChanged(session: TerminalSession) { /* No-op */ }
    override fun onSessionFinished(session: TerminalSession) { /* No-op */ }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        val context = getContext?.invoke() ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Terminal text", text.trimEnd())
        clipboard.setPrimaryClip(clip)
    }

    override fun onPasteTextFromClipboard(session: TerminalSession) { /* No-op */ }
    override fun onBell(session: TerminalSession) { /* No-op */ }
    override fun onColorsChanged(session: TerminalSession) { /* No-op */ }
    override fun onTerminalCursorStateChange(state: Boolean) { /* No-op */ }
    override fun getTerminalCursorStyle(): Int = 0
    override fun logError(tag: String?, msg: String?) { /* No-op */ }
    override fun logWarn(tag: String?, msg: String?) { /* No-op */ }
    override fun logInfo(tag: String?, msg: String?) { /* No-op */ }
    override fun logDebug(tag: String?, msg: String?) { /* No-op */ }
    override fun logVerbose(tag: String?, msg: String?) { /* No-op */ }
    override fun logStackTraceWithMessage(tag: String?, msg: String?, e: java.lang.Exception?) { /* No-op */ }
    override fun logStackTrace(tag: String?, e: java.lang.Exception?) { /* No-op */ }

    override fun onCleared() {
        super.onCleared()
        sessions.values.forEach { it.finishIfRunning() }
        sessions.clear()
        getContext = null
    }
}
