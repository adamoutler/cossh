package com.adamoutler.ssh.network

import java.io.OutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SshSessionProvider {
    var ptyOutputStream: OutputStream? = null
    var onOutputReceived: ((ByteArray, Int) -> Unit)? = null
    
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
}
