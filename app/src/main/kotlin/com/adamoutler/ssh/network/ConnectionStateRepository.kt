package com.adamoutler.ssh.network

import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

sealed interface ConnectionState {
    object Connecting : ConnectionState
    object Connected : ConnectionState
    object Disconnecting : ConnectionState
    object Disconnected : ConnectionState
    data class Terminated(val reason: String?) : ConnectionState
    data class Error(val message: String) : ConnectionState
}

/**
 * Manages high-level connection lifecycle states and active connection counts.
 * Delegates session byte streams and user interactive prompts to dedicated domain registries
 * [SessionStreamRegistry] and [UserPromptRegistry] for backwards compatibility.
 */
object ConnectionStateRepository {
    // --- Connection Lifecycle State Management ---
    private val _connectionStates = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
    val connectionStates: StateFlow<Map<String, ConnectionState>> = _connectionStates.asStateFlow()

    private val _activeConnectionCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val activeConnectionCounts: StateFlow<Map<String, Int>> = _activeConnectionCounts.asStateFlow()

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

    // --- Delegations to UserPromptRegistry (Backwards Compatibility) ---
    val promptRequest: StateFlow<HostKeyPromptRequest?>
        get() = UserPromptRegistry.promptRequest

    val authPromptRequest: StateFlow<AuthPromptRequest?>
        get() = UserPromptRegistry.authPromptRequest

    suspend fun requestAuthPrompt(profileId: String, requireUsername: Boolean, isRetry: Boolean): AuthCredentials? = UserPromptRegistry.requestAuthPrompt(profileId, requireUsername, isRetry)

    fun resolveAuthPrompt(credentials: AuthCredentials?) = UserPromptRegistry.resolveAuthPrompt(credentials)

    suspend fun requestPrompt(
        hostname: String,
        expectedFingerprint: String?,
        receivedFingerprint: String,
        isKeyChanged: Boolean,
    ): Boolean = UserPromptRegistry.requestPrompt(hostname, expectedFingerprint, receivedFingerprint, isKeyChanged)

    fun resolvePrompt(accept: Boolean) = UserPromptRegistry.resolvePrompt(accept)

    @VisibleForTesting
    fun setPromptRequestForTest(request: HostKeyPromptRequest?) = UserPromptRegistry.setPromptRequestForTest(request)

    @VisibleForTesting
    fun setAuthPromptRequestForTest(request: AuthPromptRequest?) = UserPromptRegistry.setAuthPromptRequestForTest(request)

    // --- Delegations to SessionStreamRegistry (Backwards Compatibility) ---
    val sessions: ConcurrentHashMap<String, ActiveSessionState>
        get() = SessionStreamRegistry.sessions

    var isHeadlessTest: Boolean
        get() = SessionStreamRegistry.isHeadlessTest
        set(value) {
            SessionStreamRegistry.isHeadlessTest = value
        }

    val mockTestTranscripts: ConcurrentHashMap<String, String>
        get() = SessionStreamRegistry.mockTestTranscripts

    val sessionOutput: SharedFlow<Pair<String, ByteArray>>
        get() = SessionStreamRegistry.sessionOutput

    fun getOrCreateSession(profileId: String, sessionId: String? = null): ActiveSessionState = SessionStreamRegistry.getOrCreateSession(profileId, sessionId)

    fun clearSession(sessionId: String) = SessionStreamRegistry.clearSession(sessionId)

    suspend fun emitOutput(sessionId: String, data: ByteArray) = SessionStreamRegistry.emitOutput(sessionId, data)

    fun attachUiAndGetBuffer(sessionId: String): List<ByteArray> = SessionStreamRegistry.attachUiAndGetBuffer(sessionId)

    fun detachUi(sessionId: String) = SessionStreamRegistry.detachUi(sessionId)
}
