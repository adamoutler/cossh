package com.adamoutler.ssh.network

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import java.io.File
import java.security.KeyPairGenerator

class TofuHostKeyVerifierPortBugTest {

    private lateinit var knownHostsFile: File
    private lateinit var verifier: TofuHostKeyVerifier

    @Before
    fun setUp() {
        knownHostsFile = File.createTempFile("known_hosts_bug_test", ".tmp")
        knownHostsFile.writeText("")
        verifier = TofuHostKeyVerifier(knownHostsFile)
    }

    @After
    fun tearDown() {
        knownHostsFile.delete()
    }

    @Test
    fun testPort22AndOtherPortDoNotOverwriteEachOther() {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val key22 = kpg.generateKeyPair().public
        val key32222 = kpg.generateKeyPair().public

        // Simulate accepting port 22
        Thread {
            while (ConnectionStateRepository.promptRequest.value == null) {
                Thread.sleep(50)
            }
            ConnectionStateRepository.resolvePrompt(true)
        }.start()
        verifier.verify("192.168.1.115", 22, key22)
        
        // Assert port 22 is in file
        assertTrue(knownHostsFile.readText().contains("192.168.1.115 ssh-rsa"))

        // Simulate accepting port 32222
        Thread {
            while (ConnectionStateRepository.promptRequest.value == null) {
                Thread.sleep(50)
            }
            ConnectionStateRepository.resolvePrompt(true)
        }.start()
        verifier.verify("192.168.1.115", 32222, key32222)

        val content = knownHostsFile.readText()
        println("File content after port 32222: \n" + content)
        
        // Assert BOTH are in file!
        assertTrue("Port 32222 should be in file", content.contains("[192.168.1.115]:32222 ssh-rsa"))
        assertTrue("Port 22 should STILL be in file", content.contains("192.168.1.115 ssh-rsa"))
    }
}
