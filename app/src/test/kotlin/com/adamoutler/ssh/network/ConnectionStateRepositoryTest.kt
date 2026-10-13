package com.adamoutler.ssh.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionStateRepositoryTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        ConnectionStateRepository.sessions.clear()
        ConnectionStateRepository.clearConnections()
        ConnectionStateRepository.isHeadlessTest = true
    }

    @After
    fun tearDown() {
        ConnectionStateRepository.clearConnections()
        Dispatchers.resetMain()
    }

    @Test
    fun `test output is buffered before UI attaches and emitted after`() = runTest(testDispatcher) {
        val profileId = "test_profile_85"
        val session = ConnectionStateRepository.getOrCreateSession(profileId)

        // UI is NOT attached yet. Emit 3 chunks.
        ConnectionStateRepository.emitOutput(session.sessionId, "Chunk1".toByteArray())
        ConnectionStateRepository.emitOutput(session.sessionId, "Chunk2".toByteArray())
        ConnectionStateRepository.emitOutput(session.sessionId, "Chunk3".toByteArray())

        // Ensure buffer contains 3 chunks
        assertEquals(3, session.outputBuffer.size)

        // Now attach UI
        val bufferedBytes = ConnectionStateRepository.attachUiAndGetBuffer(session.sessionId)

        // Ensure bufferedBytes contains the 3 chunks
        assertEquals(3, bufferedBytes.size)
        assertEquals("Chunk1", String(bufferedBytes[0]))
        assertEquals("Chunk2", String(bufferedBytes[1]))
        assertEquals("Chunk3", String(bufferedBytes[2]))

        // Ensure buffer is drained
        assertEquals(0, session.outputBuffer.size)

        // Now emit a 4th chunk and verify it does NOT go to buffer
        ConnectionStateRepository.emitOutput(profileId, "Chunk4".toByteArray())
        assertEquals(0, session.outputBuffer.size)
    }

    @Test
    fun `test connection lifecycle states and transitions`() {
        val profileId = "profile-lifecycle-test"

        assertEquals(emptyMap<String, ConnectionState>(), ConnectionStateRepository.connectionStates.value)

        ConnectionStateRepository.updateConnectionState(profileId, ConnectionState.Connecting)
        assertEquals(ConnectionState.Connecting, ConnectionStateRepository.connectionStates.value[profileId])

        ConnectionStateRepository.updateConnectionState(profileId, ConnectionState.Connected)
        assertEquals(ConnectionState.Connected, ConnectionStateRepository.connectionStates.value[profileId])

        ConnectionStateRepository.updateConnectionState(profileId, ConnectionState.Disconnecting)
        assertEquals(ConnectionState.Disconnecting, ConnectionStateRepository.connectionStates.value[profileId])

        ConnectionStateRepository.updateConnectionState(profileId, ConnectionState.Disconnected)
        assertEquals(ConnectionState.Disconnected, ConnectionStateRepository.connectionStates.value[profileId])

        ConnectionStateRepository.updateConnectionState(profileId, ConnectionState.Error("Auth failed"))
        val errorState = ConnectionStateRepository.connectionStates.value[profileId] as ConnectionState.Error
        assertEquals("Auth failed", errorState.message)

        ConnectionStateRepository.clearConnectionState(profileId)
        assertNull(ConnectionStateRepository.connectionStates.value[profileId])
    }

    @Test
    fun `test active connection counts management`() {
        val profileId = "profile-counts-test"

        assertEquals(0, ConnectionStateRepository.activeConnectionCounts.value[profileId] ?: 0)

        ConnectionStateRepository.addConnection(profileId)
        assertEquals(1, ConnectionStateRepository.activeConnectionCounts.value[profileId])

        ConnectionStateRepository.addConnection(profileId)
        assertEquals(2, ConnectionStateRepository.activeConnectionCounts.value[profileId])

        ConnectionStateRepository.removeConnection(profileId)
        assertEquals(1, ConnectionStateRepository.activeConnectionCounts.value[profileId])

        ConnectionStateRepository.removeConnection(profileId)
        assertNull(ConnectionStateRepository.activeConnectionCounts.value[profileId])

        // Calling removeConnection when 0 should be a no-op
        ConnectionStateRepository.removeConnection(profileId)
        assertNull(ConnectionStateRepository.activeConnectionCounts.value[profileId])

        // clearConnections
        ConnectionStateRepository.addConnection("p1")
        ConnectionStateRepository.addConnection("p2")
        assertEquals(2, ConnectionStateRepository.activeConnectionCounts.value.size)
        ConnectionStateRepository.clearConnections()
        assertEquals(0, ConnectionStateRepository.activeConnectionCounts.value.size)
    }
}
