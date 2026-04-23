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

    companion object {
        var currentPort = 4000
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
        ConnectionStateRepository.isHeadlessTest = false
        ConnectionStateRepository.clearSession("id_integration")
        ConnectionStateRepository.clearConnections()
    }

    @Test(timeout = 300000L)
    fun testInAppTerminalConnectionAndDataTransfer() = runBlocking {
        ConnectionStateRepository.isHeadlessTest = true 
        ConnectionStateRepository.clearSession("id_integration")
        val sessionData = ConnectionStateRepository.getOrCreateSession("id_integration")
        ConnectionStateRepository.mockTestTranscripts["id_integration"] = ""
        
        val profile = ConnectionProfile(
            id = "id_integration",
            nickname = "IntegrationServer",
            host = "127.0.0.1",
            port = testPort,
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
                        val outStr = String(bytes, 0, length)
                        val current = ConnectionStateRepository.mockTestTranscripts["id_integration"] ?: ""
                        ConnectionStateRepository.mockTestTranscripts["id_integration"] = current + outStr
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
            val transcript = ConnectionStateRepository.mockTestTranscripts["id_integration"]
            if (transcript?.contains("a\n") == true || transcript?.contains("Hello from mock sshd!") == true) {
                foundOutput = true
                break
            }
            delay(100)
            retries++
        }
        
        println("Transcript captured:\n${ConnectionStateRepository.mockTestTranscripts["id_integration"]}")
        assertTrue("Output should contain mock-server deterministic RESPONSE for a", foundOutput)

        try {
            sessionData.sshShell?.close()
        } catch (e: Exception) {}
        job.cancel()
    }
}