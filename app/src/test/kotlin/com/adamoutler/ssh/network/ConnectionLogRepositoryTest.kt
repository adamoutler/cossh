package com.adamoutler.ssh.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConnectionLogRepositoryTest {

    @Before
    fun setUp() {
        ConnectionLogRepository.clearAll()
    }

    @After
    fun tearDown() {
        ConnectionLogRepository.clearAll()
    }

    @Test
    fun testLogEmissionUpdatesStateFlow() {
        val sessionId = "test-session-1"
        val flow = ConnectionLogRepository.getLogFlow(sessionId)
        assertEquals(0, flow.value.size)

        ConnectionLogRepository.log(sessionId, ConnectionMilestone.DNS, "Resolving host")
        assertEquals(1, flow.value.size)
        assertEquals(ConnectionMilestone.DNS, flow.value[0].milestone)
        assertEquals("Resolving host", flow.value[0].message)

        ConnectionLogRepository.log(sessionId, ConnectionMilestone.TCP, "TCP connected", ConnectionLogLevel.INFO)
        assertEquals(2, flow.value.size)
        assertEquals(ConnectionMilestone.TCP, flow.value[1].milestone)
    }

    @Test
    fun testSessionBufferIsolation() {
        val sessionA = "session-A"
        val sessionB = "session-B"

        ConnectionLogRepository.log(sessionA, ConnectionMilestone.DNS, "DNS on A")
        ConnectionLogRepository.log(sessionB, ConnectionMilestone.SSH, "SSH on B")

        val logsA = ConnectionLogRepository.getLogFlow(sessionA).value
        val logsB = ConnectionLogRepository.getLogFlow(sessionB).value

        assertEquals(1, logsA.size)
        assertEquals("DNS on A", logsA[0].message)

        assertEquals(1, logsB.size)
        assertEquals("SSH on B", logsB[0].message)
    }

    @Test
    fun testRingBufferCapEnforced() {
        val sessionId = "capacity-session"
        val totalEntries = 200

        for (i in 1..totalEntries) {
            ConnectionLogRepository.log(
                sessionId = sessionId,
                milestone = ConnectionMilestone.KEX,
                message = "Entry #$i",
            )
        }

        val logs = ConnectionLogRepository.getLogFlow(sessionId).value
        assertEquals(ConnectionLogRepository.MAX_ENTRIES_PER_SESSION, logs.size)
        // First entry in ring buffer should be #51 (200 - 150 + 1)
        assertEquals("Entry #51", logs.first().message)
        assertEquals("Entry #200", logs.last().message)
    }

    @Test
    fun testFormattedTranscriptGeneration() {
        val sessionId = "transcript-session"
        ConnectionLogRepository.log(sessionId, ConnectionMilestone.DNS, "Resolved IP: 1.2.3.4")
        ConnectionLogRepository.log(sessionId, ConnectionMilestone.READY, "Session active")

        val transcript = ConnectionLogRepository.getFormattedTranscript(sessionId)
        assertTrue(transcript.contains("[DNS] Resolved IP: 1.2.3.4"))
        assertTrue(transcript.contains("[READY] Session active"))
    }

    @Test
    fun testClearSessionLogs() {
        val sessionId = "clear-session"
        ConnectionLogRepository.log(sessionId, ConnectionMilestone.AUTH, "Auth successful")
        assertEquals(1, ConnectionLogRepository.getLogFlow(sessionId).value.size)

        ConnectionLogRepository.clearSessionLogs(sessionId)
        assertEquals(0, ConnectionLogRepository.getLogFlow(sessionId).value.size)
        assertEquals("", ConnectionLogRepository.getFormattedTranscript(sessionId))
    }

    @Test
    fun testConcurrentLoggingStress() = runBlocking {
        val sessionId = "concurrent-session"
        val coroutinesCount = 20
        val entriesPerCoroutine = 25

        val jobs = (1..coroutinesCount).map { routineId ->
            async(Dispatchers.Default) {
                for (i in 1..entriesPerCoroutine) {
                    ConnectionLogRepository.log(
                        sessionId = sessionId,
                        milestone = ConnectionMilestone.CHANNEL,
                        message = "Routine $routineId - message $i",
                    )
                }
            }
        }
        jobs.awaitAll()

        val logs = ConnectionLogRepository.getLogFlow(sessionId).value
        assertEquals(ConnectionLogRepository.MAX_ENTRIES_PER_SESSION, logs.size)
    }
}
