package com.adamoutler.ssh.network

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
class SessionStreamRegistryIsolationTest {

    @Before
    fun setUp() {
        SessionStreamRegistry.clearAllSessions()
    }

    @Test
    fun testConcurrentSessionBufferIsolation() {
        val s1 = SessionStreamRegistry.getOrCreateSession("profile-1", "session-1")
        val s2 = SessionStreamRegistry.getOrCreateSession("profile-1", "session-2")

        val data1 = "session 1 output".toByteArray()
        val data2 = "session 2 output".toByteArray()

        s1.outputBuffer.add(data1)
        s2.outputBuffer.add(data2)

        val retrieved1 = SessionStreamRegistry.attachUiAndGetBuffer("session-1")
        val retrieved2 = SessionStreamRegistry.attachUiAndGetBuffer("session-2")

        assertEquals(1, retrieved1.size)
        assertArrayEquals(data1, retrieved1[0])

        assertEquals(1, retrieved2.size)
        assertArrayEquals(data2, retrieved2[0])
    }

    @Test
    fun testSessionTeardownZeroWipesBuffer() {
        val session = SessionStreamRegistry.getOrCreateSession("profile-test", "session-wipe")
        val secretBytes = "very_sensitive_password".toByteArray()
        session.outputBuffer.add(secretBytes)

        val ptyStream = ByteArrayOutputStream()
        session.ptyOutputStream = ptyStream

        // Perform teardown / sanitize
        SessionStreamRegistry.clearSession("session-wipe")

        assertNull(SessionStreamRegistry.sessions["session-wipe"])

        // The buffer array in secretBytes should have been zero-wiped
        for (b in secretBytes) {
            assertEquals(0.toByte(), b)
        }
    }

    @Test
    fun testSessionStateTrackingInConnectionStateRepository() {
        ConnectionStateRepository.updateSessionState("session-abc", ConnectionState.Connected)
        assertEquals(ConnectionState.Connected, ConnectionStateRepository.sessionStates.value["session-abc"])

        ConnectionStateRepository.clearSessionState("session-abc")
        assertNull(ConnectionStateRepository.sessionStates.value["session-abc"])
    }
}
