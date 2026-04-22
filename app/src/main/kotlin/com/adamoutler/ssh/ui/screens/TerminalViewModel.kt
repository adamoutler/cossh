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
class TerminalViewModel(application: Application) : AndroidViewModel(application), TerminalSessionClient {
    private val sessions = mutableMapOf<String, TerminalSession>()
    var getContext: (() -> Context)? = null

    private val storageManager = SecurityStorageManager(application)
    private val settingsManager = SettingsManager(application)

    private val _fontSizeFlow = MutableStateFlow(14)
    val fontSizeFlow = _fontSizeFlow.asStateFlow()

    private var activeProfileId: String? = null

    init {
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
        _fontSizeFlow.value = newSize.coerceIn(4, 40)
    }

    fun getOrCreateSession(sessionId: String, context: Context): TerminalSession {
        getContext = { context }
        return sessions.getOrPut(sessionId) {
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
