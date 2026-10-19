package com.adamoutler.ssh.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.schmizz.sshj.connection.channel.direct.Session.Shell
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.lang.reflect.Proxy

class SessionStreamRegistryTest {

    private class CloseTrackingOutputStream : ByteArrayOutputStream() {
        var isClosed = false
        override fun close() {
            super.close()
            isClosed = true
        }
    }

    @Before
    fun setUp() {
        SessionStreamRegistry.clearAllSessions()
        SessionStreamRegistry.mockTestTranscripts.clear()
        SessionStreamRegistry.isHeadlessTest = false
    }

    @After
    fun tearDown() {
        SessionStreamRegistry.clearAllSessions()
        SessionStreamRegistry.mockTestTranscripts.clear()
        SessionStreamRegistry.isHeadlessTest = false
    }

    @Test
    fun testSessionCreation_generatesIdWhenNull() {
        val session = SessionStreamRegistry.getOrCreateSession(profileId = "profile-1")

        assertNotNull(session)
        assertNotNull(session.sessionId)
        assertEquals("profile-1", session.profileId)
        assertSame(session, SessionStreamRegistry.sessions[session.sessionId])
    }

    @Test
    fun testSessionCreation_reusesExistingSession() {
        val session1 = SessionStreamRegistry.getOrCreateSession(profileId = "profile-1", sessionId = "custom-session-id")
        val session2 = SessionStreamRegistry.getOrCreateSession(profileId = "profile-1", sessionId = "custom-session-id")

        assertSame(session1, session2)
        assertEquals("custom-session-id", session1.sessionId)
        assertEquals(1, SessionStreamRegistry.sessions.size)
    }

    @Test
    fun testOutputStreamStorage() {
        val session = SessionStreamRegistry.getOrCreateSession(profileId = "profile-1", sessionId = "session-stream-test")
        val outputStream = ByteArrayOutputStream()

        assertNull(session.ptyOutputStream)
        session.ptyOutputStream = outputStream

        assertSame(outputStream, session.ptyOutputStream)
        assertSame(outputStream, SessionStreamRegistry.sessions["session-stream-test"]?.ptyOutputStream)
    }

    @Test
    fun testBufferingWhenUiDetached() = runBlocking {
        val sessionId = "session-buffer-test"
        val session = SessionStreamRegistry.getOrCreateSession(profileId = "profile-1", sessionId = sessionId)
        assertFalse(session.isUiAttached)

        val chunk1 = "Hello, ".toByteArray()
        val chunk2 = "World!\n".toByteArray()

        SessionStreamRegistry.emitOutput(sessionId, chunk1)
        SessionStreamRegistry.emitOutput(sessionId, chunk2)

        // UI is not attached, so chunks should be in outputBuffer
        assertEquals(2, session.outputBuffer.size)

        // Attach UI and drain buffer
        val drained = SessionStreamRegistry.attachUiAndGetBuffer(sessionId)
        assertTrue(session.isUiAttached)
        assertEquals(2, drained.size)
        assertArrayEquals(chunk1, drained[0])
        assertArrayEquals(chunk2, drained[1])

        // Buffer should now be empty
        assertEquals(0, session.outputBuffer.size)

        // Subsequent attach call on empty buffer returns empty list
        val drainedAgain = SessionStreamRegistry.attachUiAndGetBuffer(sessionId)
        assertTrue(drainedAgain.isEmpty())
    }

    @Test
    fun testDirectFlowEmissionWhenUiAttached() = runBlocking {
        val sessionId = "session-flow-test"
        val session = SessionStreamRegistry.getOrCreateSession(profileId = "profile-1", sessionId = sessionId)
        SessionStreamRegistry.attachUiAndGetBuffer(sessionId)
        assertTrue(session.isUiAttached)

        val chunk = "Direct Flow Data".toByteArray()
        var receivedData: Pair<String, ByteArray>? = null

        val job = launch(Dispatchers.Default) {
            receivedData = SessionStreamRegistry.sessionOutput.first()
        }

        // Allow collector to subscribe to the shared flow
        delay(100)

        SessionStreamRegistry.emitOutput(sessionId, chunk)
        withTimeout(2000L) {
            job.join()
        }

        assertNotNull(receivedData)
        assertEquals(sessionId, receivedData?.first)
        assertArrayEquals(chunk, receivedData?.second)
        // Ensure no bytes buffered in queue
        assertTrue(session.outputBuffer.isEmpty())
    }

    @Test
    fun testDetachUi() {
        val sessionId = "session-detach-test"
        val session = SessionStreamRegistry.getOrCreateSession(profileId = "profile-1", sessionId = sessionId)

        SessionStreamRegistry.attachUiAndGetBuffer(sessionId)
        assertTrue(session.isUiAttached)

        SessionStreamRegistry.detachUi(sessionId)
        assertFalse(session.isUiAttached)
    }

    @Test
    fun testClearSession_sanitizesBufferAndRemovesSession() {
        val sessionId = "session-clear-test"
        val session = SessionStreamRegistry.getOrCreateSession(profileId = "profile-1", sessionId = sessionId)
        val sensitiveData1 = byteArrayOf(1, 2, 3, 4, 5)
        val sensitiveData2 = byteArrayOf(6, 7, 8, 9, 10)
        session.outputBuffer.add(sensitiveData1)
        session.outputBuffer.add(sensitiveData2)
        val trackingOutputStream = CloseTrackingOutputStream()
        session.ptyOutputStream = trackingOutputStream
        val mockShell = Proxy.newProxyInstance(
            Shell::class.java.classLoader,
            arrayOf(Shell::class.java),
        ) { _, _, _ -> null } as Shell
        session.sshShell = mockShell
        SessionStreamRegistry.mockTestTranscripts[sessionId] = "mock transcript"

        SessionStreamRegistry.clearSession(sessionId)

        assertNull(SessionStreamRegistry.sessions[sessionId])
        // Sensitive bytes in memory must be wiped with zeros
        assertArrayEquals(byteArrayOf(0, 0, 0, 0, 0), sensitiveData1)
        assertArrayEquals(byteArrayOf(0, 0, 0, 0, 0), sensitiveData2)
        // Stream must be closed and references nulled out
        assertTrue(trackingOutputStream.isClosed)
        assertNull(session.ptyOutputStream)
        assertNull(session.sshShell)
        // Queue must be empty
        assertTrue(session.outputBuffer.isEmpty())
        // Transcripts removed in non-headless test mode
        assertNull(SessionStreamRegistry.mockTestTranscripts[sessionId])
    }

    @Test
    fun testActiveSessionState_sanitize_handlesStreamCloseException() {
        val session = ActiveSessionState(profileId = "profile-err")
        val throwingStream = object : OutputStream() {
            override fun write(b: Int) {}
            override fun close(): Unit = throw IOException("Stream close error")
        }
        session.ptyOutputStream = throwingStream
        session.sanitize()
        assertNull(session.ptyOutputStream)
        assertNull(session.sshShell)
    }

    @Test
    fun testClearSession_preservesTranscriptsInHeadlessTestMode() {
        SessionStreamRegistry.isHeadlessTest = true
        val sessionId = "headless-session"
        SessionStreamRegistry.getOrCreateSession(profileId = "profile-1", sessionId = sessionId)
        SessionStreamRegistry.mockTestTranscripts[sessionId] = "headless-output"

        SessionStreamRegistry.clearSession(sessionId)

        assertNull(SessionStreamRegistry.sessions[sessionId])
        assertEquals("headless-output", SessionStreamRegistry.mockTestTranscripts[sessionId])
    }

    @Test
    fun testNonExistentSessionHandling() = runBlocking {
        // Calling attachUiAndGetBuffer on non-existent session
        val buffer = SessionStreamRegistry.attachUiAndGetBuffer("non-existent")
        assertTrue(buffer.isEmpty())

        // Detaching non-existent session should not throw
        SessionStreamRegistry.detachUi("non-existent")

        // Emitting output for unknown session emits directly to flow
        val testBytes = "test-bytes".toByteArray()
        var emitted: Pair<String, ByteArray>? = null
        val job = launch(Dispatchers.Default) {
            emitted = SessionStreamRegistry.sessionOutput.first()
        }

        delay(100)

        SessionStreamRegistry.emitOutput("unknown-id", testBytes)
        withTimeout(2000L) {
            job.join()
        }

        assertEquals("unknown-id", emitted?.first)
        assertArrayEquals(testBytes, emitted?.second)
    }
}
