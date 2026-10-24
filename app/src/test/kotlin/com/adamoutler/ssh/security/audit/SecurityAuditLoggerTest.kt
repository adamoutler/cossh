package com.adamoutler.ssh.security.audit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SecurityAuditLoggerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var logFile: File
    private lateinit var logger: SecurityAuditLogger

    @Before
    fun setUp() {
        logFile = tempFolder.newFile("audit_chain.jsonl")
        logger = SecurityAuditLogger(logFile)
    }

    @Test
    fun testEmptyLogVerification() = runBlocking {
        val result = logger.verifyChainIntegrity()
        assertEquals(AuditIntegrityResult.EmptyLog, result)
    }

    @Test
    fun testSingleEntryAppendAndVerification() = runBlocking {
        val record = logger.appendEvent(
            AuditEventType.SECURITY_INITIALIZED,
            mapOf("device" to "Pixel 9", "version" to "1.0"),
        )

        assertEquals(1L, record.sequenceNumber)
        assertEquals(SecurityAuditLogger.GENESIS_HASH, record.previousHash)

        val result = logger.verifyChainIntegrity()
        assertTrue(result is AuditIntegrityResult.Valid)
        val valid = result as AuditIntegrityResult.Valid
        assertEquals(1L, valid.totalEntries)
        assertEquals(record.entryHash, valid.headHash)
    }

    @Test
    fun testThousandEntryContinuousChainVerification() = runBlocking {
        val count = 1000
        for (i in 1..count) {
            logger.appendEvent(
                AuditEventType.SESSION_CONNECTED,
                mapOf("index" to i.toString(), "action" to "connect"),
            )
        }

        val result = logger.verifyChainIntegrity()
        assertTrue("Verification should be valid for $count entries", result is AuditIntegrityResult.Valid)
        val valid = result as AuditIntegrityResult.Valid
        assertEquals(count.toLong(), valid.totalEntries)
    }

    @Test
    fun testSingleBitPayloadMutationDetected() = runBlocking {
        for (i in 1..10) {
            logger.appendEvent(
                AuditEventType.HOST_KEY_VERIFIED,
                mapOf("entry" to i.toString(), "data" to "authentic_value"),
            )
        }

        // Mutate a single line in the jsonl file
        val lines = logFile.readLines().toMutableList()
        val targetLine = lines[4] // 5th entry
        // Mutate payload value by 1 char
        val mutatedLine = targetLine.replace("authentic_value", "mutated_value")
        lines[4] = mutatedLine
        logFile.writeText(lines.joinToString("\n") + "\n")

        val result = logger.verifyChainIntegrity()
        assertTrue("Single-bit payload tampering must be detected", result is AuditIntegrityResult.Corrupted)
        val corrupted = result as AuditIntegrityResult.Corrupted
        assertEquals(5L, corrupted.sequenceNumber)
        assertTrue(corrupted.reason.contains("tampering", ignoreCase = true))
    }

    @Test
    fun testSequenceDeletionDetected() = runBlocking {
        for (i in 1..10) {
            logger.appendEvent(
                AuditEventType.KEY_PAIR_GENERATED,
                mapOf("seq" to i.toString()),
            )
        }

        // Delete entry sequence 5
        val lines = logFile.readLines().toMutableList()
        lines.removeAt(4)
        logFile.writeText(lines.joinToString("\n") + "\n")

        val result = logger.verifyChainIntegrity()
        assertTrue("Sequence deletion must be detected", result is AuditIntegrityResult.Corrupted)
        val corrupted = result as AuditIntegrityResult.Corrupted
        // Entry at index 4 was originally sequence 6; it now follows sequence 4
        assertEquals(6L, corrupted.sequenceNumber)
    }

    @Test
    fun testEntryReorderingDetected() = runBlocking {
        for (i in 1..10) {
            logger.appendEvent(
                AuditEventType.APP_LOCK_UNLOCKED,
                mapOf("attempt" to i.toString()),
            )
        }

        // Swap entry 3 and entry 4
        val lines = logFile.readLines().toMutableList()
        val temp = lines[2]
        lines[2] = lines[3]
        lines[3] = temp
        logFile.writeText(lines.joinToString("\n") + "\n")

        val result = logger.verifyChainIntegrity()
        assertTrue("Entry reordering must be detected", result is AuditIntegrityResult.Corrupted)
    }

    @Test
    fun testFiftyConcurrentCoroutinesStressTest() = runBlocking {
        val concurrency = 50
        val entriesPerCoroutine = 20
        val totalExpected = (concurrency * entriesPerCoroutine).toLong()

        val jobs = (1..concurrency).map { workerId ->
            async(Dispatchers.IO) {
                for (step in 1..entriesPerCoroutine) {
                    logger.appendEvent(
                        AuditEventType.SESSION_CONNECTED,
                        mapOf(
                            "worker" to workerId.toString(),
                            "step" to step.toString(),
                        ),
                    )
                }
            }
        }

        jobs.awaitAll()

        val records = logger.getAllRecords()
        assertEquals(totalExpected, records.size.toLong())

        val result = logger.verifyChainIntegrity()
        assertTrue("Concurrent log stream must maintain 100% chain integrity", result is AuditIntegrityResult.Valid)
        val valid = result as AuditIntegrityResult.Valid
        assertEquals(totalExpected, valid.totalEntries)
    }

    @Test
    fun testHeadAnchorRollbackDetected() = runBlocking {
        for (i in 1..10) {
            logger.appendEvent(
                AuditEventType.HOST_KEY_VERIFIED,
                mapOf("step" to i.toString()),
            )
        }

        val allRecords = logger.getAllRecords()
        val originalHeadSeq = allRecords.last().sequenceNumber
        val originalHeadHash = allRecords.last().entryHash

        // Truncate the file to 5 entries (simulating rollback attack)
        val lines = logFile.readLines().take(5)
        logFile.writeText(lines.joinToString("\n") + "\n")

        val result = logger.verifyChainIntegrity(
            expectedHeadSequence = originalHeadSeq,
            expectedHeadHash = originalHeadHash,
        )

        assertTrue("Rollback attack against head anchor must be flagged as Corrupted", result is AuditIntegrityResult.Corrupted)
        val corrupted = result as AuditIntegrityResult.Corrupted
        assertTrue(corrupted.reason.contains("rollback", ignoreCase = true) || corrupted.reason.contains("sequence", ignoreCase = true))
    }
}
