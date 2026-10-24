package com.adamoutler.ssh.crypto.certificate

import com.adamoutler.ssh.crypto.SSHKeyGenerator
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.util.Base64

class OpenSshCertificateParserTest {

    @Test
    fun testEd25519CertificateRoundTrip() {
        val subjectKp = SSHKeyGenerator.generateEd25519KeyPair()
        val caKp = SSHKeyGenerator.generateEd25519KeyPair()

        val cert = createTestCertificate(
            magic = "ssh-ed25519-cert-v01@openssh.com",
            subjectKp = subjectKp,
            caKp = caKp,
            certType = OpenSshCertType.USER,
            keyId = "dev@corp.internal",
            validPrincipals = listOf("developer", "admin"),
            validAfter = 1700000000L,
            validBefore = 1800000000L,
            criticalOptions = mapOf("force-command" to "/usr/bin/restricted-shell"),
            extensions = mapOf("permit-pty" to ""),
            sigAlg = "ssh-ed25519",
        )

        val wireBytes = OpenSshCertificateParser.serializeWireBytes(cert)
        assertNotNull(wireBytes)
        assertTrue(wireBytes.isNotEmpty())

        val parsed = OpenSshCertificateParser.parseWireBytes(wireBytes)
        assertEquals(cert.magic, parsed.magic)
        assertArrayEquals(cert.nonce, parsed.nonce)
        assertEquals(cert.serial, parsed.serial)
        assertEquals(cert.certType, parsed.certType)
        assertEquals(cert.keyId, parsed.keyId)
        assertEquals(cert.validPrincipals, parsed.validPrincipals)
        assertEquals(cert.validAfterSeconds, parsed.validAfterSeconds)
        assertEquals(cert.validBeforeSeconds, parsed.validBeforeSeconds)
        assertEquals(cert.criticalOptions, parsed.criticalOptions)
        assertEquals(cert.extensions, parsed.extensions)
        assertEquals(cert.signatureAlgorithm, parsed.signatureAlgorithm)
        assertArrayEquals(cert.rawSignatureBlob, parsed.rawSignatureBlob)
        assertArrayEquals(cert.signedBytes, parsed.signedBytes)
    }

    @Test
    fun testRsaCertificateRoundTrip() {
        val subjectKp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val caKp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

        val cert = createTestCertificate(
            magic = "ssh-rsa-cert-v01@openssh.com",
            subjectKp = subjectKp,
            caKp = caKp,
            certType = OpenSshCertType.HOST,
            keyId = "host.internal.corp",
            validPrincipals = listOf("host.internal.corp", "*.internal.corp"),
            validAfter = 1000L,
            validBefore = 2000L,
            criticalOptions = emptyMap(),
            extensions = emptyMap(),
            sigAlg = "rsa-sha2-256",
        )

        val wireBytes = OpenSshCertificateParser.serializeWireBytes(cert)
        val parsed = OpenSshCertificateParser.parseWireBytes(wireBytes)

        assertEquals("ssh-rsa-cert-v01@openssh.com", parsed.magic)
        assertEquals(OpenSshCertType.HOST, parsed.certType)
        assertEquals("host.internal.corp", parsed.keyId)
        assertEquals(listOf("host.internal.corp", "*.internal.corp"), parsed.validPrincipals)
    }

    @Test
    fun testEcdsaCertificateRoundTrip() {
        val subjectKp = SSHKeyGenerator.generateECDSAKeyPair("secp256r1")
        val caKp = SSHKeyGenerator.generateECDSAKeyPair("secp256r1")

        val cert = createTestCertificate(
            magic = "ecdsa-sha2-nistp256-cert-v01@openssh.com",
            subjectKp = subjectKp,
            caKp = caKp,
            certType = OpenSshCertType.USER,
            keyId = "ec-user",
            validPrincipals = listOf("ec-user"),
            validAfter = 500L,
            validBefore = -1L, // Infinity
            criticalOptions = emptyMap(),
            extensions = emptyMap(),
            sigAlg = "ecdsa-sha2-nistp256",
        )

        val wireBytes = OpenSshCertificateParser.serializeWireBytes(cert)
        val parsed = OpenSshCertificateParser.parseWireBytes(wireBytes)

        assertEquals("ecdsa-sha2-nistp256-cert-v01@openssh.com", parsed.magic)
        assertEquals(OpenSshCertType.USER, parsed.certType)
        assertEquals(-1L, parsed.validBeforeSeconds)
    }

    @Test
    fun testParseAuthorizedLineFormat() {
        val subjectKp = SSHKeyGenerator.generateEd25519KeyPair()
        val caKp = SSHKeyGenerator.generateEd25519KeyPair()

        val cert = createTestCertificate(
            magic = "ssh-ed25519-cert-v01@openssh.com",
            subjectKp = subjectKp,
            caKp = caKp,
            certType = OpenSshCertType.HOST,
            keyId = "test-host",
            validPrincipals = listOf("node.corp"),
            validAfter = 100L,
            validBefore = 200L,
            criticalOptions = emptyMap(),
            extensions = emptyMap(),
            sigAlg = "ssh-ed25519",
        )

        val line = OpenSshCertificateParser.encodeAuthorizedLine(cert, "ca-host-comment")
        assertTrue(line.startsWith("ssh-ed25519-cert-v01@openssh.com "))
        assertTrue(line.endsWith(" ca-host-comment"))

        val parsed = OpenSshCertificateParser.parse(line)
        assertEquals("test-host", parsed.keyId)
        assertEquals(listOf("node.corp"), parsed.validPrincipals)
    }

    @Test
    fun testParseKnownHostsCertAuthorityLine() {
        val subjectKp = SSHKeyGenerator.generateEd25519KeyPair()
        val caKp = SSHKeyGenerator.generateEd25519KeyPair()

        val cert = createTestCertificate(
            magic = "ssh-ed25519-cert-v01@openssh.com",
            subjectKp = subjectKp,
            caKp = caKp,
            certType = OpenSshCertType.HOST,
            keyId = "host-ca",
            validPrincipals = listOf("*.corp"),
            validAfter = 0L,
            validBefore = -1L,
            criticalOptions = emptyMap(),
            extensions = emptyMap(),
            sigAlg = "ssh-ed25519",
        )

        val bytes = OpenSshCertificateParser.serializeWireBytes(cert)
        val b64 = Base64.getEncoder().encodeToString(bytes)
        val line = "@cert-authority *.corp $b64 admin@corp"

        val parsed = OpenSshCertificateParser.parse(line)
        assertEquals("host-ca", parsed.keyId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testParseCorruptedWireBytes_throwsException() {
        val invalidBytes = byteArrayOf(0x00, 0x00, 0x00, 0x05, 0x73, 0x73, 0x68)
        OpenSshCertificateParser.parseWireBytes(invalidBytes)
    }

    private fun createTestCertificate(
        magic: String,
        subjectKp: java.security.KeyPair,
        caKp: java.security.KeyPair,
        certType: OpenSshCertType,
        keyId: String,
        validPrincipals: List<String>,
        validAfter: Long,
        validBefore: Long,
        criticalOptions: Map<String, String>,
        extensions: Map<String, String>,
        sigAlg: String,
    ): OpenSshCertificate {
        val nonce = "nonce12345678901".toByteArray()

        // Construct provisional cert to compute signedBytes
        val provisional = OpenSshCertificate(
            magic = magic,
            nonce = nonce,
            subjectPublicKey = subjectKp.public,
            serial = 42L,
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

        // Serialize unsigned portion to get signedBytes
        val writer = WireBufferWriter()
        writer.writeString(provisional.magic)
        writer.writeStringBytes(provisional.nonce)

        when (val pub = provisional.subjectPublicKey) {
            is java.security.interfaces.RSAPublicKey -> {
                writer.writeMpint(pub.publicExponent)
                writer.writeMpint(pub.modulus)
            }

            is java.security.interfaces.ECPublicKey -> {
                val curveName = if (magic.contains("nistp256")) "nistp256" else "nistp256"
                writer.writeString(curveName)
                writer.writeStringBytes(
                    OpenSshCertificateParser.encodePublicKeyBlob(pub).let {
                        // Extract point from encoded blob
                        val r = WireBufferReader(it)
                        r.readString() // keyType
                        r.readString() // curve
                        r.readStringBytes() // point
                    },
                )
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

        val critWriter = WireBufferWriter()
        for ((k, v) in provisional.criticalOptions) {
            critWriter.writeString(k)
            critWriter.writeString(v)
        }
        writer.writeStringBytes(critWriter.toByteArray())

        val extWriter = WireBufferWriter()
        for ((k, v) in provisional.extensions) {
            extWriter.writeString(k)
            extWriter.writeString(v)
        }
        writer.writeStringBytes(extWriter.toByteArray())

        writer.writeStringBytes(ByteArray(0)) // reserved
        writer.writeStringBytes(OpenSshCertificateParser.encodePublicKeyBlob(provisional.caPublicKey))

        val signedBytes = writer.toByteArray()
        val unsignedCertWithSignedBytes = provisional.copy(signedBytes = signedBytes)

        return OpenSshCertificateValidator.signCertificate(
            unsignedCert = unsignedCertWithSignedBytes,
            caPrivateKey = caKp.private,
            signatureAlgorithm = sigAlg,
        )
    }
}
