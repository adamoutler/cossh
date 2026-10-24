package com.adamoutler.ssh.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import net.schmizz.sshj.connection.channel.direct.Session.Shell
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Encapsulates the runtime state of an active SSH terminal session.
 */
data class ActiveSessionState(
    val sessionId: String = UUID.randomUUID().toString(),
    val profileId: String,
    var ptyOutputStream: OutputStream? = null,
    var sshShell: Shell? = null,
    var firstSshOutputReceived: Boolean = false,
    val connectedAt: Long = System.currentTimeMillis(),
    var isUiAttached: Boolean = false,
    var onWindowResize: ((cols: Int, rows: Int, widthPx: Int, heightPx: Int) -> Unit)? = null,
    val outputBuffer: ConcurrentLinkedQueue<ByteArray> = ConcurrentLinkedQueue(),
    val bufferLock: Any = Any(),
) {
    fun sanitize() {
        synchronized(bufferLock) {
            while (true) {
                val bytes = outputBuffer.poll() ?: break
                bytes.fill(0)
            }
        }
        try {
            ptyOutputStream?.close()
        } catch (_: Exception) {
        }
        ptyOutputStream = null
        sshShell = null
        onWindowResize = null
    }
}

/**
 * Manages active SSH session streams, PTY output streams, shell channels,
 * and PTY output buffer queues.
 */
object SessionStreamRegistry {
    val sessions = ConcurrentHashMap<String, ActiveSessionState>()

    var isHeadlessTest: Boolean = false
    val mockTestTranscripts = ConcurrentHashMap<String, String>()

    private val _sessionOutput = MutableSharedFlow<Pair<String, ByteArray>>(extraBufferCapacity = 1000)
    val sessionOutput: SharedFlow<Pair<String, ByteArray>> = _sessionOutput.asSharedFlow()

    private val perSessionFlows = ConcurrentHashMap<String, MutableSharedFlow<ByteArray>>()

    fun getSessionOutputFlow(sessionId: String): SharedFlow<ByteArray> = perSessionFlows.computeIfAbsent(sessionId) {
        MutableSharedFlow(extraBufferCapacity = 1000)
    }.asSharedFlow()

    fun getOrCreateSession(profileId: String, sessionId: String? = null): ActiveSessionState {
        if (sessionId != null) {
            sessions[sessionId]?.let { return it }
        }
        val newSessionId = sessionId ?: UUID.randomUUID().toString()
        val session = ActiveSessionState(sessionId = newSessionId, profileId = profileId)
        sessions[newSessionId] = session
        return session
    }

    fun clearSession(sessionId: String) {
        val session = sessions.remove(sessionId)
        session?.sanitize()
        perSessionFlows.remove(sessionId)
        if (!isHeadlessTest) {
            mockTestTranscripts.remove(sessionId)
        }
    }

    fun clearAllSessions() {
        for (sessionId in sessions.keys().toList()) {
            clearSession(sessionId)
        }
        perSessionFlows.clear()
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
                perSessionFlows[sessionId]?.emit(data)
            }
        } else {
            _sessionOutput.emit(Pair(sessionId, data))
            perSessionFlows[sessionId]?.emit(data)
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
