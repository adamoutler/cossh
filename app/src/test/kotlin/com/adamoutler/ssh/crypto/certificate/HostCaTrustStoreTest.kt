package com.adamoutler.ssh.crypto.certificate

import com.adamoutler.ssh.crypto.KnownHostsManager
import com.adamoutler.ssh.crypto.SSHKeyGenerator
import com.adamoutler.ssh.network.TofuHostKeyVerifier
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Base64

class HostCaTrustStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var knownHostsFile: File
    private lateinit var manager: KnownHostsManager

    @Before
    fun setUp() {
        knownHostsFile = tempFolder.newFile("test_known_hosts")
        manager = KnownHostsManager(knownHostsFile)
    }

    @Test
    fun testParseCertAuthorityEntry() {
        val caKp = SSHKeyGenerator.generateEd25519KeyPair()
        val caKeyBlob = OpenSshCertificateParser.encodePublicKeyBlob(caKp.public)
        val caB64 = Base64.getEncoder().encodeToString(caKeyBlob)

        val line = "@cert-authority *.internal.corp,bastion.corp ssh-ed25519 $caB64 corp-ca"
        knownHostsFile.writeText("$line\n")

        val entries = manager.getEntries()
        assertEquals(1, entries.size)
        val entry = entries[0]
        assertTrue(entry.isCertAuthority)
        assertEquals("*.internal.corp,bastion.corp", entry.rawHostPattern)
        assertEquals("ssh-ed25519", entry.keyType)
        assertEquals(caB64, entry.publicKeyBase64)
        assertEquals("corp-ca", entry.comment)
        assertEquals("*.internal.corp,bastion.corp (CA)", entry.displayHost)
    }

    @Test
    fun testFindTrustedCaForHost_withWildcards() {
        val caKp1 = SSHKeyGenerator.generateEd25519KeyPair()
        val caB64First = Base64.getEncoder().encodeToString(OpenSshCertificateParser.encodePublicKeyBlob(caKp1.public))

        val caKp2 = SSHKeyGenerator.generateEd25519KeyPair()
        val caB64Second = Base64.getEncoder().encodeToString(OpenSshCertificateParser.encodePublicKeyBlob(caKp2.public))

        manager.saveCertAuthority("*.internal.corp", "ssh-ed25519", caB64First, "internal-ca")
        manager.saveCertAuthority("bastion.corp", "ssh-ed25519", caB64Second, "bastion-ca")

        // Should match *.internal.corp
        val casForServer = manager.findTrustedCaForHost("server01.internal.corp")
        assertEquals(1, casForServer.size)
        assertArrayEquals(caKp1.public.encoded, casForServer[0].encoded)

        // Should match bastion.corp
        val casForBastion = manager.findTrustedCaForHost("bastion.corp")
        assertEquals(1, casForBastion.size)
        assertArrayEquals(caKp2.public.encoded, casForBastion[0].encoded)

        // Should not match external
        val casForExternal = manager.findTrustedCaForHost("other.org")
        assertTrue(casForExternal.isEmpty())
    }

    @Test
    fun testMatchesCaHostPattern_negationPattern() {
        val pattern = "!rogue.corp,*.corp"

        assertFalse(manager.matchesCaHostPattern(pattern, "rogue.corp", 22))
        assertTrue(manager.matchesCaHostPattern(pattern, "secure.corp", 22))
        assertTrue(manager.matchesCaHostPattern(pattern, "api.corp", 22))
        assertFalse(manager.matchesCaHostPattern(pattern, "other.com", 22))
    }

    @Test
    fun testHashAllPlaintextHosts_preservesCertAuthorityLines() {
        val caKp = SSHKeyGenerator.generateEd25519KeyPair()
        val caB64 = Base64.getEncoder().encodeToString(OpenSshCertificateParser.encodePublicKeyBlob(caKp.public))

        // Write 1 plain host and 1 @cert-authority
        knownHostsFile.writeText(
            "plain-host.corp ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIG... \n" +
                "@cert-authority *.corp ssh-ed25519 $caB64 ca-admin\n",
        )

        val converted = manager.hashAllPlaintextHosts()
        assertEquals(1, converted)

        val lines = knownHostsFile.readLines().filter { it.isNotBlank() }
        assertEquals(2, lines.size)

        // First line is hashed
        assertTrue(lines[0].startsWith("|1|"))

        // Second line is untouched @cert-authority
        assertTrue(lines[1].startsWith("@cert-authority *.corp ssh-ed25519"))
    }

    @Test
    fun testTofuHostKeyVerifier_autoAcceptsHostCertificateWithTrustedCa() {
        val caKp = SSHKeyGenerator.generateEd25519KeyPair()
        val caB64 = Base64.getEncoder().encodeToString(OpenSshCertificateParser.encodePublicKeyBlob(caKp.public))
        manager.saveCertAuthority("*.internal.corp", "ssh-ed25519", caB64, "trusted-host-ca")

        val serverHostKp = SSHKeyGenerator.generateEd25519KeyPair()

        // Build valid host certificate signed by caKp
        val unsignedCert = OpenSshCertificate(
            magic = "ssh-ed25519-cert-v01@openssh.com",
            nonce = "1234567890123456".toByteArray(),
            subjectPublicKey = serverHostKp.public,
            serial = 100L,
            certType = OpenSshCertType.HOST,
            keyId = "server01-cert",
            validPrincipals = listOf("*.internal.corp"),
            validAfterSeconds = 0L,
            validBeforeSeconds = -1L, // Never expires
            criticalOptions = emptyMap(),
            extensions = emptyMap(),
            caPublicKey = caKp.public,
            signatureAlgorithm = "ssh-ed25519",
            rawSignatureBlob = ByteArray(0),
            signedBytes = ByteArray(0),
        )

        val writer = WireBufferWriter()
        writer.writeString(unsignedCert.magic)
        writer.writeStringBytes(unsignedCert.nonce)
        writer.writeStringBytes(OpenSshCertificateParser.extractEd25519RawBytes(unsignedCert.subjectPublicKey))
        writer.writeUint64(unsignedCert.serial)
        writer.writeUint32(unsignedCert.certType.code.toLong())
        writer.writeString(unsignedCert.keyId)
        val pWriter = WireBufferWriter()
        for (p in unsignedCert.validPrincipals) pWriter.writeString(p)
        writer.writeStringBytes(pWriter.toByteArray())
        writer.writeUint64(unsignedCert.validAfterSeconds)
        writer.writeUint64(unsignedCert.validBeforeSeconds)
        writer.writeStringBytes(ByteArray(0)) // crit
        writer.writeStringBytes(ByteArray(0)) // ext
        writer.writeStringBytes(ByteArray(0)) // reserved
        writer.writeStringBytes(OpenSshCertificateParser.encodePublicKeyBlob(unsignedCert.caPublicKey))

        val signedBytes = writer.toByteArray()
        val signedCert = OpenSshCertificateValidator.signCertificate(
            unsignedCert = unsignedCert.copy(signedBytes = signedBytes),
            caPrivateKey = caKp.private,
            signatureAlgorithm = "ssh-ed25519",
        )

        // Convert to SSHJ certificate Public Key
        val sshjCertKey = CertificateKeyProvider.toSshjCertificate(signedCert)

        val verifier = TofuHostKeyVerifier(knownHostsFile, isAutonomousReconnect = true)
        val verified = verifier.verify("api.internal.corp", 22, sshjCertKey)

        // Verified automatically via trusted CA without TOFU prompt!
        assertTrue(verified)
    }
}
