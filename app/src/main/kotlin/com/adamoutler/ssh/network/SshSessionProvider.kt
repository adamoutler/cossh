package com.adamoutler.ssh.network

import java.io.OutputStream

// Compatibility layer for tests
object SshSessionProvider {
    var isHeadlessTest: Boolean
        get() = ConnectionStateRepository.isHeadlessTest
        set(value) {
            ConnectionStateRepository.isHeadlessTest = value
        }

    val activeConnectionCounts: kotlinx.coroutines.flow.StateFlow<Map<String, Int>>
        get() = ConnectionStateRepository.activeConnectionCounts

    fun addConnection(id: String) {
        ConnectionStateRepository.addConnection(id)
    }

    fun removeConnection(id: String) {
        ConnectionStateRepository.removeConnection(id)
    }

    val ptyOutputStream: OutputStream?
        get() = ConnectionStateRepository.sessions.values.firstOrNull()?.ptyOutputStream

    var mockTestTranscript: String?
        get() = ConnectionStateRepository.mockTestTranscripts.values.joinToString("\n")
        set(value) {
            if (value == null) {
                ConnectionStateRepository.mockTestTranscripts.clear()
            } else {
                val key = ConnectionStateRepository.mockTestTranscripts.keys().toList().firstOrNull() ?: "mock-id-ui-crash-test"
                ConnectionStateRepository.mockTestTranscripts[key] = value
            }
        }

    val terminalSession: com.termux.terminal.TerminalSession?
        get() = com.adamoutler.ssh.ui.screens.TerminalViewModel.activeSessionsRef?.values?.firstOrNull()
}
