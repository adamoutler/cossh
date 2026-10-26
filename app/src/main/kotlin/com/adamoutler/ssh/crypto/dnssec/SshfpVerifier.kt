package com.adamoutler.ssh.crypto.dnssec

import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * DNSSEC-authenticated SSHFP (SSH Fingerprint, RFC 4255) verifier for Zero-TOFU host authentication.
 *
 * Implements:
 * 1. RFC 8484 DNS wire format query builder with EDNS0 OPT RR and `DO` (DNSSEC OK) bit.
 * 2. DNS wire format response parser extracting the `AD` (Authenticated Data) bit and SSHFP records (Type 44).
 * 3. Cryptographic host key verifier validating raw host key bytes against authenticated DNSSEC digests.
 */
object SshfpVerifier {

    const val RR_TYPE_SSHFP = 44
    const val RR_TYPE_OPT = 41
    const val CLASS_IN = 1

    // SSHFP Algorithms (RFC 4255, RFC 6594, RFC 7479)
    const val ALGORITHM_RSA = 1
    const val ALGORITHM_DSA = 2
    const val ALGORITHM_ECDSA = 3
    const val ALGORITHM_ED25519 = 4
    const val ALGORITHM_XMSS = 5
    const val ALGORITHM_ED448 = 6

    // SSHFP Fingerprint Types (RFC 4255, RFC 6594)
    const val FPTYPE_SHA1 = 1
    const val FPTYPE_SHA256 = 2

    /**
     * Builds an RFC 8484 DNS binary wire format query for RR type 44 (SSHFP)
     * including an EDNS0 OPT pseudo-record with the DNSSEC OK (DO) bit set to 0x8000.
     */
    fun buildDnssecWireQuery(hostname: String): ByteArray {
        val out = ByteArrayOutputStream()

        // 12-byte DNS Header
        // ID: 0x0000 (standard for DoH)
        out.write(0x00)
        out.write(0x00)
        // Flags: RD=1 (Recursion Desired = 0x0100)
        out.write(0x01)
        out.write(0x00)
        // QDCOUNT: 1
        out.write(0x00)
        out.write(0x01)
        // ANCOUNT: 0
        out.write(0x00)
        out.write(0x00)
        // NSCOUNT: 0
        out.write(0x00)
        out.write(0x00)
        // ARCOUNT: 1 (EDNS0 OPT RR)
        out.write(0x00)
        out.write(0x01)

        // Question Section
        encodeDomainName(hostname, out)
        // QTYPE: 44 (SSHFP)
        out.write(0x00)
        out.write(RR_TYPE_SSHFP)
        // QCLASS: 1 (IN)
        out.write(0x00)
        out.write(CLASS_IN)

        // Additional Section: EDNS0 OPT RR (RFC 6891)
        // NAME: root (0x00)
        out.write(0x00)
        // TYPE: 41 (OPT)
        out.write(0x00)
        out.write(RR_TYPE_OPT)
        // CLASS: UDP payload size (4096 = 0x1000)
        out.write(0x10)
        out.write(0x00)
        // Extended RCODE: 0x00, Version: 0x00
        out.write(0x00)
        out.write(0x00)
        // Flags: DO bit set (0x8000 -> 0x80, 0x00)
        out.write(0x80)
        out.write(0x00)
        // RDLENGTH: 0 (0x0000)
        out.write(0x00)
        out.write(0x00)

        return out.toByteArray()
    }

    /**
     * Parses a DNS binary wire response extracting the AD bit and SSHFP records.
     */
    fun parseDnsResponse(responseBytes: ByteArray): DnsSshfpResponse {
        require(responseBytes.size >= 12) { "DNS response too short: ${responseBytes.size} bytes" }

        val flags = ((responseBytes[2].toInt() and 0xFF) shl 8) or (responseBytes[3].toInt() and 0xFF)
        val authenticatedData = (flags and 0x0020) != 0 // Bit 10 (0x0020) is AD bit
        val rcode = flags and 0x000F

        val qdCount = ((responseBytes[4].toInt() and 0xFF) shl 8) or (responseBytes[5].toInt() and 0xFF)
        val anCount = ((responseBytes[6].toInt() and 0xFF) shl 8) or (responseBytes[7].toInt() and 0xFF)

        var offset = 12

        // Skip Question Section
        for (i in 0 until qdCount) {
            offset = skipDomainName(responseBytes, offset)
            offset += 4 // QTYPE (2) + QCLASS (2)
            require(offset <= responseBytes.size) { "Malformed question section" }
        }

        // Parse Answer Section
        val records = mutableListOf<SshfpRecord>()
        for (i in 0 until anCount) {
            offset = skipDomainName(responseBytes, offset)
            require(offset + 10 <= responseBytes.size) { "Malformed answer record header" }

            val type = ((responseBytes[offset].toInt() and 0xFF) shl 8) or (responseBytes[offset + 1].toInt() and 0xFF)
            val rrClass = ((responseBytes[offset + 2].toInt() and 0xFF) shl 8) or (responseBytes[offset + 3].toInt() and 0xFF)
            // ttl is 4 bytes (offset + 4 .. offset + 7)
            val rdLength = ((responseBytes[offset + 8].toInt() and 0xFF) shl 8) or (responseBytes[offset + 9].toInt() and 0xFF)
            offset += 10

            require(offset + rdLength <= responseBytes.size) { "RDATA exceeds packet boundary" }

            if (type == RR_TYPE_SSHFP && rrClass == CLASS_IN && rdLength >= 2) {
                val algorithm = responseBytes[offset].toInt() and 0xFF
                val fpType = responseBytes[offset + 1].toInt() and 0xFF
                val fingerprint = responseBytes.copyOfRange(offset + 2, offset + rdLength)
                records.add(SshfpRecord(algorithm = algorithm, fpType = fpType, fingerprint = fingerprint))
            }

            offset += rdLength
        }

        return DnsSshfpResponse(
            authenticatedData = authenticatedData,
            responseCode = rcode,
            records = records,
        )
    }

    /**
     * Verifies the presented raw host key wire bytes against authenticated DNSSEC SSHFP records.
     *
     * @param hostname Server hostname
     * @param algorithm SSHFP algorithm identifier (e.g. [ALGORITHM_ED25519], [ALGORITHM_RSA], [ALGORITHM_ECDSA])
     * @param hostKeyWireBytes Raw wire bytes of the SSH host key (e.g. `ssh-ed25519` key blob)
     * @param dnsResponseBytes Raw wire DNS response bytes from DoH resolver
     */
    fun verifyHostKeyWire(
        hostname: String,
        algorithm: Int,
        hostKeyWireBytes: ByteArray,
        dnsResponseBytes: ByteArray,
    ): SshfpValidationResult {
        require(hostname.isNotBlank()) { "Hostname must not be blank" }
        val response = parseDnsResponse(dnsResponseBytes)

        if (!response.authenticatedData) {
            return SshfpValidationResult.DnssecNotAuthenticated
        }

        val matchingRecords = response.records.filter { it.algorithm == algorithm }
        if (matchingRecords.isEmpty()) {
            return SshfpValidationResult.NoRecordsFound
        }

        val sha256 = MessageDigest.getInstance("SHA-256").digest(hostKeyWireBytes)
        val sha1 = MessageDigest.getInstance("SHA-1").digest(hostKeyWireBytes)

        val matched = matchingRecords.firstOrNull { record ->
            when (record.fpType) {
                FPTYPE_SHA256 -> record.fingerprint.contentEquals(sha256)
                FPTYPE_SHA1 -> record.fingerprint.contentEquals(sha1)
                else -> false
            }
        }

        return if (matched != null) {
            SshfpValidationResult.ValidDnssecMatch(matched)
        } else {
            SshfpValidationResult.FingerprintMismatchAlert(
                expectedFingerprints = matchingRecords,
                actualFingerprint = sha256,
                actualFingerprintHex = sha256.joinToString("") { "%02x".format(it) },
            )
        }
    }

    private fun encodeDomainName(name: String, out: ByteArrayOutputStream) {
        val trimmed = name.trim().trimEnd('.')
        if (trimmed.isEmpty()) {
            out.write(0)
            return
        }
        val labels = trimmed.split(".")
        for (label in labels) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            require(bytes.size in 1..63) { "Invalid label length: ${bytes.size}" }
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0) // Null root terminator
    }

    private fun skipDomainName(bytes: ByteArray, startOffset: Int): Int {
        var offset = startOffset
        while (offset < bytes.size) {
            val len = bytes[offset].toInt() and 0xFF
            if (len == 0) {
                return offset + 1
            } else if ((len and 0xC0) == 0xC0) {
                // Compression pointer (2 bytes)
                return offset + 2
            } else {
                offset += 1 + len
            }
        }
        throw IllegalArgumentException("Unterminated domain name at offset $startOffset")
    }
}

/**
 * Parsed SSHFP record (RFC 4255).
 */
data class SshfpRecord(
    val algorithm: Int,
    val fpType: Int,
    val fingerprint: ByteArray,
) {
    val fingerprintHex: String
        get() = fingerprint.joinToString("") { "%02x".format(it) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SshfpRecord) return false
        return algorithm == other.algorithm && fpType == other.fpType && fingerprint.contentEquals(other.fingerprint)
    }

    override fun hashCode(): Int = 31 * (31 * algorithm + fpType) + fingerprint.contentHashCode()
}

/**
 * Parsed DNS wire response containing authenticated data status and SSHFP records.
 */
data class DnsSshfpResponse(
    val authenticatedData: Boolean,
    val responseCode: Int,
    val records: List<SshfpRecord>,
)

/**
 * Result of DNSSEC SSHFP validation.
 */
sealed class SshfpValidationResult {
    data class ValidDnssecMatch(val record: SshfpRecord) : SshfpValidationResult()

    data class FingerprintMismatchAlert(
        val expectedFingerprints: List<SshfpRecord>,
        val actualFingerprint: ByteArray,
        val actualFingerprintHex: String,
    ) : SshfpValidationResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is FingerprintMismatchAlert) return false
            return expectedFingerprints == other.expectedFingerprints &&
                actualFingerprint.contentEquals(other.actualFingerprint) &&
                actualFingerprintHex == other.actualFingerprintHex
        }

        override fun hashCode(): Int = 31 * (31 * expectedFingerprints.hashCode() + actualFingerprint.contentHashCode()) + actualFingerprintHex.hashCode()
    }

    object DnssecNotAuthenticated : SshfpValidationResult()

    object NoRecordsFound : SshfpValidationResult()
}
