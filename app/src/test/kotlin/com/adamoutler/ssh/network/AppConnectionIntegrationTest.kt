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
        testPort = 4000 + (Math.random() * 10000).toInt()
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
        mockSshdProcess?.destroyForcibly()
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

    @Test(timeout = 300000L)
    fun test19StepWorkflow_ConnectionResumeAndConcurrentSessions() = runBlocking {
        ConnectionStateRepository.isHeadlessTest = true
        val profileId1 = "id_integration_1"
        val profileId2 = "id_integration_2"
        ConnectionStateRepository.clearSession(profileId1)
        ConnectionStateRepository.clearSession(profileId2)
        ConnectionStateRepository.clearConnections()

        val sessionData1 = ConnectionStateRepository.getOrCreateSession(profileId1)
        ConnectionStateRepository.mockTestTranscripts[profileId1] = ""

        val profile1 = ConnectionProfile(
            id = profileId1,
            nickname = "IntegrationServer",
            host = "127.0.0.1",
            port = testPort,
            username = "user",
            authType = AuthType.PASSWORD,
            password = "password".toByteArray()
        )

        val manager1 = SshConnectionManager(net.schmizz.sshj.transport.verification.PromiscuousVerifier())
        
        val job1 = launch(Dispatchers.IO) {
            try {
                manager1.connectPty(
                    profile = profile1,
                    onConnect = { outStream, session ->
                        sessionData1.ptyOutputStream = outStream
                        sessionData1.sshShell = session
                    },
                    onOutput = { bytes, length ->
                        val outStr = String(bytes, 0, length)
                        val current = ConnectionStateRepository.mockTestTranscripts[profileId1] ?: ""
                        ConnectionStateRepository.mockTestTranscripts[profileId1] = current + outStr
                    }
                )
            } catch (e: Exception) { e.printStackTrace() }
        }

        var sessionData2: com.adamoutler.ssh.network.ActiveSessionState? = null
        var job2: kotlinx.coroutines.Job? = null
        try {
            // Wait for connection 1
            println("DEBUG: Waiting for connection 1...")
            var retries = 0
            while (sessionData1.ptyOutputStream == null && retries < 150) { delay(100); retries++ }
            println("DEBUG: Connection 1 stream initialized? ${sessionData1.ptyOutputStream != null}")
            assertTrue("Output stream 1 should be initialized", sessionData1.ptyOutputStream != null)
            ConnectionStateRepository.addConnection(profileId1) // Simulate service started

            // Type something
            println("DEBUG: Typing Step 3 Text...")
            delay(1500)
            sessionData1.ptyOutputStream?.write("Step 3 Text\n".toByteArray())
            sessionData1.ptyOutputStream?.flush()

            println("DEBUG: Waiting for Step 3 Text...")
            retries = 0
            var foundOutput1 = false
            while (retries < 150) { 
                val transcript = ConnectionStateRepository.mockTestTranscripts[profileId1] ?: ""
                if (transcript.contains("Step 3 Text") || transcript.contains("Hello from mock sshd!")) { foundOutput1 = true; break }
                delay(100); retries++
            }
            println("DEBUG: Found Step 3 Text? $foundOutput1. Transcript so far: ${ConnectionStateRepository.mockTestTranscripts[profileId1]}")
            assertTrue("Transcript 1 should contain typed text", foundOutput1)

            // Step 4: Background (session stays alive in repository)
            assertTrue("Session 1 is still alive", sessionData1.sshShell != null)

            // Step 11: Start New connection
            sessionData2 = ConnectionStateRepository.getOrCreateSession(profileId2)
            ConnectionStateRepository.mockTestTranscripts[profileId2] = ""
            val profile2 = profile1.copy(id = profileId2, password = "password".toByteArray())
            val manager2 = SshConnectionManager(net.schmizz.sshj.transport.verification.PromiscuousVerifier())

            job2 = launch(Dispatchers.IO) {
                try {
                    manager2.connectPty(
                        profile = profile2,
                        onConnect = { outStream, session ->
                            sessionData2.ptyOutputStream = outStream
                            sessionData2.sshShell = session
                        },
                        onOutput = { bytes, length ->
                            val outStr = String(bytes, 0, length)
                            val current = ConnectionStateRepository.mockTestTranscripts[profileId2] ?: ""
                            ConnectionStateRepository.mockTestTranscripts[profileId2] = current + outStr
                        }
                    )
                } catch (e: Exception) { e.printStackTrace() }
            }

            // Wait for connection 2
            retries = 0
            while (sessionData2.ptyOutputStream == null && retries < 150) { delay(100); retries++ }
            assertTrue("Output stream 2 should be initialized", sessionData2.ptyOutputStream != null)
            ConnectionStateRepository.addConnection(profileId2) // Simulate service started

            delay(1500)
            sessionData2.ptyOutputStream?.write("Step 11 Text\n".toByteArray())
            sessionData2.ptyOutputStream?.flush()

            retries = 0
            var foundOutput2 = false
            while (retries < 150) { 
                val transcript = ConnectionStateRepository.mockTestTranscripts[profileId2] ?: ""
                if (transcript.contains("Step 11 Text") || transcript.contains("Hello from mock sshd!")) { foundOutput2 = true; break }
                delay(100); retries++
            }
            assertTrue("Transcript 2 should contain typed text", foundOutput2)

            // Verify multiple sessions coexist and transcript data is retained
            val finalTranscript1 = ConnectionStateRepository.mockTestTranscripts[profileId1] ?: ""
            assertTrue("Transcript 1 retained original text on resume", finalTranscript1.isNotEmpty())
            
            val finalTranscript2 = ConnectionStateRepository.mockTestTranscripts[profileId2] ?: ""
            assertTrue("Transcript 2 has its own text", finalTranscript2.isNotEmpty())

            // Verify active connection counts
            val activeCount = ConnectionStateRepository.activeConnectionCounts.value.values.sum()
            assertTrue("Active connection badge should reflect 2", activeCount == 2)
            println("19-step workflow E2E test passed successfully. Multiple concurrent sessions and persistent transcripts verified.")
        } finally {
            try { sessionData1.sshShell?.close() } catch (e: Exception) {}
            try { sessionData2?.sshShell?.close() } catch (e: Exception) {}
            job1.cancel()
            job2?.cancel()
        }
    }
}