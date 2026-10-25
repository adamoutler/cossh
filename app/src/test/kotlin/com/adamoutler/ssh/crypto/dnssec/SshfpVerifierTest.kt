package com.adamoutler.ssh.crypto.dnssec

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

class SshfpVerifierTest {

    @Test
    fun buildDnssecWireQuery_formatsRfc8484AndEdns0DoBit() {
        val hostname = "ssh.example.com"
        val query = SshfpVerifier.buildDnssecWireQuery(hostname)

        assertTrue("Query must be at least 12 header + question + OPT RR", query.size > 28)

        // 1. Header checks
        assertEquals(0x00.toByte(), query[0]) // ID high
        assertEquals(0x00.toByte(), query[1]) // ID low
        assertEquals(0x01.toByte(), query[2]) // RD=1
        assertEquals(0x00.toByte(), query[3])
        assertEquals(0x00.toByte(), query[4]) // QDCOUNT high
        assertEquals(0x01.toByte(), query[5]) // QDCOUNT low = 1
        assertEquals(0x00.toByte(), query[10]) // ARCOUNT high
        assertEquals(0x01.toByte(), query[11]) // ARCOUNT low = 1

        // 2. Question section checks
        // Labels: 3 "ssh" 7 "example" 3 "com" 0
        assertEquals(3.toByte(), query[12])
        assertEquals("ssh", String(query.copyOfRange(13, 16), Charsets.US_ASCII))
        assertEquals(7.toByte(), query[16])
        assertEquals("example", String(query.copyOfRange(17, 24), Charsets.US_ASCII))
        assertEquals(3.toByte(), query[24])
        assertEquals("com", String(query.copyOfRange(25, 28), Charsets.US_ASCII))
        assertEquals(0.toByte(), query[28]) // root terminator

        val qtype = ((query[29].toInt() and 0xFF) shl 8) or (query[30].toInt() and 0xFF)
        assertEquals(SshfpVerifier.RR_TYPE_SSHFP, qtype)

        val qclass = ((query[31].toInt() and 0xFF) shl 8) or (query[32].toInt() and 0xFF)
        assertEquals(SshfpVerifier.CLASS_IN, qclass)

        // 3. EDNS0 OPT RR checks
        // root name (0), type 41 (OPT), udp 4096 (0x1000), flags DO bit (0x8000)
        assertEquals(0.toByte(), query[33]) // root name
        val optType = ((query[34].toInt() and 0xFF) shl 8) or (query[35].toInt() and 0xFF)
        assertEquals(SshfpVerifier.RR_TYPE_OPT, optType)
        val optPayload = ((query[36].toInt() and 0xFF) shl 8) or (query[37].toInt() and 0xFF)
        assertEquals(4096, optPayload)

        // Extended flags: DO bit is in query[40] (0x80)
        assertEquals(0x80.toByte(), query[40])
        assertEquals(0x00.toByte(), query[41])
    }

    @Test
    fun parseDnsResponse_extractsAdBitAndSshfpRecords() {
        val dummyHostKey = byteArrayOf(10, 20, 30, 40, 50)
        val expectedSha256 = MessageDigest.getInstance("SHA-256").digest(dummyHostKey)

        val responseBytes = createDnsResponseBytes(
            adBit = true,
            records = listOf(
                SshfpRecord(
                    algorithm = SshfpVerifier.ALGORITHM_ED25519,
                    fpType = SshfpVerifier.FPTYPE_SHA256,
                    fingerprint = expectedSha256,
                ),
            ),
        )

        val response = SshfpVerifier.parseDnsResponse(responseBytes)
        assertTrue(response.authenticatedData)
        assertEquals(0, response.responseCode)
        assertEquals(1, response.records.size)

        val record = response.records[0]
        assertEquals(SshfpVerifier.ALGORITHM_ED25519, record.algorithm)
        assertEquals(SshfpVerifier.FPTYPE_SHA256, record.fpType)
        assertArrayEquals(expectedSha256, record.fingerprint)
    }

    @Test
    fun verifyHostKeyWire_validDnssecMatch() {
        val dummyHostKey = "ssh-ed25519-dummy-key-bytes".toByteArray(Charsets.UTF_8)
        val sha256 = MessageDigest.getInstance("SHA-256").digest(dummyHostKey)

        val dnsResponse = createDnsResponseBytes(
            adBit = true,
            records = listOf(
                SshfpRecord(
                    algorithm = SshfpVerifier.ALGORITHM_ED25519,
                    fpType = SshfpVerifier.FPTYPE_SHA256,
                    fingerprint = sha256,
                ),
            ),
        )

        val result = SshfpVerifier.verifyHostKeyWire(
            hostname = "bastion.example.com",
            algorithm = SshfpVerifier.ALGORITHM_ED25519,
            hostKeyWireBytes = dummyHostKey,
            dnsResponseBytes = dnsResponse,
        )

        assertTrue(result is SshfpValidationResult.ValidDnssecMatch)
        val match = result as SshfpValidationResult.ValidDnssecMatch
        assertEquals(SshfpVerifier.ALGORITHM_ED25519, match.record.algorithm)
        assertArrayEquals(sha256, match.record.fingerprint)
    }

    @Test
    fun verifyHostKeyWire_returnsDnssecNotAuthenticatedWhenAdBitIsFalse() {
        val dummyHostKey = "dummy-key".toByteArray(Charsets.UTF_8)
        val sha256 = MessageDigest.getInstance("SHA-256").digest(dummyHostKey)

        val dnsResponse = createDnsResponseBytes(
            adBit = false, // AD bit NOT set
            records = listOf(
                SshfpRecord(
                    algorithm = SshfpVerifier.ALGORITHM_ED25519,
                    fpType = SshfpVerifier.FPTYPE_SHA256,
                    fingerprint = sha256,
                ),
            ),
        )

        val result = SshfpVerifier.verifyHostKeyWire(
            hostname = "untrusted.example.com",
            algorithm = SshfpVerifier.ALGORITHM_ED25519,
            hostKeyWireBytes = dummyHostKey,
            dnsResponseBytes = dnsResponse,
        )

        assertEquals(SshfpValidationResult.DnssecNotAuthenticated, result)
    }

    @Test
    fun verifyHostKeyWire_returnsFingerprintMismatchAlert() {
        val actualHostKey = "actual-host-key".toByteArray(Charsets.UTF_8)
        val spoofedFingerprint = ByteArray(32) { 0xAA.toByte() }

        val dnsResponse = createDnsResponseBytes(
            adBit = true,
            records = listOf(
                SshfpRecord(
                    algorithm = SshfpVerifier.ALGORITHM_ED25519,
                    fpType = SshfpVerifier.FPTYPE_SHA256,
                    fingerprint = spoofedFingerprint,
                ),
            ),
        )

        val result = SshfpVerifier.verifyHostKeyWire(
            hostname = "mitm-target.example.com",
            algorithm = SshfpVerifier.ALGORITHM_ED25519,
            hostKeyWireBytes = actualHostKey,
            dnsResponseBytes = dnsResponse,
        )

        assertTrue(result is SshfpValidationResult.FingerprintMismatchAlert)
        val alert = result as SshfpValidationResult.FingerprintMismatchAlert
        assertEquals(1, alert.expectedFingerprints.size)
        assertFalse(alert.actualFingerprint.contentEquals(spoofedFingerprint))
    }

    @Test
    fun verifyHostKeyWire_returnsNoRecordsFound() {
        val actualHostKey = "actual-host-key".toByteArray(Charsets.UTF_8)

        val dnsResponse = createDnsResponseBytes(
            adBit = true,
            records = emptyList(),
        )

        val result = SshfpVerifier.verifyHostKeyWire(
            hostname = "no-sshfp.example.com",
            algorithm = SshfpVerifier.ALGORITHM_ED25519,
            hostKeyWireBytes = actualHostKey,
            dnsResponseBytes = dnsResponse,
        )

        assertEquals(SshfpValidationResult.NoRecordsFound, result)
    }

    private fun createDnsResponseBytes(
        adBit: Boolean,
        records: List<SshfpRecord>,
        rcode: Int = 0,
    ): ByteArray {
        val out = ByteArrayOutputStream()

        // 12-byte header
        // ID: 0x0000
        out.write(0x00)
        out.write(0x00)

        // Flags: QR=1 (response), RD=1, RA=1, AD bit (0x0020)
        var flags = 0x8180 or (rcode and 0x0F)
        if (adBit) {
            flags = flags or 0x0020
        }
        out.write((flags ushr 8) and 0xFF)
        out.write(flags and 0xFF)

        // QDCOUNT: 1
        out.write(0x00)
        out.write(0x01)
        // ANCOUNT
        out.write((records.size ushr 8) and 0xFF)
        out.write(records.size and 0xFF)
        // NSCOUNT: 0
        out.write(0x00)
        out.write(0x00)
        // ARCOUNT: 0
        out.write(0x00)
        out.write(0x00)

        // Question: 3 "ssh" 0, QTYPE 44, QCLASS 1
        out.write(3)
        out.write("ssh".toByteArray(Charsets.US_ASCII))
        out.write(0)
        out.write(0x00)
        out.write(SshfpVerifier.RR_TYPE_SSHFP)
        out.write(0x00)
        out.write(SshfpVerifier.CLASS_IN)

        // Answers
        for (rec in records) {
            // Compressed pointer to question name (0xC00C)
            out.write(0xC0)
            out.write(0x0C)

            // TYPE 44 (SSHFP)
            out.write(0x00)
            out.write(SshfpVerifier.RR_TYPE_SSHFP)
            // CLASS 1 (IN)
            out.write(0x00)
            out.write(SshfpVerifier.CLASS_IN)
            // TTL: 300 seconds
            out.write(0x00)
            out.write(0x00)
            out.write(0x01)
            out.write(0x2C)

            // RDLENGTH = 2 (algo + fptype) + fingerprint.size
            val rdLen = 2 + rec.fingerprint.size
            out.write((rdLen ushr 8) and 0xFF)
            out.write(rdLen and 0xFF)

            // RDATA
            out.write(rec.algorithm)
            out.write(rec.fpType)
            out.write(rec.fingerprint)
        }

        return out.toByteArray()
    }
}
