package com.adamoutler.ssh.network

import com.adamoutler.ssh.network.ConnectionStateRepository
import java.io.OutputStream

// Compatibility layer for tests
object SshSessionProvider {
    var isHeadlessTest: Boolean
        get() = ConnectionStateRepository.isHeadlessTest
        set(value) { ConnectionStateRepository.isHeadlessTest = value }

    val activeConnections: kotlinx.coroutines.flow.StateFlow<Set<String>>
        get() = ConnectionStateRepository.activeConnections

    fun addConnection(id: String) {
        ConnectionStateRepository.addConnection(id)
    }

    fun removeConnection(id: String) {
        ConnectionStateRepository.removeConnection(id)
    }

    val ptyOutputStream: OutputStream?
        get() = ConnectionStateRepository.sessions.values.firstOrNull()?.ptyOutputStream

    var mockTestTranscript: String?
        get() = ConnectionStateRepository.mockTestTranscripts.values.firstOrNull()
        set(value) {
            val key = ConnectionStateRepository.mockTestTranscripts.keys().toList().firstOrNull() ?: "mock-id-ui-crash-test"
            if (value == null) {
                ConnectionStateRepository.mockTestTranscripts.remove(key)
            } else {
                ConnectionStateRepository.mockTestTranscripts[key] = value
            }
        }

    val terminalSession: com.termux.terminal.TerminalSession?
        get() = null // mock for tests
}
