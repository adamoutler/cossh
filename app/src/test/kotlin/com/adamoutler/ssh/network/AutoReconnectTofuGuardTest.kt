package com.adamoutler.ssh.network

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.util.Base64

/**
 * Unit tests for TOFU Zero-Prompt Guard during autonomous reconnect (SSH-208).
 * Ensures that if a host key mismatches or is unknown during an autonomous reconnect,
 * the connection is immediately aborted without prompting the user.
 */
class AutoReconnectTofuGuardTest {

    private lateinit var knownHostsFile: File

    @Before
    fun setUp() {
        knownHostsFile = File.createTempFile("known_hosts_tofu_guard", ".tmp")
        knownHostsFile.writeText("")
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

    private fun writeEntry(host: String, key: PublicKey) {
        val keyType = net.schmizz.sshj.common.KeyType.fromKey(key).toString()
        val buffer = net.schmizz.sshj.common.Buffer.PlainBuffer().putPublicKey(key)
        val keyBlob = Base64.getEncoder().encodeToString(buffer.compactData)
        knownHostsFile.appendText("$host $keyType $keyBlob\n")
    }

    @Test
    fun verify_autonomousReconnect_whenHostKeyMatches_returnsTrue() {
        val key = generateKey()
        val host = "192.168.1.100"
        val port = 22
        writeEntry(host, key)

        val verifier = TofuHostKeyVerifier(knownHostsFile, isAutonomousReconnect = true)
        val verified = verifier.verify(host, port, key)

        assertTrue("Autonomous reconnect must succeed when host key matches known_hosts", verified)
        assertNull("No prompt request should be created on host key match", ConnectionStateRepository.promptRequest.value)
    }

    @Test
    fun verify_autonomousReconnect_whenHostKeyMismatches_abortsWithoutPrompt() {
        val originalKey = generateKey()
        val rogueKey = generateKey()
        val host = "192.168.1.100"
        val port = 22
        writeEntry(host, originalKey)

        val verifier = TofuHostKeyVerifier(knownHostsFile, isAutonomousReconnect = true)
        val verified = verifier.verify(host, port, rogueKey)

        assertFalse("Autonomous reconnect must immediately abort on host key mismatch", verified)
        assertNull("Zero-prompt guard must prevent setting prompt request during autonomous reconnect", ConnectionStateRepository.promptRequest.value)
    }

    @Test
    fun verify_autonomousReconnect_whenUnknownHost_abortsWithoutPrompt() {
        val key = generateKey()
        val host = "192.168.1.200"
        val port = 22

        val verifier = TofuHostKeyVerifier(knownHostsFile, isAutonomousReconnect = true)
        val verified = verifier.verify(host, port, key)

        assertFalse("Autonomous reconnect must immediately abort for unknown hosts", verified)
        assertNull("Zero-prompt guard must prevent setting prompt request for unknown host during autonomous reconnect", ConnectionStateRepository.promptRequest.value)
    }

    @Test
    fun verify_manualConnect_whenHostKeyMismatches_allowsPrompt() {
        val originalKey = generateKey()
        val rogueKey = generateKey()
        val host = "192.168.1.100"
        val port = 22
        writeEntry(host, originalKey)

        // Manual connect (isAutonomousReconnect = false)
        val verifier = TofuHostKeyVerifier(knownHostsFile, isAutonomousReconnect = false)
        
        // In a headless test without user prompt handler, the verifier will create a prompt request and timeout or wait
        // We verify that isAutonomousReconnect = false reaches the prompt logic unlike isAutonomousReconnect = true
        Thread {
            while (ConnectionStateRepository.promptRequest.value == null) {
                Thread.sleep(25)
            }
            ConnectionStateRepository.resolvePrompt(false)
        }.start()

        val verified = verifier.verify(host, port, rogueKey)
        assertFalse("Manual connect rejected by user must return false", verified)
    }
}
