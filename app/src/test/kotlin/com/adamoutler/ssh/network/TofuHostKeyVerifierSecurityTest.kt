package com.adamoutler.ssh.network

import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.IdentityProfile
import kotlinx.coroutines.runBlocking
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.DisconnectReason
import net.schmizz.sshj.transport.TransportException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64

class TofuHostKeyVerifierSecurityTest {

    private lateinit var knownHostsFile: File
    private lateinit var verifier: TofuHostKeyVerifier

    @Before
    fun setUp() {
        knownHostsFile = File.createTempFile("known_hosts_sec_test", ".tmp")
        knownHostsFile.writeText("")
        verifier = TofuHostKeyVerifier(knownHostsFile)
        ConnectionStateRepository.setPromptRequestForTest(null)
    }

    @After
    fun tearDown() {
        ConnectionStateRepository.setPromptRequestForTest(null)
        if (knownHostsFile.exists()) {
            knownHostsFile.delete()
        }
        File(knownHostsFile.absolutePath + ".tmp").delete()
    }

    private fun generateKey(): PublicKey {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(1024)
        return kpg.generateKeyPair().public
    }

    private fun computeSha256Fingerprint(key: PublicKey): String {
        val buffer = net.schmizz.sshj.common.Buffer.PlainBuffer().putPublicKey(key)
        val md = MessageDigest.getInstance("SHA-256")
        return "SHA256:" + Base64.getEncoder().encodeToString(md.digest(buffer.compactData))
    }

    private fun writeEntry(host: String, key: PublicKey) {
        val keyType = net.schmizz.sshj.common.KeyType.fromKey(key).toString()
        val buffer = net.schmizz.sshj.common.Buffer.PlainBuffer().putPublicKey(key)
        val keyBlob = Base64.getEncoder().encodeToString(buffer.compactData)
        knownHostsFile.appendText("$host $keyType $keyBlob\n")
    }

    @Test(timeout = 30000L)
    fun verify_whenHostKeyMismatchAndUserRejects_returnsFalseAndDoesNotModifyKnownHosts() {
        val host = "192.168.1.50"
        val port = 22
        val keyA = generateKey()
        val keyB = generateKey()

        writeEntry(host, keyA)
        val initialContent = knownHostsFile.readText()
        val expectedSha256A = computeSha256Fingerprint(keyA)
        val expectedSha256B = computeSha256Fingerprint(keyB)

        // Asynchronously intercept prompt request and reject
        val promptThread = Thread {
            while (ConnectionStateRepository.promptRequest.value == null) {
                Thread.sleep(25)
            }
            val request = ConnectionStateRepository.promptRequest.value
            assertNotNull("Prompt request must be emitted", request)
            assertTrue("Should indicate key changed (MITM warning)", request!!.isKeyChanged)
            assertEquals("Hostname must match formatted host", host, request.hostname)
            assertEquals("Expected fingerprint must match Key A", expectedSha256A, request.expectedFingerprint)
            assertEquals("Received fingerprint must match Key B", expectedSha256B, request.receivedFingerprint)

            ConnectionStateRepository.resolvePrompt(false)
        }
        promptThread.start()

        val verified = verifier.verify(host, port, keyB)
        promptThread.join(5000)

        assertFalse("Verification must return false on user rejection", verified)
        val currentContent = knownHostsFile.readText()
        assertEquals("known_hosts must remain completely unmodified on rejection", initialContent, currentContent)
        assertFalse("Key B must NOT be written to known_hosts", currentContent.contains(expectedSha256B))
    }

    @Test(timeout = 30000L)
    fun verify_whenNewHostAndUserRejects_returnsFalseAndKnownHostsRemainsEmpty() {
        val host = "10.0.0.1"
        val port = 22
        val keyA = generateKey()
        val expectedSha256A = computeSha256Fingerprint(keyA)

        val promptThread = Thread {
            while (ConnectionStateRepository.promptRequest.value == null) {
                Thread.sleep(25)
            }
            val request = ConnectionStateRepository.promptRequest.value
            assertNotNull(request)
            assertFalse("Must NOT indicate key changed for new host", request!!.isKeyChanged)
            assertNull("Expected fingerprint must be null for new host", request.expectedFingerprint)
            assertEquals("Received fingerprint must match Key A", expectedSha256A, request.receivedFingerprint)

            ConnectionStateRepository.resolvePrompt(false)
        }
        promptThread.start()

        val verified = verifier.verify(host, port, keyA)
        promptThread.join(5000)

        assertFalse("Verification must return false on user rejection", verified)
        assertTrue("known_hosts must remain empty when new host is rejected", knownHostsFile.readText().isEmpty())
    }

    @Test(timeout = 30000L)
    fun verify_whenHostKeyMismatchAndUserAccepts_atomicallyUpdatesKnownHosts() {
        val host = "192.168.1.50"
        val port = 22
        val keyA = generateKey()
        val keyB = generateKey()

        writeEntry(host, keyA)

        val promptThread = Thread {
            while (ConnectionStateRepository.promptRequest.value == null) {
                Thread.sleep(25)
            }
            ConnectionStateRepository.resolvePrompt(true)
        }
        promptThread.start()

        val verified = verifier.verify(host, port, keyB)
        promptThread.join(5000)

        assertTrue("Verification must return true when user accepts replacement", verified)
        val content = knownHostsFile.readText().trim()
        val lines = content.split("\n")
        assertEquals("File must contain exactly 1 entry for host after update", 1, lines.size)
        val bufferB = net.schmizz.sshj.common.Buffer.PlainBuffer().putPublicKey(keyB)
        val keyBlobB = Base64.getEncoder().encodeToString(bufferB.compactData)
        assertTrue("Entry must contain Key B", content.contains(keyBlobB))
    }

    @Test(timeout = 30000L)
    fun verify_whenCustomPortAndUserRejects_preservesExistingPortEntry() {
        val host = "10.0.0.2"
        val port = 2222
        val formattedHost = "[$host]:$port"
        val keyA = generateKey()
        val keyB = generateKey()

        writeEntry(formattedHost, keyA)
        val initialContent = knownHostsFile.readText()

        val promptThread = Thread {
            while (ConnectionStateRepository.promptRequest.value == null) {
                Thread.sleep(25)
            }
            val request = ConnectionStateRepository.promptRequest.value!!
            assertTrue("Must detect existing port entry as key changed", request.isKeyChanged)
            assertEquals(formattedHost, request.hostname)
            ConnectionStateRepository.resolvePrompt(false)
        }
        promptThread.start()

        val verified = verifier.verify(host, port, keyB)
        promptThread.join(5000)

        assertFalse("Verification must return false on rejection", verified)
        assertEquals("known_hosts must preserve original port entry", initialContent, knownHostsFile.readText())
    }

    @Test(timeout = 30000L)
    fun verify_matchingKeyAlreadyInKnownHosts_returnsTrueWithoutPrompt() {
        val host = "192.168.1.99"
        val port = 22
        val key = generateKey()

        writeEntry(host, key)
        val verifiedVerifier = TofuHostKeyVerifier(knownHostsFile)

        val verified = verifiedVerifier.verify(host, port, key)
        assertTrue("Should verify existing key immediately", verified)
        assertNull("No prompt request should be emitted for verified key", ConnectionStateRepository.promptRequest.value)
    }

    @Test(timeout = 30000L)
    fun verify_whenHostKeyMismatch_abortsHandshakeWithoutAuthentication() {
        val keyRejectedVerifier = object : net.schmizz.sshj.transport.verification.HostKeyVerifier {
            override fun verify(hostname: String?, port: Int, key: PublicKey?): Boolean = false
            override fun findExistingAlgorithms(hostname: String?, port: Int): MutableList<String> = mutableListOf()
        }

        var authenticateInvoked = false
        val coordinator = object : SshHandshakeCoordinator(hostKeyVerifier = keyRejectedVerifier) {
            override fun connectClient(client: SSHClient, host: String, port: Int) {
                // Simulate SSHJ key exchange failure when HostKeyVerifier returns false
                throw TransportException(DisconnectReason.HOST_KEY_NOT_VERIFIABLE, "Host key not verifiable")
            }

            override fun authenticateClient(
                client: SSHClient,
                profile: ConnectionProfile,
                keyPair: KeyPair?,
                identity: IdentityProfile?,
                effectiveProfile: ConnectionProfile,
            ) {
                authenticateInvoked = true
            }
        }

        val testPassword = byteArrayOf(1, 2, 3, 4, 5)
        val profile = ConnectionProfile(
            id = "test-prof",
            nickname = "Test",
            host = "secure.host",
            port = 22,
            authType = AuthType.PASSWORD,
            password = testPassword,
        )

        runBlocking {
            try {
                coordinator.executeWithConnection(SSHClient(), profile, null) {
                    fail("Block should not execute when host key verification fails")
                }
                fail("executeWithConnection must throw TransportException on host key rejection")
            } catch (e: TransportException) {
                assertEquals(DisconnectReason.HOST_KEY_NOT_VERIFIABLE, e.disconnectReason)
            }
        }

        assertFalse("authenticateClient must NEVER be invoked when host key is rejected", authenticateInvoked)
        assertTrue("Profile password must be zero-wiped upon handshake abort", testPassword.all { it == 0.toByte() })
    }
}
