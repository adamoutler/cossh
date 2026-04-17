package com.adamoutler.ssh.network

import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

@RunWith(RobolectricTestRunner::class)
class AppConnectionIntegrationTest {

    @After
    fun tearDown() {
        SshSessionProvider.isHeadlessTest = false
        SshSessionProvider.clearSession()
        SshSessionProvider.clearConnections()
    }

    @Test
    fun testInAppTerminalConnectionAndDataTransfer() = runBlocking {
        SshSessionProvider.isHeadlessTest = true // Use mock terminal text initially if needed
        SshSessionProvider.clearSession()
        SshSessionProvider.mockTestTranscript = ""
        
        val profile = ConnectionProfile(
            id = "id_integration",
            nickname = "IntegrationServer",
            host = "mock.hackedyour.info",
            port = 32222,
            username = "testuser",
            authType = AuthType.PASSWORD,
            password = "testpassword".toByteArray()
        )

        val manager = SshConnectionManager(net.schmizz.sshj.transport.verification.PromiscuousVerifier())
        
        val job = launch(Dispatchers.IO) {
            try {
                manager.connectPty(
                    profile = profile,
                    onConnect = { outStream, session ->
                        SshSessionProvider.ptyOutputStream = outStream
                        SshSessionProvider.activeSshSession = session
                    },
                    onOutput = { bytes, length ->
                        val session = SshSessionProvider.getOrCreateSession()
                        session?.emulator?.append(bytes, length)
                        val outStr = String(bytes, 0, length)
                        SshSessionProvider.mockTestTranscript = (SshSessionProvider.mockTestTranscript ?: "") + outStr
                        SshSessionProvider.onScreenUpdated?.invoke()
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Wait for connection to establish
        var retries = 0
        while (SshSessionProvider.ptyOutputStream == null && retries < 150) { // 15s timeout
            delay(100)
            retries++
        }
        assertTrue("Output stream should be initialized", SshSessionProvider.ptyOutputStream != null)

        // Give the mock server a moment to send the welcome banner
        delay(1500)

        // Now send data to the server (Simulating user typing)
        SshSessionProvider.ptyOutputStream?.write("HELLO_SERVER\n".toByteArray())
        SshSessionProvider.ptyOutputStream?.flush()

        // Verify the mock transcript has processed HELLO_SERVER and originated its deterministic structure
        retries = 0
        var foundOutput = false
        while (retries < 150) { // Wait up to 15s for the network to respond
            if (SshSessionProvider.mockTestTranscript?.contains("RESPONSE 1, 'HELLO_SERVER'") == true) {
                foundOutput = true
                break
            }
            delay(100)
            retries++
        }
        
        println("Transcript captured:\\n${SshSessionProvider.mockTestTranscript}")
        
        assertTrue("Output should contain mock-server deterministic RESPONSE for HELLO_SERVER", foundOutput)

        job.cancel()
    }
}
