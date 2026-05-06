package com.adamoutler.ssh.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SshSessionProviderTest {

    @Before
    fun setup() {
        ConnectionStateRepository.sessions.clear()
        ConnectionStateRepository.isHeadlessTest = true
        ConnectionStateRepository.mockTestTranscripts.clear()
    }

    @Test
    fun `test isHeadlessTest setter and getter`() {
        SshSessionProvider.isHeadlessTest = false
        assertEquals(false, SshSessionProvider.isHeadlessTest)
        SshSessionProvider.isHeadlessTest = true
        assertEquals(true, SshSessionProvider.isHeadlessTest)
    }

    @Test
    fun `test addConnection and removeConnection`() {
        val id = "test_connection_id"
        SshSessionProvider.addConnection(id)
        assertTrue(SshSessionProvider.activeConnectionCounts.value.containsKey(id))
        assertEquals(1, SshSessionProvider.activeConnectionCounts.value[id])

        SshSessionProvider.addConnection(id)
        assertEquals(2, SshSessionProvider.activeConnectionCounts.value[id])

        SshSessionProvider.removeConnection(id)
        assertEquals(1, SshSessionProvider.activeConnectionCounts.value[id])

        SshSessionProvider.removeConnection(id)
        assertNull(SshSessionProvider.activeConnectionCounts.value[id])
    }

    @Test
    fun `test mockTestTranscript`() {
        assertTrue(SshSessionProvider.mockTestTranscript.isNullOrEmpty())

        SshSessionProvider.mockTestTranscript = "test transcript line 1"
        assertEquals("test transcript line 1", SshSessionProvider.mockTestTranscript)

        SshSessionProvider.mockTestTranscript = "test transcript line 2"
        assertEquals("test transcript line 2", SshSessionProvider.mockTestTranscript)

        SshSessionProvider.mockTestTranscript = null
        assertTrue(SshSessionProvider.mockTestTranscript.isNullOrEmpty())
    }

    @Test
    fun `test ptyOutputStream and terminalSession are null when empty`() {
        assertNull(SshSessionProvider.ptyOutputStream)
        assertNull(SshSessionProvider.terminalSession)
    }
}
