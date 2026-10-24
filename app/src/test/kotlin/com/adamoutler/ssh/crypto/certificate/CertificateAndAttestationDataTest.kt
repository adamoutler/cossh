package com.adamoutler.ssh.crypto.certificate

import com.adamoutler.ssh.crypto.SSHKeyGenerator
import com.adamoutler.ssh.crypto.strongbox.AttestationSecurityLevel
import com.adamoutler.ssh.crypto.strongbox.KeyAttestationRecord
import com.adamoutler.ssh.crypto.strongbox.StrongBoxKeyResult
import com.adamoutler.ssh.security.audit.AuditEventType
import com.adamoutler.ssh.security.audit.AuditRecord
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator

class CertificateAndAttestationDataTest {

    @Test
    fun testOpenSshCertificate_dataClassMethods() {
        val kp = SSHKeyGenerator.generateEd25519KeyPair()
        val cert1 = OpenSshCertificate(
            magic = "ssh-ed25519-cert-v01@openssh.com",
            nonce = byteArrayOf(1, 2, 3),
            subjectPublicKey = kp.public,
            serial = 100L,
            certType = OpenSshCertType.USER,
            keyId = "user1",
            validPrincipals = listOf("p1"),
            validAfterSeconds = 10L,
            validBeforeSeconds = 20L,
            criticalOptions = mapOf("k" to "v"),
            extensions = mapOf("e" to "v"),
            caPublicKey = kp.public,
            signatureAlgorithm = "ssh-ed25519",
            rawSignatureBlob = byteArrayOf(4, 5),
            signedBytes = byteArrayOf(6, 7),
        )

        val cert2 = cert1.copy()
        assertEquals(cert1, cert2)
        assertEquals(cert1.hashCode(), cert2.hashCode())
        assertNotNull(cert1.toString())

        // Test difference in byte array fields
        val cert3 = cert1.copy(nonce = byteArrayOf(9, 9))
        assertNotEquals(cert1, cert3)

        val cert4 = cert1.copy(rawSignatureBlob = byteArrayOf(9, 9))
        assertNotEquals(cert1, cert4)

        val cert5 = cert1.copy(signedBytes = byteArrayOf(9, 9))
        assertNotEquals(cert1, cert5)

        val cert6 = cert1.copy(serial = 200L)
        assertNotEquals(cert1, cert6)

        assertFalse(cert1.equals("other type"))
    }

    @Test
    fun testKeyAttestationRecord_dataClassMethods() {
        val record1 = KeyAttestationRecord(
            attestationVersion = 3,
            attestationSecurityLevel = AttestationSecurityLevel.STRONGBOX,
            keymasterVersion = 4,
            keymasterSecurityLevel = AttestationSecurityLevel.STRONGBOX,
            attestationChallenge = byteArrayOf(1, 2),
            uniqueId = byteArrayOf(3, 4),
        )

        val record2 = record1.copy()
        assertEquals(record1, record2)
        assertEquals(record1.hashCode(), record2.hashCode())
        assertNotNull(record1.toString())

        val record3 = record1.copy(attestationChallenge = byteArrayOf(9, 9))
        assertNotEquals(record1, record3)

        val record4 = record1.copy(uniqueId = null)
        assertNotEquals(record1, record4)

        val record5 = record1.copy(attestationVersion = 2)
        assertNotEquals(record1, record5)

        assertFalse(record1.equals(null))
    }

    @Test
    fun testAttestationSecurityLevel_enumMapping() {
        assertEquals(AttestationSecurityLevel.SOFTWARE, AttestationSecurityLevel.fromInt(0))
        assertEquals(AttestationSecurityLevel.TRUSTED_ENVIRONMENT, AttestationSecurityLevel.fromInt(1))
        assertEquals(AttestationSecurityLevel.STRONGBOX, AttestationSecurityLevel.fromInt(2))
        assertEquals(AttestationSecurityLevel.UNKNOWN, AttestationSecurityLevel.fromInt(99))
    }

    @Test
    fun testStrongBoxKeyResult_dataClassMethods() {
        val kp = SSHKeyGenerator.generateEd25519KeyPair()
        val res1 = StrongBoxKeyResult(
            alias = "test-alias",
            keyPair = kp,
            isStrongBoxBacked = true,
            attestationRecord = null,
            publicKeyOpenSsh = "ssh-ed25519 AAAA...",
            certificateChain = null,
        )

        val res2 = res1.copy()
        assertEquals(res1, res2)
        assertEquals(res1.hashCode(), res2.hashCode())
        assertNotNull(res1.toString())
    }

    @Test
    fun testAuditRecord_dataClassMethods() {
        val rec1 = AuditRecord(
            sequenceNumber = 1L,
            timestampMs = 123456789L,
            eventType = AuditEventType.KEY_PAIR_GENERATED,
            payload = mapOf("key" to "value"),
            previousHash = "0000",
            entryHash = "aaaa",
        )

        val rec2 = rec1.copy()
        assertEquals(rec1, rec2)
        assertEquals(rec1.hashCode(), rec2.hashCode())
        assertNotNull(rec1.toString())
    }
}
