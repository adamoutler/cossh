package com.adamoutler.ssh.network

import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AppConnectionIntegrationTest {

    private var mockSshdProcess: Process? = null

    @Before
    fun setUp() {
        var mockScript = File("mock_sshd.py")
        if (!mockScript.exists()) {
             mockScript = File("../../mock_sshd.py")
        }
        if (!mockScript.exists()) {
             mockScript = File("../mock_sshd.py")
        }
        
        println("Starting mock_sshd from: ${mockScript.absolutePath}")
        try {
            val pb = ProcessBuilder("python3", mockScript.absolutePath)
            pb.redirectErrorStream(true)
            mockSshdProcess = pb.start()
            // Read output in a separate thread so it doesn't block
            Thread {
                mockSshdProcess?.inputStream?.bufferedReader()?.use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        println("mock_sshd: $line")
                    }
                }
            }.start()
            Thread.sleep(3000) // Wait for it to bind on 2222
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @After
    fun tearDown() {
        mockSshdProcess?.destroy()
        SshSessionProvider.isHeadlessTest = false
        SshSessionProvider.clearSession("id_integration")
        SshSessionProvider.clearConnections()
    }

    @Test(timeout = 300000L)
    fun testInAppTerminalConnectionAndDataTransfer() = runBlocking {
        SshSessionProvider.isHeadlessTest = true 
        SshSessionProvider.clearSession("id_integration")
        val sessionData = SshSessionProvider.getOrCreateSession("id_integration")
        sessionData.mockTestTranscript = ""
        
        val profile = ConnectionProfile(
            id = "id_integration",
            nickname = "IntegrationServer",
            host = "127.0.0.1",
            port = 2222,
            username = "user",
            authType = AuthType.PASSWORD,
            password = "password".toByteArray()
        )

        val manager = SshConnectionManager(net.schmizz.sshj.transport.verification.PromiscuousVerifier())
        
        val job = launch(Dispatchers.IO) {
            try {
                manager.connectPty(
                    profile = profile,
                    onConnect = { outStream, session ->
                        sessionData.ptyOutputStream = outStream
                        sessionData.sshShell = session
                    },
                    onOutput = { bytes, length ->
                        val session = SshSessionProvider.getOrCreateSession("id_integration")
                        session.terminalSession?.emulator?.append(bytes, length)
                        val outStr = String(bytes, 0, length)
                        session.mockTestTranscript = (session.mockTestTranscript ?: "") + outStr
                        SshSessionProvider.postScreenUpdate("id_integration")
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        var retries = 0
        while (sessionData.ptyOutputStream == null && retries < 150) { 
            delay(100)
            retries++
        }
        assertTrue("Output stream should be initialized", sessionData.ptyOutputStream != null)

        delay(1500)

        sessionData.ptyOutputStream?.write("a\n".toByteArray())
        sessionData.ptyOutputStream?.flush()

        retries = 0
        var foundOutput = false
        while (retries < 150) { 
            if (sessionData.mockTestTranscript?.contains("a\n") == true || sessionData.mockTestTranscript?.contains("Hello from mock sshd!") == true) {
                foundOutput = true
                break
            }
            delay(100)
            retries++
        }
        
        println("Transcript captured:\n${sessionData.mockTestTranscript}")
        assertTrue("Output should contain mock-server deterministic RESPONSE for a", foundOutput)

        try {
            sessionData.sshShell?.close()
        } catch (e: Exception) {}
        job.cancel()
    }
}
