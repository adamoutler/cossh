package com.adamoutler.ssh.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class KnownHostsBulkPorterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var knownHostsFile: File
    private lateinit var manager: KnownHostsManager

    private val sampleEd25519Key1 = "AAAAC3NzaC1lZDI1NTE5AAAAIGo4wN/jWvjZ4mN+0pB4B9wT7Hq9q2r1x8kL8p8kL8p8"
    private val sampleEd25519Key2 = "AAAAC3NzaC1lZDI1NTE5AAAAINewKeyDataForReplacement1234567890123"
    private val sampleRsaKey1 = "AAAAB3NzaC1yc2EAAAADAQABAAABAQC3r3r3r3r3r3r3r3r3r3r3r3r3r3r3r3r3r3r3"
    private val sampleRsaKey2 = "AAAAB3NzaC1yc2EAAAADAQABAAABAQD4s4s4s4s4s4s4s4s4s4s4s4s4s4s4s4s4s4s4"
    private val sampleEcdsaKey = "AAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBN5y9x0123456789"

    @Before
    fun setup() {
        knownHostsFile = tempFolder.newFile("ssh_known_hosts")
        manager = KnownHostsManager(knownHostsFile)
    }

    @Test
    fun testValidatePublicKeyBlob_validBlobs() {
        assertTrue(KnownHostsBulkPorter.validatePublicKeyBlob("ssh-ed25519", sampleEd25519Key1))
        assertTrue(KnownHostsBulkPorter.validatePublicKeyBlob("ssh-ed25519", sampleEd25519Key2))
        assertTrue(KnownHostsBulkPorter.validatePublicKeyBlob("ssh-rsa", sampleRsaKey1))
        assertTrue(KnownHostsBulkPorter.validatePublicKeyBlob("ssh-rsa", sampleRsaKey2))
        assertTrue(KnownHostsBulkPorter.validatePublicKeyBlob("ecdsa-sha2-nistp256", sampleEcdsaKey))
    }

    @Test
    fun testValidatePublicKeyBlob_mismatchedKeyType() {
        assertFalse(KnownHostsBulkPorter.validatePublicKeyBlob("ssh-rsa", sampleEd25519Key1))
        assertFalse(KnownHostsBulkPorter.validatePublicKeyBlob("ssh-ed25519", sampleRsaKey1))
        assertFalse(KnownHostsBulkPorter.validatePublicKeyBlob("ecdsa-sha2-nistp384", sampleEcdsaKey))
    }

    @Test
    fun testValidatePublicKeyBlob_invalidOrTruncatedBlobs() {
        assertFalse(KnownHostsBulkPorter.validatePublicKeyBlob("ssh-ed25519", ""))
        assertFalse(KnownHostsBulkPorter.validatePublicKeyBlob("", sampleEd25519Key1))
        assertFalse(KnownHostsBulkPorter.validatePublicKeyBlob("ssh-ed25519", "NotValidBase64!!!"))
        // Base64 of 2 bytes (too short for 4-byte uint32 header)
        assertFalse(KnownHostsBulkPorter.validatePublicKeyBlob("ssh-ed25519", "AQI="))
        // Header with length 11 ("ssh-ed25519"), but no trailing key material
        val onlyHeader = ByteArray(15)
        onlyHeader[3] = 11
        System.arraycopy("ssh-ed25519".toByteArray(StandardCharsets.US_ASCII), 0, onlyHeader, 4, 11)
        val onlyHeaderB64 = Base64.getEncoder().encodeToString(onlyHeader)
        assertFalse(KnownHostsBulkPorter.validatePublicKeyBlob("ssh-ed25519", onlyHeaderB64))
    }

    @Test
    fun testImport_payloadSizeExceeded() {
        val oversizedContent = "a".repeat(KnownHostsBulkPorter.MAX_PAYLOAD_BYTES + 1)
        val result = KnownHostsBulkPorter.importKnownHosts(oversizedContent, manager)

        assertFalse(result.isSuccess)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("exceeds maximum limit"))
    }

    @Test
    fun testImport_streamPayloadSizeExceeded() {
        val oversizedBytes = ByteArray(KnownHostsBulkPorter.MAX_PAYLOAD_BYTES + 10)
        oversizedBytes.fill('a'.code.toByte())
        val inputStream = ByteArrayInputStream(oversizedBytes)

        val result = KnownHostsBulkPorter.importKnownHosts(inputStream, manager)

        assertFalse(result.isSuccess)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("exceeds maximum allowed limit"))
    }

    @Test
    fun testImport_maxEntryCountExceeded() {
        val sb = StringBuilder()
        for (i in 1..2005) {
            sb.append("host$i.internal ssh-ed25519 $sampleEd25519Key1\n")
        }
        val result = KnownHostsBulkPorter.importKnownHosts(sb.toString(), manager)

        assertFalse(result.isSuccess)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("exceeds maximum allowed limit of 2000"))
    }

    @Test
    fun testImport_skipsOversizedLinesAndLinesWithControlCharacters() {
        val normalLine = "valid.internal ssh-ed25519 $sampleEd25519Key1"
        val oversizedLine = "oversized.internal ssh-ed25519 " + "A".repeat(8200)
        val nullCharLine = "null\u0000.internal ssh-ed25519 $sampleEd25519Key1"
        val escapeCharLine = "esc\u001B.internal ssh-ed25519 $sampleEd25519Key1"
        val delCharLine = "del\u007F.internal ssh-ed25519 $sampleEd25519Key1"

        val content = """
            # Header
            $normalLine
            $oversizedLine
            $nullCharLine
            $escapeCharLine
            $delCharLine
        """.trimIndent()

        val result = KnownHostsBulkPorter.importKnownHosts(content, manager)

        assertTrue(result.isSuccess)
        assertEquals(1, result.importedCount)
        assertEquals(4, result.skippedCount)
        assertEquals(4, result.warnings.size)

        val entries = manager.getEntries()
        assertEquals(1, entries.size)
        assertEquals("valid.internal", entries[0].rawHostPattern)
    }

    @Test
    fun testImport_parsesAllSupportedEntryTypes() {
        val hashedLine = manager.hashHostPattern("hashed.server.org") + " ssh-ed25519 $sampleEd25519Key1"
        val content = """
            # Standard host
            plain.server.org ssh-ed25519 $sampleEd25519Key1 Production Node
            # Bracketed port
            [custom.server.org]:2222 ssh-rsa $sampleRsaKey1
            # Hashed host
            $hashedLine
            # CA entry
            @cert-authority *.internal.corp ssh-ed25519 $sampleEd25519Key1 CA Authority
            # Revoked entry
            @revoked compromised.host.net ssh-rsa $sampleRsaKey1 Revoked for security breach
        """.trimIndent()

        val result = KnownHostsBulkPorter.importKnownHosts(content, manager)

        assertTrue(result.isSuccess)
        assertEquals(5, result.importedCount)

        val entries = manager.getEntries()
        assertEquals(5, entries.size)

        val plain = entries.first { it.rawHostPattern == "plain.server.org" }
        assertEquals("Production Node", plain.comment)
        assertFalse(plain.isCertAuthority)
        assertFalse(plain.isRevoked)

        val bracketed = entries.first { it.rawHostPattern == "[custom.server.org]:2222" }
        assertEquals("ssh-rsa", bracketed.keyType)

        val hashed = entries.first { it.isHashed }
        assertTrue(manager.matchesHost(hashed, "hashed.server.org", 22))

        val ca = entries.first { it.isCertAuthority }
        assertEquals("*.internal.corp", ca.rawHostPattern)
        assertEquals("CA Authority", ca.comment)

        val revoked = entries.first { it.isRevoked }
        assertEquals("compromised.host.net", revoked.rawHostPattern)
        assertEquals("Revoked for security breach", revoked.comment)
    }

    @Test
    fun testImport_strictRejectConflicts_plaintextConflict() {
        manager.saveOrUpdateHostKey("node.example.com", 22, "ssh-ed25519", sampleEd25519Key1, hashHost = false)

        val incomingContent = "node.example.com ssh-ed25519 $sampleEd25519Key2"
        val result = KnownHostsBulkPorter.importKnownHosts(
            incomingContent,
            manager,
            KnownHostsMergeStrategy.STRICT_REJECT_CONFLICTS,
        )

        assertFalse(result.isSuccess)
        assertEquals(1, result.conflicts.size)
        assertEquals(0, result.importedCount)
        assertEquals("node.example.com", result.conflicts[0].host)

        // Manager must be left untouched
        val entries = manager.getEntries()
        assertEquals(1, entries.size)
        assertEquals(sampleEd25519Key1, entries[0].publicKeyBase64)
    }

    @Test
    fun testImport_strictRejectConflicts_hashedExistingVsPlaintextIncoming() {
        // Pre-populate manager with hashed entry
        manager.saveOrUpdateHostKey("secure.vault.org", 22, "ssh-ed25519", sampleEd25519Key1, hashHost = true)
        val existingEntries = manager.getEntries()
        assertEquals(1, existingEntries.size)
        assertTrue(existingEntries[0].isHashed)

        // Incoming is plaintext for the same host but differing key
        val incoming = "secure.vault.org ssh-ed25519 $sampleEd25519Key2"
        val result = KnownHostsBulkPorter.importKnownHosts(
            incoming,
            manager,
            KnownHostsMergeStrategy.STRICT_REJECT_CONFLICTS,
        )

        assertFalse(result.isSuccess)
        assertEquals(1, result.conflicts.size)
        assertEquals(sampleEd25519Key1, existingEntries[0].publicKeyBase64)
    }

    @Test
    fun testImport_strictRejectConflicts_plaintextExistingVsHashedIncoming() {
        // Pre-populate manager with plaintext entry
        manager.saveOrUpdateHostKey("secure.vault.org", 22, "ssh-ed25519", sampleEd25519Key1, hashHost = false)

        // Incoming is hashed for secure.vault.org but with differing key
        val incomingHashedToken = manager.hashHostPattern("secure.vault.org")
        val incoming = "$incomingHashedToken ssh-ed25519 $sampleEd25519Key2"
        val result = KnownHostsBulkPorter.importKnownHosts(
            incoming,
            manager,
            KnownHostsMergeStrategy.STRICT_REJECT_CONFLICTS,
        )

        assertFalse(result.isSuccess)
        assertEquals(1, result.conflicts.size)
    }

    @Test
    fun testImport_strictRejectConflicts_intraBatchConflict() {
        val content = """
            duplicate.host.org ssh-ed25519 $sampleEd25519Key1
            duplicate.host.org ssh-ed25519 $sampleEd25519Key2
        """.trimIndent()

        val result = KnownHostsBulkPorter.importKnownHosts(
            content,
            manager,
            KnownHostsMergeStrategy.STRICT_REJECT_CONFLICTS,
        )

        assertFalse(result.isSuccess)
        assertEquals(1, result.conflicts.size)
        assertTrue(result.conflicts[0].message.contains("within import payload"))
    }

    @Test
    fun testImport_strictRejectConflicts_identicalKeySkippedWithoutConflict() {
        manager.saveOrUpdateHostKey("node.example.com", 22, "ssh-ed25519", sampleEd25519Key1, hashHost = false)

        val incomingContent = """
            node.example.com ssh-ed25519 $sampleEd25519Key1
            new.example.com ssh-ed25519 $sampleEd25519Key2
        """.trimIndent()

        val result = KnownHostsBulkPorter.importKnownHosts(
            incomingContent,
            manager,
            KnownHostsMergeStrategy.STRICT_REJECT_CONFLICTS,
        )

        assertTrue(result.isSuccess)
        assertEquals(1, result.importedCount)
        assertEquals(1, result.skippedCount)
        assertEquals(0, result.conflicts.size)

        val entries = manager.getEntries()
        assertEquals(2, entries.size)
    }

    @Test
    fun testImport_skipExistingStrategy() {
        manager.saveOrUpdateHostKey("node.example.com", 22, "ssh-ed25519", sampleEd25519Key1, hashHost = false)

        val incomingContent = """
            node.example.com ssh-ed25519 $sampleEd25519Key2
            fresh.example.com ssh-ed25519 $sampleEd25519Key2
        """.trimIndent()

        val result = KnownHostsBulkPorter.importKnownHosts(
            incomingContent,
            manager,
            KnownHostsMergeStrategy.SKIP_EXISTING,
        )

        assertTrue(result.isSuccess)
        assertEquals(1, result.importedCount)
        assertEquals(1, result.skippedCount)
        assertEquals(0, result.overwrittenCount)

        val entries = manager.getEntries()
        assertEquals(2, entries.size)
        val existingNode = entries.first { it.rawHostPattern == "node.example.com" }
        assertEquals(sampleEd25519Key1, existingNode.publicKeyBase64)
    }

    @Test
    fun testImport_overwriteChangedStrategy() {
        val initialContent = """
            # Production bastion comment
            bastion.example.com ssh-ed25519 $sampleEd25519Key1 Initial Comment
            unmodified.example.com ssh-rsa $sampleRsaKey1
        """.trimIndent()
        knownHostsFile.writeText("$initialContent\n")

        val incomingContent = """
            bastion.example.com ssh-ed25519 $sampleEd25519Key2 Updated Comment
            brandnew.example.com ssh-ed25519 $sampleEd25519Key1
        """.trimIndent()

        val result = KnownHostsBulkPorter.importKnownHosts(
            incomingContent,
            manager,
            KnownHostsMergeStrategy.OVERWRITE_CHANGED,
        )

        assertTrue(result.isSuccess)
        assertEquals(1, result.importedCount)
        assertEquals(1, result.overwrittenCount)

        val entries = manager.getEntries()
        assertEquals(3, entries.size)

        val updatedBastion = entries.first { it.rawHostPattern == "bastion.example.com" }
        assertEquals(sampleEd25519Key2, updatedBastion.publicKeyBase64)
        assertEquals("Updated Comment", updatedBastion.comment)

        // Preserves comments in file
        val fileLines = knownHostsFile.readLines()
        assertTrue(fileLines.any { it.contains("# Production bastion comment") })
    }

    @Test
    fun testExportToOpenSshFormat_stringAndStream() {
        val content = """
            node1.example.com ssh-ed25519 $sampleEd25519Key1 Production Node
            [node2.example.com]:2222 ssh-rsa $sampleRsaKey1
            @cert-authority *.internal ssh-ed25519 $sampleEd25519Key2 Internal CA
            @revoked compromised.internal ssh-rsa $sampleRsaKey2 Bad Key
        """.trimIndent()
        knownHostsFile.writeText("$content\n")

        val exportedString = KnownHostsBulkPorter.exportToOpenSshFormat(manager)
        assertTrue(exportedString.contains("node1.example.com ssh-ed25519 $sampleEd25519Key1 Production Node"))
        assertTrue(exportedString.contains("[node2.example.com]:2222 ssh-rsa $sampleRsaKey1"))
        assertTrue(exportedString.contains("@cert-authority *.internal ssh-ed25519 $sampleEd25519Key2 Internal CA"))
        assertTrue(exportedString.contains("@revoked compromised.internal ssh-rsa $sampleRsaKey2 Bad Key"))

        val outputStream = ByteArrayOutputStream()
        KnownHostsBulkPorter.exportToOpenSshFormat(manager, outputStream)
        val streamedString = outputStream.toString(StandardCharsets.UTF_8.name())
        assertEquals(exportedString, streamedString)
    }

    @Test
    fun testExportToOpenSshFormat_emptyManager() {
        knownHostsFile.delete()
        val exported = KnownHostsBulkPorter.exportToOpenSshFormat(manager)
        assertEquals("", exported)
    }

    @Test
    fun testSanitizeComment() {
        val dirtyComment = "My server\u0000\u001B[31m colored \u007F\r\ncomment"
        val clean = KnownHostsBulkPorter.sanitizeComment(dirtyComment)
        assertEquals("My server[31m colored comment", clean)
    }

    @Test
    fun testParseHostAndPort() {
        val (h1, p1) = KnownHostsBulkPorter.parseHostAndPort("example.com")
        assertEquals("example.com", h1)
        assertEquals(22, p1)

        val (h2, p2) = KnownHostsBulkPorter.parseHostAndPort("[example.com]:2222")
        assertEquals("example.com", h2)
        assertEquals(2222, p2)

        val (h3, p3) = KnownHostsBulkPorter.parseHostAndPort("[::1]:8022")
        assertEquals("::1", h3)
        assertEquals(8022, p3)
    }

    @Test
    fun testImport_multipleKeyTypesForSameHostDoNotConflict() {
        val initialContent = "dualstack.internal ssh-ed25519 $sampleEd25519Key1"
        knownHostsFile.writeText("$initialContent\n")

        val incomingContent = "dualstack.internal ssh-rsa $sampleRsaKey1"
        val result = KnownHostsBulkPorter.importKnownHosts(
            incomingContent,
            manager,
            KnownHostsMergeStrategy.STRICT_REJECT_CONFLICTS,
        )

        assertTrue(result.isSuccess)
        assertEquals(1, result.importedCount)
        assertEquals(0, result.conflicts.size)

        val entries = manager.getEntries()
        assertEquals(2, entries.size)
        assertTrue(entries.any { it.keyType == "ssh-ed25519" })
        assertTrue(entries.any { it.keyType == "ssh-rsa" })
    }
}
