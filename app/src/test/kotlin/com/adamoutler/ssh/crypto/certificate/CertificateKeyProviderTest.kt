package com.adamoutler.ssh.crypto.certificate

import com.adamoutler.ssh.crypto.SSHKeyGenerator
import net.schmizz.sshj.common.KeyType
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator

class CertificateKeyProviderTest {

    @Test
    fun testResolveKeyType_allSupportedCurvesAndAlgorithms() {
        fun dummyCert(magic: String): OpenSshCertificate {
            val kp = SSHKeyGenerator.generateEd25519KeyPair()
            return OpenSshCertificate(
                magic = magic,
                nonce = ByteArray(16),
                subjectPublicKey = kp.public,
                serial = 1L,
                certType = OpenSshCertType.USER,
                keyId = "test",
                validPrincipals = emptyList(),
                validAfterSeconds = 0L,
                validBeforeSeconds = 1000L,
                criticalOptions = emptyMap(),
                extensions = emptyMap(),
                caPublicKey = kp.public,
                signatureAlgorithm = "ssh-ed25519",
                rawSignatureBlob = ByteArray(64),
                signedBytes = ByteArray(32),
            )
        }

        assertEquals(KeyType.ED25519_CERT, CertificateKeyProvider.resolveKeyType(dummyCert("ssh-ed25519-cert-v01@openssh.com")))
        assertEquals(KeyType.RSA_CERT, CertificateKeyProvider.resolveKeyType(dummyCert("ssh-rsa-cert-v01@openssh.com")))
        assertEquals(KeyType.RSA_CERT, CertificateKeyProvider.resolveKeyType(dummyCert("rsa-sha2-512-cert-v01@openssh.com")))
        assertEquals(KeyType.ECDSA256_CERT, CertificateKeyProvider.resolveKeyType(dummyCert("ecdsa-sha2-nistp256-cert-v01@openssh.com")))
        assertEquals(KeyType.ECDSA384_CERT, CertificateKeyProvider.resolveKeyType(dummyCert("ecdsa-sha2-nistp384-cert-v01@openssh.com")))
        assertEquals(KeyType.ECDSA521_CERT, CertificateKeyProvider.resolveKeyType(dummyCert("ecdsa-sha2-nistp521-cert-v01@openssh.com")))
        assertEquals(KeyType.ED25519_CERT, CertificateKeyProvider.resolveKeyType(dummyCert("unknown-cert")))
    }

    @Test
    fun testCertificateKeyProvider_ed25519() {
        val subjectKp = SSHKeyGenerator.generateEd25519KeyPair()
        val caKp = SSHKeyGenerator.generateEd25519KeyPair()

        val cert = OpenSshCertificate(
            magic = "ssh-ed25519-cert-v01@openssh.com",
            nonce = ByteArray(32) { it.toByte() },
            subjectPublicKey = subjectKp.public,
            serial = 42L,
            certType = OpenSshCertType.USER,
            keyId = "user@test",
            validPrincipals = listOf("root", "ubuntu"),
            validAfterSeconds = 1600000000L,
            validBeforeSeconds = 1700000000L,
            criticalOptions = mapOf("force-command" to "true"),
            extensions = mapOf("permit-pty" to ""),
            caPublicKey = caKp.public,
            signatureAlgorithm = "ssh-ed25519",
            rawSignatureBlob = ByteArray(64) { 1 },
            signedBytes = ByteArray(128) { 2 },
        )

        val provider = CertificateKeyProvider(cert, subjectKp.private)
        assertEquals(subjectKp.private, provider.getPrivate())
        assertEquals(KeyType.ED25519_CERT, provider.getType())

        val sshjCert = provider.getPublic()
        assertNotNull(sshjCert)
    }

    @Test
    fun testCertificateKeyProvider_rsa_withMaxValidBefore() {
        val kpg = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        val subjectKp = kpg.generateKeyPair()
        val caKp = kpg.generateKeyPair()

        val cert = OpenSshCertificate(
            magic = "ssh-rsa-cert-v01@openssh.com",
            nonce = ByteArray(32),
            subjectPublicKey = subjectKp.public,
            serial = 999L,
            certType = OpenSshCertType.USER,
            keyId = "rsa-user",
            validPrincipals = emptyList(),
            validAfterSeconds = 1000L,
            validBeforeSeconds = -1L, // tests max value branch
            criticalOptions = emptyMap(),
            extensions = emptyMap(),
            caPublicKey = caKp.public,
            signatureAlgorithm = "rsa-sha2-256",
            rawSignatureBlob = ByteArray(256),
            signedBytes = ByteArray(100),
        )

        val provider = CertificateKeyProvider(cert, subjectKp.private)
        assertEquals(KeyType.RSA_CERT, provider.getType())
        assertNotNull(provider.getPublic())
    }
}
