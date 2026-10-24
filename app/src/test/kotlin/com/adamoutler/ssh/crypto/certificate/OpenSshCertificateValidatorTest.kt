package com.adamoutler.ssh.crypto.certificate

import com.adamoutler.ssh.crypto.SSHKeyGenerator
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator

class OpenSshCertificateValidatorTest {

    private fun buildTestCert(
        magic: String = "ssh-ed25519-cert-v01@openssh.com",
        subjectKp: KeyPair = SSHKeyGenerator.generateEd25519KeyPair(),
        caKp: KeyPair = SSHKeyGenerator.generateEd25519KeyPair(),
        certType: OpenSshCertType = OpenSshCertType.HOST,
        keyId: String = "test-cert",
        validPrincipals: List<String> = listOf("host.internal.corp", "*.corp"),
        validAfter: Long = 1000L,
        validBefore: Long = 2000L,
        criticalOptions: Map<String, String> = emptyMap(),
        extensions: Map<String, String> = emptyMap(),
        sigAlg: String = "ssh-ed25519",
    ): OpenSshCertificate {
        val provisional = OpenSshCertificate(
            magic = magic,
            nonce = "1234567890123456".toByteArray(),
            subjectPublicKey = subjectKp.public,
            serial = 1L,
            certType = certType,
            keyId = keyId,
            validPrincipals = validPrincipals,
            validAfterSeconds = validAfter,
            validBeforeSeconds = validBefore,
            criticalOptions = criticalOptions,
            extensions = extensions,
            caPublicKey = caKp.public,
            signatureAlgorithm = sigAlg,
            rawSignatureBlob = ByteArray(0),
            signedBytes = ByteArray(0),
        )

        // Compute signedBytes up to signature
        val writer = WireBufferWriter()
        writer.writeString(provisional.magic)
        writer.writeStringBytes(provisional.nonce)
        when (val pub = provisional.subjectPublicKey) {
            is java.security.interfaces.RSAPublicKey -> {
                writer.writeMpint(pub.publicExponent)
                writer.writeMpint(pub.modulus)
            }

            is java.security.interfaces.ECPublicKey -> {
                writer.writeString("nistp256")
                val pubBlob = OpenSshCertificateParser.encodePublicKeyBlob(pub)
                val r = WireBufferReader(pubBlob)
                r.readString()
                r.readString()
                writer.writeStringBytes(r.readStringBytes())
            }

            else -> {
                writer.writeStringBytes(OpenSshCertificateParser.extractEd25519RawBytes(pub))
            }
        }
        writer.writeUint64(provisional.serial)
        writer.writeUint32(provisional.certType.code.toLong())
        writer.writeString(provisional.keyId)

        val pWriter = WireBufferWriter()
        for (p in provisional.validPrincipals) pWriter.writeString(p)
        writer.writeStringBytes(pWriter.toByteArray())

        writer.writeUint64(provisional.validAfterSeconds)
        writer.writeUint64(provisional.validBeforeSeconds)

        val cWriter = WireBufferWriter()
        for ((k, v) in provisional.criticalOptions) {
            cWriter.writeString(k)
            cWriter.writeString(v)
        }
        writer.writeStringBytes(cWriter.toByteArray())

        val eWriter = WireBufferWriter()
        for ((k, v) in provisional.extensions) {
            eWriter.writeString(k)
            eWriter.writeString(v)
        }
        writer.writeStringBytes(eWriter.toByteArray())

        writer.writeStringBytes(ByteArray(0))
        writer.writeStringBytes(OpenSshCertificateParser.encodePublicKeyBlob(provisional.caPublicKey))

        val signedBytes = writer.toByteArray()
        val certWithSignedBytes = provisional.copy(signedBytes = signedBytes)

        return OpenSshCertificateValidator.signCertificate(
            unsignedCert = certWithSignedBytes,
            caPrivateKey = caKp.private,
            signatureAlgorithm = sigAlg,
        )
    }

    @Test
    fun testValidateTemporal_activeWindow_returnsValid() {
        val cert = buildTestCert(validAfter = 1000L, validBefore = 2000L)
        val result = OpenSshCertificateValidator.validateTemporal(cert, currentEpochSeconds = 1500L)
        assertTrue(result is CertValidationResult.Valid)
    }

    @Test
    fun testValidateTemporal_expired_returnsExpired() {
        val cert = buildTestCert(validAfter = 1000L, validBefore = 2000L)
        val result = OpenSshCertificateValidator.validateTemporal(cert, currentEpochSeconds = 2500L)
        assertTrue(result is CertValidationResult.Expired)
        val expired = result as CertValidationResult.Expired
        assertEquals(2000L, expired.validBeforeSeconds)
        assertEquals(2500L, expired.currentEpochSeconds)
    }

    @Test
    fun testValidateTemporal_notYetValid_returnsNotYetValid() {
        val cert = buildTestCert(validAfter = 1000L, validBefore = 2000L)
        val result = OpenSshCertificateValidator.validateTemporal(cert, currentEpochSeconds = 500L)
        assertTrue(result is CertValidationResult.NotYetValid)
        val notYet = result as CertValidationResult.NotYetValid
        assertEquals(1000L, notYet.validAfterSeconds)
    }

    @Test
    fun testValidateTemporal_foreverInfinity_returnsValid() {
        val cert = buildTestCert(validAfter = 1000L, validBefore = -1L)
        val result = OpenSshCertificateValidator.validateTemporal(cert, currentEpochSeconds = 9999999999L)
        assertTrue(result is CertValidationResult.Valid)
    }

    @Test
    fun testValidatePrincipal_exactAndWildcard() {
        val cert = buildTestCert(validPrincipals = listOf("bastion.corp", "*.internal.corp"))

        // Exact match
        val resExact = OpenSshCertificateValidator.validatePrincipal(cert, "bastion.corp")
        assertTrue(resExact is CertValidationResult.Valid)

        // Wildcard match
        val resWildcard = OpenSshCertificateValidator.validatePrincipal(cert, "db01.internal.corp")
        assertTrue(resWildcard is CertValidationResult.Valid)

        // Mismatch
        val resMismatch = OpenSshCertificateValidator.validatePrincipal(cert, "evil.external.org")
        assertTrue(resMismatch is CertValidationResult.InvalidPrincipal)
    }

    @Test
    fun testValidatePrincipal_emptyPrincipalsOnHostCert_allowsAnyHost() {
        val cert = buildTestCert(certType = OpenSshCertType.HOST, validPrincipals = emptyList())
        val res = OpenSshCertificateValidator.validatePrincipal(cert, "any.domain.com")
        assertTrue(res is CertValidationResult.Valid)
    }

    @Test
    fun testCriticalOptionsTrap_rejectsUnknownOption() {
        val cert = buildTestCert(
            criticalOptions = mapOf("unknown-option@corp" to "strict-enforce"),
        )
        val result = OpenSshCertificateValidator.validateCriticalOptions(cert)
        assertTrue(result is CertValidationResult.UnsupportedCriticalOption)
        val unsupp = result as CertValidationResult.UnsupportedCriticalOption
        assertEquals("unknown-option@corp", unsupp.optionName)
    }

    @Test
    fun testCriticalOptionsTrap_acceptsRecognizedOptions() {
        val cert = buildTestCert(
            criticalOptions = mapOf("force-command" to "/bin/ls", "source-address" to "10.0.0.0/8"),
        )
        val result = OpenSshCertificateValidator.validateCriticalOptions(cert)
        assertTrue(result is CertValidationResult.Valid)
    }

    @Test
    fun testSignatureValidation_ed25519() {
        val cert = buildTestCert(sigAlg = "ssh-ed25519")
        val result = OpenSshCertificateValidator.validateSignature(cert)
        assertTrue(result is CertValidationResult.Valid)
    }

    @Test
    fun testSignatureValidation_rsaSha2() {
        val caKp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val cert = buildTestCert(
            magic = "ssh-rsa-cert-v01@openssh.com",
            caKp = caKp,
            sigAlg = "rsa-sha2-256",
        )
        val result = OpenSshCertificateValidator.validateSignature(cert)
        assertTrue(result is CertValidationResult.Valid)
    }

    @Test
    fun testSignatureValidation_ecdsa() {
        val caKp = SSHKeyGenerator.generateECDSAKeyPair("secp256r1")
        val cert = buildTestCert(
            magic = "ecdsa-sha2-nistp256-cert-v01@openssh.com",
            caKp = caKp,
            sigAlg = "ecdsa-sha2-nistp256",
        )
        val result = OpenSshCertificateValidator.validateSignature(cert)
        assertTrue(result is CertValidationResult.Valid)
    }

    @Test
    fun testSignatureValidation_tamperedSignedBytes_fails() {
        val cert = buildTestCert(sigAlg = "ssh-ed25519")
        val tamperedBytes = cert.signedBytes.clone()
        tamperedBytes[0] = (tamperedBytes[0].toInt() xor 0xFF).toByte()
        val tamperedCert = cert.copy(signedBytes = tamperedBytes)

        val result = OpenSshCertificateValidator.validateSignature(tamperedCert)
        assertTrue(result is CertValidationResult.InvalidSignature)
    }

    @Test
    fun testValidatePipeline_caTrustCheck() {
        val caKp = SSHKeyGenerator.generateEd25519KeyPair()
        val otherCaKp = SSHKeyGenerator.generateEd25519KeyPair()

        val cert = buildTestCert(caKp = caKp, validAfter = 100L, validBefore = 5000L)

        // Untrusted CA
        val unres = OpenSshCertificateValidator.validate(
            cert = cert,
            requestedPrincipal = "host.internal.corp",
            currentEpochSeconds = 1000L,
            trustedCaKeys = listOf(otherCaKp.public),
        )
        assertTrue(unres is CertValidationResult.CaNotTrusted)

        // Trusted CA
        val trustedRes = OpenSshCertificateValidator.validate(
            cert = cert,
            requestedPrincipal = "host.internal.corp",
            currentEpochSeconds = 1000L,
            trustedCaKeys = listOf(caKp.public),
        )
        assertTrue(trustedRes is CertValidationResult.Valid)
    }
}
