package com.adamoutler.ssh.crypto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class KnownHostsManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var knownHostsFile: File
    private lateinit var manager: KnownHostsManager

    private val sampleEd25519Key = "AAAAC3NzaC1lZDI1NTE5AAAAIGo4wN/jWvjZ4mN+0pB4B9wT7Hq9q2r1x8kL8p8kL8p8"
    private val sampleRsaKey = "AAAAB3NzaC1yc2EAAAADAQABAAABAQC3r3r3r3r3r3r3r3r3r3r3r3r3r3r3r3r3r3r3"

    @Before
    fun setup() {
        knownHostsFile = tempFolder.newFile("ssh_known_hosts")
        manager = KnownHostsManager(knownHostsFile)
    }

    @Test
    fun testParseLine_commentsAndEmptyLines() {
        assertNull(manager.parseLine(""))
        assertNull(manager.parseLine("   "))
        assertNull(manager.parseLine("# This is a comment"))
        assertNull(manager.parseLine("invalid-line-with-only-one-token"))
        assertNull(manager.parseLine("invalid token"))
    }

    @Test
    fun testParseLine_plaintextHost() {
        val line = "192.168.1.100 ssh-ed25519 $sampleEd25519Key"
        val record = manager.parseLine(line)
        assertNotNull(record)
        assertEquals("192.168.1.100", record!!.rawHostPattern)
        assertEquals("192.168.1.100", record.displayHost)
        assertFalse(record.isHashed)
        assertEquals("ssh-ed25519", record.keyType)
        assertEquals(sampleEd25519Key, record.publicKeyBase64)
        assertTrue(record.fingerprint.startsWith("SHA256:"))
        assertNull(record.saltBase64)
        assertNull(record.hashBase64)
    }

    @Test
    fun testParseLine_hashedHost() {
        val salt = "F1Y8yB+90GfL8y3k8/X+xA=="
        val hash = "6hG+U8y3k8/X+xAF1Y8yB+90GfM="
        val line = "|1|$salt|$hash ssh-rsa $sampleRsaKey"

        val record = manager.parseLine(line)
        assertNotNull(record)
        assertTrue(record!!.isHashed)
        assertEquals(salt, record.saltBase64)
        assertEquals(hash, record.hashBase64)
        assertEquals("ssh-rsa", record.keyType)
        assertTrue(record.displayHost.startsWith("|1|"))
        assertTrue(record.displayHost.contains("(Hashed Host)"))
        assertTrue(record.fingerprint.startsWith("SHA256:"))
    }

    @Test
    fun testMatchesHost_plaintext() {
        val record = manager.parseLine("server.example.com,[10.0.0.1]:2222 ssh-ed25519 $sampleEd25519Key")!!

        // Host on port 22
        assertTrue(manager.matchesHost(record, "server.example.com", 22))
        // Non-standard port match
        assertTrue(manager.matchesHost(record, "10.0.0.1", 2222))
        // Mismatch
        assertFalse(manager.matchesHost(record, "other.example.com", 22))
        assertFalse(manager.matchesHost(record, "10.0.0.1", 22))
    }

    @Test
    fun testMatchesHost_hashed() {
        val host = "secure.company.org"
        val hashedLine = manager.hashHostPattern(host) + " ssh-ed25519 $sampleEd25519Key"
        val record = manager.parseLine(hashedLine)!!

        assertTrue(record.isHashed)
        assertTrue(manager.matchesHost(record, host, 22))
        assertFalse(manager.matchesHost(record, "attacker.org", 22))
    }

    @Test
    fun testMatchesHost_hashedCustomPort() {
        val host = "git.example.com"
        val port = 2223
        val formatted = "[$host]:$port"
        val hashedLine = manager.hashHostPattern(formatted) + " ssh-ed25519 $sampleEd25519Key"
        val record = manager.parseLine(hashedLine)!!

        assertTrue(record.isHashed)
        assertTrue(manager.matchesHost(record, host, port))
        assertFalse(manager.matchesHost(record, host, 22))
    }

    @Test
    fun testGetEntries_readsAllValidRecords() {
        val content = """
            # Header comment
            node1.internal ssh-ed25519 $sampleEd25519Key
            
            node2.internal ssh-rsa $sampleRsaKey
            # Another comment
        """.trimIndent()
        knownHostsFile.writeText(content)

        val entries = manager.getEntries()
        assertEquals(2, entries.size)
        assertEquals("node1.internal", entries[0].rawHostPattern)
        assertEquals("node2.internal", entries[1].rawHostPattern)
    }

    @Test
    fun testRemoveEntry_single() {
        val line1 = "host1.local ssh-ed25519 $sampleEd25519Key"
        val line2 = "host2.local ssh-rsa $sampleRsaKey"
        knownHostsFile.writeText("$line1\n$line2\n")

        val entries = manager.getEntries()
        assertEquals(2, entries.size)

        val success = manager.removeEntry(entries[0].id)
        assertTrue(success)

        val remaining = manager.getEntries()
        assertEquals(1, remaining.size)
        assertEquals("host2.local", remaining[0].rawHostPattern)
    }

    @Test
    fun testRemoveEntries_bulk() {
        val line1 = "host1.local ssh-ed25519 $sampleEd25519Key"
        val line2 = "host2.local ssh-rsa $sampleRsaKey"
        val line3 = "host3.local ssh-ed25519 $sampleEd25519Key"
        knownHostsFile.writeText("$line1\n$line2\n$line3\n")

        val entries = manager.getEntries()
        assertEquals(3, entries.size)

        val removedCount = manager.removeEntries(setOf(entries[0].id, entries[2].id))
        assertEquals(2, removedCount)

        val remaining = manager.getEntries()
        assertEquals(1, remaining.size)
        assertEquals("host2.local", remaining[0].rawHostPattern)
    }

    @Test
    fun testSaveOrUpdateHostKey_newEntryHashed() {
        knownHostsFile.delete() // Ensure creates if missing

        val success = manager.saveOrUpdateHostKey(
            formattedHost = "bastion.cloud.com",
            port = 22,
            keyType = "ssh-ed25519",
            keyBlobBase64 = sampleEd25519Key,
            hashHost = true,
        )
        assertTrue(success)
        assertTrue(knownHostsFile.exists())

        val entries = manager.getEntries()
        assertEquals(1, entries.size)
        assertTrue(entries[0].isHashed)
        assertTrue(manager.matchesHost(entries[0], "bastion.cloud.com", 22))
    }

    @Test
    fun testSaveOrUpdateHostKey_replacesExistingHostKeyWithoutDuplicates() {
        // First add a plaintext entry
        manager.saveOrUpdateHostKey(
            formattedHost = "bastion.cloud.com",
            port = 22,
            keyType = "ssh-ed25519",
            keyBlobBase64 = sampleEd25519Key,
            hashHost = false,
        )
        assertEquals(1, manager.getEntries().size)
        assertFalse(manager.getEntries()[0].isHashed)

        // Now update with a new key and hashHost = true
        val newKey = "AAAAC3NzaC1lZDI1NTE5AAAAINewKeyDataForReplacement1234567890123"
        manager.saveOrUpdateHostKey(
            formattedHost = "bastion.cloud.com",
            port = 22,
            keyType = "ssh-ed25519",
            keyBlobBase64 = newKey,
            hashHost = true,
        )

        // Must still only have 1 entry (stale entry replaced)
        val updatedEntries = manager.getEntries()
        assertEquals(1, updatedEntries.size)
        assertTrue(updatedEntries[0].isHashed)
        assertEquals(newKey, updatedEntries[0].publicKeyBase64)
        assertTrue(manager.matchesHost(updatedEntries[0], "bastion.cloud.com", 22))
    }

    @Test
    fun testHashAllPlaintextHosts_convertsOnlyPlaintextAndPreservesComments() {
        val plaintext1 = "host1.example.com ssh-ed25519 $sampleEd25519Key"
        val plaintext2 = "host2.example.com ssh-rsa $sampleRsaKey"
        val hashedLine = manager.hashHostPattern("already-hashed.com") + " ssh-ed25519 $sampleEd25519Key"
        val comment = "# Important production hosts"

        knownHostsFile.writeText("$comment\n$plaintext1\n$plaintext2\n$hashedLine\n")

        val converted = manager.hashAllPlaintextHosts()
        assertEquals(2, converted)

        val entries = manager.getEntries()
        assertEquals(3, entries.size)
        assertTrue(entries.all { it.isHashed })

        // Check that hosts still match their respective original hostnames
        assertTrue(manager.matchesHost(entries.first { manager.matchesHost(it, "host1.example.com", 22) }, "host1.example.com", 22))
        assertTrue(manager.matchesHost(entries.first { manager.matchesHost(it, "host2.example.com", 22) }, "host2.example.com", 22))
        assertTrue(manager.matchesHost(entries.first { manager.matchesHost(it, "already-hashed.com", 22) }, "already-hashed.com", 22))
    }

    @Test
    fun testContextConstructor() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val contextManager = KnownHostsManager(context)
        assertEquals(File(context.filesDir, "ssh_known_hosts").absolutePath, contextManager.knownHostsFile.absolutePath)
    }

    @Test
    fun testInvalidKeyBlob_handlesFingerprintGracefully() {
        val line = "host.invalid ssh-ed25519 NotAValidBase64!!!"
        val record = manager.parseLine(line)
        assertNotNull(record)
        assertEquals("SHA256:Unknown", record!!.fingerprint)
    }
}
