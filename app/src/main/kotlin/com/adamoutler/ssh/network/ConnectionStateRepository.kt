package com.adamoutler.ssh.network

import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
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
    val sessionId: String = java.util.UUID.randomUUID().toString(),
    val profileId: String,
    var ptyOutputStream: OutputStream? = null,
    var sshShell: Shell? = null,
    var firstSshOutputReceived: Boolean = false,
    val connectedAt: Long = System.currentTimeMillis(),
    var isUiAttached: Boolean = false,
    val outputBuffer: ConcurrentLinkedQueue<ByteArray> = ConcurrentLinkedQueue(),
    val bufferLock: Any = Any()
)

object ConnectionStateRepository {
    val sessions = ConcurrentHashMap<String, ActiveSessionState>()

    var isHeadlessTest: Boolean = false
    val mockTestTranscripts = ConcurrentHashMap<String, String>()

    private val _connectionStates = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
    val connectionStates: StateFlow<Map<String, ConnectionState>> = _connectionStates.asStateFlow()

    private val _activeConnectionCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val activeConnectionCounts: StateFlow<Map<String, Int>> = _activeConnectionCounts.asStateFlow()

    private val _sessionOutput = MutableSharedFlow<Pair<String, ByteArray>>(extraBufferCapacity = 1000)
    val sessionOutput = _sessionOutput.asSharedFlow()

    fun updateConnectionState(profileId: String, state: ConnectionState) {
        val newMap = _connectionStates.value.toMutableMap()
        newMap[profileId] = state
        _connectionStates.value = newMap
    }

    fun clearConnectionState(profileId: String) {
        val newMap = _connectionStates.value.toMutableMap()
        newMap.remove(profileId)
        _connectionStates.value = newMap
    }

    fun addConnection(profileId: String) {
        val currentCount = _activeConnectionCounts.value[profileId] ?: 0
        val newMap = _activeConnectionCounts.value.toMutableMap()
        newMap[profileId] = currentCount + 1
        _activeConnectionCounts.value = newMap
    }

    fun removeConnection(profileId: String) {
        val currentCount = _activeConnectionCounts.value[profileId] ?: 0
        if (currentCount > 0) {
            val newMap = _activeConnectionCounts.value.toMutableMap()
            if (currentCount == 1) {
                newMap.remove(profileId)
            } else {
                newMap[profileId] = currentCount - 1
            }
            _activeConnectionCounts.value = newMap
        }
    }

    fun clearConnections() {
        _activeConnectionCounts.value = emptyMap()
    }

    fun getOrCreateSession(profileId: String, sessionId: String? = null): ActiveSessionState {
        if (sessionId != null) {
            sessions[sessionId]?.let { return it }
        }
        val newSessionId = sessionId ?: java.util.UUID.randomUUID().toString()
        val session = ActiveSessionState(sessionId = newSessionId, profileId = profileId)
        sessions[newSessionId] = session
        return session
    }

    fun clearSession(sessionId: String) {
        sessions.remove(sessionId)
        mockTestTranscripts.remove(sessionId)
    }

    suspend fun emitOutput(sessionId: String, data: ByteArray) {
        val session = sessions[sessionId]
        if (session != null) {
            var emitToFlow = false
            synchronized(session.bufferLock) {
                if (!session.isUiAttached) {
                    session.outputBuffer.add(data)
                } else {
                    emitToFlow = true
                }
            }
            if (emitToFlow) {
                _sessionOutput.emit(Pair(sessionId, data))
            }
        } else {
            _sessionOutput.emit(Pair(sessionId, data))
        }
    }

    fun attachUiAndGetBuffer(sessionId: String): List<ByteArray> {
        val session = sessions[sessionId] ?: return emptyList()
        val buffer = mutableListOf<ByteArray>()
        synchronized(session.bufferLock) {
            session.isUiAttached = true
            while (true) {
                val bytes = session.outputBuffer.poll() ?: break
                buffer.add(bytes)
            }
        }
        return buffer
    }

    fun detachUi(sessionId: String) {
        val session = sessions[sessionId]
        if (session != null) {
            synchronized(session.bufferLock) {
                session.isUiAttached = false
            }
        }
    }
}
