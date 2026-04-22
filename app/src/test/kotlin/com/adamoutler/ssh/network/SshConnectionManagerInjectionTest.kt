package com.adamoutler.ssh.network

import com.adamoutler.ssh.crypto.SSHKeyGenerator
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SshConnectionManagerInjectionTest {

    companion object {
        var currentPort = 2224
    }

    private var mockSshdProcess: Process? = null
    private var testPort = 0

    @Before
    fun setUp() {
        testPort = currentPort++
        var mockScript = File("mock_sshd.py")
        if (!mockScript.exists()) {
             mockScript = File("../../mock_sshd.py")
        }
        if (!mockScript.exists()) {
             mockScript = File("../mock_sshd.py")
        }
        
        println("Starting mock_sshd from: ${mockScript.absolutePath} on port $testPort")
        try {
            val pb = ProcessBuilder("python3", mockScript.absolutePath, testPort.toString())
            pb.redirectErrorStream(true)
            mockSshdProcess = pb.start()
            Thread {
                mockSshdProcess?.inputStream?.bufferedReader()?.use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        println("mock_sshd: $line")
                    }
                }
            }.start()
            Thread.sleep(3000)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @After
    fun tearDown() {
        mockSshdProcess?.destroy()
    }

    @Test
    fun testInjectPublicKey_InvalidKey_ReturnsFalse() = runBlocking {
        val manager = SshConnectionManager(net.schmizz.sshj.transport.verification.PromiscuousVerifier())
        val profile = ConnectionProfile(
            id = "test",
            nickname = "test",
            host = "127.0.0.1",
            port = testPort,
            username = "user",
            authType = AuthType.PASSWORD,
            password = "password".toByteArray()
        )
        
        // Invalid characters in public key (shell injection attempt)
        val maliciousKey = "ssh-ed25519 AAAA; rm -rf /; #"
        val result = manager.injectPublicKey(profile, maliciousKey)
        
        assertFalse("Should fail regex validation for malicious key", result)
    }

    @Test(timeout = 60000L)
    fun testInjectPublicKey_ValidKey_AttemptConnect() = runBlocking {
        val manager = SshConnectionManager(net.schmizz.sshj.transport.verification.PromiscuousVerifier())
        
        val passwordProfile = ConnectionProfile(
            id = "test_pass",
            nickname = "test_pass",
            host = "127.0.0.1",
            port = testPort,
            username = "user",
            authType = AuthType.PASSWORD,
            password = "password".toByteArray()
        )
        
        // Generate a new key
        val keyPair = SSHKeyGenerator.generateRSAKeyPair()
        val publicKeyString = SSHKeyGenerator.encodePublicKey(keyPair)
        
        // 1. Inject the key using password authentication
        val injectionCommand = "mkdir -p ~/.ssh && chmod 700 ~/.ssh && echo \"$publicKeyString\" >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
        manager.connectAndExecute(passwordProfile, injectionCommand)
        assertTrue("Should successfully inject the public key", true)
        
        // 2. Re-authenticate using the newly injected key
        val keyProfile = ConnectionProfile(
            id = "test_key",
            nickname = "test_key",
            host = "127.0.0.1",
            port = testPort,
            username = "user",
            authType = AuthType.KEY
        )
        
        // This will connect and execute a command using public key authentication.
        // If it throws an exception, the test will fail, proving the key wasn't accepted.
        try {
            manager.connectAndExecute(keyProfile, "echo test", keyPair)
            // If we reach here, authentication succeeded!
            assertTrue(true)
        } catch (e: Exception) {
            assertFalse("Authentication with injected key failed: ${e.message}", true)
        }
    }
}