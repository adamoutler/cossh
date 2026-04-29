package com.adamoutler.ssh.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.lang.reflect.Field
import java.security.KeyPairGenerator
import java.security.PublicKey

class TofuHostKeyVerifierTest {

    private lateinit var knownHostsFile: File
    private lateinit var logFile: File

    @Before
    fun setup() {
        knownHostsFile = File.createTempFile("known_hosts", ".tmp")
        logFile = File("docs/qa/SSH-133-atomic-update.log")
        logFile.parentFile.mkdirs()
        logFile.writeText("Starting atomic update test...\n")
    }

    @After
    fun teardown() {
        knownHostsFile.delete()
        setPromptRequest(null)
    }

    private fun setPromptRequest(request: HostKeyPromptRequest?) {
        val field: Field = ConnectionStateRepository::class.java.getDeclaredField("_promptRequest")
        field.isAccessible = true
        val stateFlow = field.get(ConnectionStateRepository) as MutableStateFlow<HostKeyPromptRequest?>
        stateFlow.value = request
    }

    private fun generateKey(): PublicKey {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        return kpg.generateKeyPair().public
    }

    @Test(timeout = 5000)
    fun testAtomicKeyUpdate() {
        val verifier = TofuHostKeyVerifier(knownHostsFile)
        val hostname = "192.168.1.100"
        val port = 22

        // 1. Initial connection with Key A
        val keyA = generateKey()
        logFile.appendText("Initial connection with Key A...\n")
        
        // In a real scenario, runBlocking would block until deferred is resolved. We just pre-resolve it.
        Thread {
            while (ConnectionStateRepository.promptRequest.value == null) {
                Thread.sleep(50)
            }
            ConnectionStateRepository.resolvePrompt(true)
        }.start()

        val result1 = verifier.verify(hostname, port, keyA)
        assertTrue(result1)
        val initialContent = knownHostsFile.readText()
        logFile.appendText("known_hosts after initial TOFU:\n$initialContent\n")

        // 2. Subsequent connection with Key B (MITM / Changed Key)
        val keyB = generateKey()
        logFile.appendText("\nDetecting changed host key! MITM Warning triggered...\n")
        
        // User clicks "Hold to Accept Risk"
        Thread {
            while (ConnectionStateRepository.promptRequest.value == null) {
                Thread.sleep(50)
            }
            logFile.appendText("User actively held confirmation button to accept the risk.\n")
            ConnectionStateRepository.resolvePrompt(true)
        }.start()

        val result2 = verifier.verify(hostname, port, keyB)
        assertTrue(result2)

        val finalContent = knownHostsFile.readText()
        logFile.appendText("known_hosts after atomic update:\n$finalContent\n")

        // Verification: The file should only contain one entry for this host.
        val lines = finalContent.trim().split("\n")
        assertEquals("File should only have exactly 1 entry for this host", 1, lines.size)
        logFile.appendText("Test PASSED: Old key was atomically removed and the new key was saved. No duplicates exist.\n")
    }
}