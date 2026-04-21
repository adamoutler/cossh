package com.adamoutler.ssh.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionStateRepositoryTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        ConnectionStateRepository.sessions.clear()
        ConnectionStateRepository.isHeadlessTest = true
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test output is buffered before UI attaches and emitted after`() = runTest(testDispatcher) {
        val profileId = "test_profile_85"
        val session = ConnectionStateRepository.getOrCreateSession(profileId)

        // UI is NOT attached yet. Emit 3 chunks.
        ConnectionStateRepository.emitOutput(profileId, "Chunk1".toByteArray())
        ConnectionStateRepository.emitOutput(profileId, "Chunk2".toByteArray())
        ConnectionStateRepository.emitOutput(profileId, "Chunk3".toByteArray())

        // Ensure buffer contains 3 chunks
        assertEquals(3, session.outputBuffer.size)

        // Now attach UI
        val bufferedBytes = ConnectionStateRepository.attachUiAndGetBuffer(profileId)
        
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
}
