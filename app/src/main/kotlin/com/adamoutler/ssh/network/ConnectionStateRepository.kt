package com.adamoutler.ssh.network

import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import net.schmizz.sshj.connection.channel.direct.Session.Shell

sealed interface ConnectionState {
    object Connecting : ConnectionState
    object Connected : ConnectionState
    data class Error(val message: String) : ConnectionState
}

data class ActiveSessionState(
    val profileId: String,
    var ptyOutputStream: OutputStream? = null,
    var sshShell: Shell? = null,
    var firstSshOutputReceived: Boolean = false,
    val connectedAt: Long = System.currentTimeMillis()
)

object ConnectionStateRepository {
    val sessions = ConcurrentHashMap<String, ActiveSessionState>()

    var isHeadlessTest: Boolean = false
    val mockTestTranscripts = ConcurrentHashMap<String, String>()

    private val _connectionStates = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
    val connectionStates: StateFlow<Map<String, ConnectionState>> = _connectionStates.asStateFlow()

    private val _activeConnections = MutableStateFlow<Set<String>>(emptySet())
    val activeConnections: StateFlow<Set<String>> = _activeConnections.asStateFlow()

    private val _sessionOutput = MutableSharedFlow<Pair<String, ByteArray>>(extraBufferCapacity = 1000)
    val sessionOutput = _sessionOutput.asSharedFlow()

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

    fun getOrCreateSession(profileId: String): ActiveSessionState {
        return sessions.getOrPut(profileId) {
            ActiveSessionState(profileId = profileId)
        }
    }

    fun clearSession(profileId: String) {
        sessions.remove(profileId)
        mockTestTranscripts.remove(profileId)
    }

    suspend fun emitOutput(profileId: String, data: ByteArray) {
        _sessionOutput.emit(Pair(profileId, data))
    }
}
