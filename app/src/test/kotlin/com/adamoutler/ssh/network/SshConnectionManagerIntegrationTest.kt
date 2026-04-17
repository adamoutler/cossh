package com.adamoutler.ssh.network

import com.adamoutler.ssh.crypto.SSHKeyGenerator
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SshConnectionManagerIntegrationTest {

    @Test
    fun testHeadlessPasswordConnectionAndCommandExecution() = runBlocking {
        val profile = ConnectionProfile(
            id = "test-1",
            nickname = "Test Server",
            host = "mock.hackedyour.info",
            port = 32222,
            username = "testuser",
            authType = AuthType.PASSWORD,
            password = "testpassword".toByteArray()
        )

        val manager = SshConnectionManager(net.schmizz.sshj.transport.verification.PromiscuousVerifier())
        val output = manager.connectAndExecute(profile, "echo \"CoSSH_Test\"")

        // The mock server exec channel handler we added responds with the command string
        assertEquals("echo \"CoSSH_Test\"\n", output)
    }

    @Test
    fun testHeadlessPasswordConnectionFailsAndClearsMemory() = runBlocking {
        val passwordBytes = "wrongpassword".toByteArray()
        val profile = ConnectionProfile(
            id = "test-fail",
            nickname = "Test Server",
            host = "mock.hackedyour.info",
            port = 32222,
            username = "testuser",
            authType = AuthType.PASSWORD,
            password = passwordBytes
        )

        val manager = SshConnectionManager(net.schmizz.sshj.transport.verification.PromiscuousVerifier())
        try {
            manager.connectAndExecute(profile, "echo \"CoSSH_Test\"")
            org.junit.Assert.fail("Expected UserAuthException")
        } catch (e: Exception) {
            // Memory should be zeroed
            val allZero = passwordBytes.all { it == 0.toByte() }
            org.junit.Assert.assertTrue("Password memory was not cleared on exception!", allZero)
        }
    }

    @Test
    fun testHeadlessKeyConnectionAndCommandExecution() = runBlocking {
        val keyPair = SSHKeyGenerator.generateRSAKeyPair()
        
        val profile = ConnectionProfile(
            id = "test-2",
            nickname = "Test Server",
            host = "mock.hackedyour.info",
            port = 32222,
            username = "testuser",
            authType = AuthType.KEY
        )

        val manager = SshConnectionManager(net.schmizz.sshj.transport.verification.PromiscuousVerifier())
        val output = manager.connectAndExecute(profile, "echo \"CoSSH_Test\"", keyPair)

        assertEquals("echo \"CoSSH_Test\"\n", output)
    }
}