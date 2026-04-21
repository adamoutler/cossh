package com.adamoutler.ssh.network

import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.security.MessageDigest
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
class FiftyKbIntegrityTest {

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
        ConnectionStateRepository.clearSession("id_50kb")
        ConnectionStateRepository.clearConnections()
    }

    private fun sha256(input: String): String {
        val bytes = input.toByteArray(Charsets.UTF_8)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    @Test(timeout = 300000L)
    fun test50KbDataIntegrity() = runBlocking {
        ConnectionStateRepository.isHeadlessTest = true 
        ConnectionStateRepository.clearSession("id_50kb")
        val sessionData = ConnectionStateRepository.getOrCreateSession("id_50kb")
        ConnectionStateRepository.mockTestTranscripts["id_50kb"] = ""
        
        val profile = ConnectionProfile(
            id = "id_50kb",
            nickname = "IntegrityServer",
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
                        val outStr = String(bytes, 0, length)
                        val current = ConnectionStateRepository.mockTestTranscripts["id_50kb"] ?: ""
                        ConnectionStateRepository.mockTestTranscripts["id_50kb"] = current + outStr
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

        delay(1500) // Give connection time to settle and send MOTD

        // Clear transcript to remove MOTD from our 50kb capture
        ConnectionStateRepository.mockTestTranscripts["id_50kb"] = ""

        // Generate 50kb of standard ASCII-compliant typable characters
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+-=[]{}|;':,./<>? "
        val sb = StringBuilder(50 * 1024)
        for (i in 0 until 50 * 1024) {
            sb.append(chars[Random.nextInt(chars.length)])
        }
        val generatedData = sb.toString()
        val expectedHash = sha256(generatedData)

        println("Generated 50kb ASCII data. Local SHA256: $expectedHash")

        // We write in chunks to avoid overwhelming the local pipes or SSHJ buffers all at once
        val chunkSizeBytes = 4096
        var offset = 0
        val dataBytes = generatedData.toByteArray()
        while (offset < dataBytes.size) {
            val length = minOf(chunkSizeBytes, dataBytes.size - offset)
            sessionData.ptyOutputStream?.write(dataBytes, offset, length)
            sessionData.ptyOutputStream?.flush()
            offset += length
            delay(10) // tiny delay to allow reading
        }

        println("Data transmitted through terminal.")

        retries = 0
        var remoteData = ""
        while (retries < 300) { 
            val transcript = ConnectionStateRepository.mockTestTranscripts["id_50kb"] ?: ""
            if (transcript.length >= generatedData.length) {
                // mock_sshd.py might send Windows newlines (\r\n) or we might have some extra bytes
                // But it's an exact byte echo server, so length should match.
                remoteData = transcript.substring(0, generatedData.length)
                break
            }
            delay(100)
            retries++
        }

        val outputFile = File("docs/qa/SSH-50-output.txt")
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(remoteData)
        
        val actualHash = sha256(remoteData)
        println("Data received and written to ${outputFile.absolutePath}. Remote SHA256: $actualHash")

        assertEquals("The local SHA256 hash must match the remote SHA256 hash", expectedHash, actualHash)

        try {
            sessionData.sshShell?.close()
        } catch (e: Exception) {}
        job.cancel()
    }
}
