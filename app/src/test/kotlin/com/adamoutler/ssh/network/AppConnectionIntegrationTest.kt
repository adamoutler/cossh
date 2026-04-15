package com.adamoutler.ssh.network

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.command.Command
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope

@RunWith(RobolectricTestRunner::class)
class AppConnectionIntegrationTest {

    private lateinit var sshd: SshServer
    private val testPort = 2223
    private val testUser = "testuser"
    private val testPassword = "testpassword"
    
    // Command input latch to signal we received data
    private val commandLatch = CountDownLatch(1)

    @Before
    fun setUp() {
        sshd = SshServer.setUpDefaultServer()
        sshd.port = testPort
        val provider = SimpleGeneratorHostKeyProvider(Files.createTempFile("host", "key"))
        provider.algorithm = "RSA"
        sshd.keyPairProvider = provider
        
        sshd.passwordAuthenticator = PasswordAuthenticator { username, password, _ ->
            username == testUser && password == testPassword
        }

        // We actually want a custom shell to interact with the user
        sshd.shellFactory = org.apache.sshd.server.shell.ShellFactory {
            object : Command {
                private var out: OutputStream? = null
                private var inStream: InputStream? = null
                private var trueExitCallback: org.apache.sshd.server.ExitCallback? = null
                
                override fun setInputStream(inputStream: InputStream) { this.inStream = inputStream }
                override fun setOutputStream(outputStream: OutputStream) { this.out = outputStream }
                override fun setErrorStream(errStream: OutputStream) {}
                override fun setExitCallback(callback: org.apache.sshd.server.ExitCallback) {
                    this.trueExitCallback = callback
                }

                override fun start(channel: org.apache.sshd.server.channel.ChannelSession?, env: org.apache.sshd.server.Environment?) {
                    Thread {
                        out?.write("SERVER_READY\n".toByteArray())
                        out?.flush()
                        
                        val buffer = ByteArray(1024)
                        val read = inStream?.read(buffer) ?: -1
                        if (read > 0) {
                            val received = String(buffer, 0, read)
                            if (received.contains("HELLO_SERVER")) {
                                out?.write("HELLO_CLIENT\n".toByteArray())
                                out?.flush()
                                commandLatch.countDown()
                            }
                        }
                        Thread.sleep(100)
                        trueExitCallback?.onExit(0)
                    }.start()
                }
                override fun destroy(channel: org.apache.sshd.server.channel.ChannelSession?) {}
            }
        }
        sshd.start()
    }

    @After
    fun tearDown() {
        sshd.stop(true)
        SshSessionProvider.isHeadlessTest = false
        SshSessionProvider.clearSession()
        SshSessionProvider.clearConnections()
    }

    @Test
    fun testInAppTerminalConnectionAndDataTransfer() = runBlocking {
        SshSessionProvider.isHeadlessTest = true // Use mock terminal text initially if needed
        SshSessionProvider.clearSession()
        
        val profile = ConnectionProfile(
            id = "id_integration",
            nickname = "IntegrationServer",
            host = "127.0.0.1",
            port = testPort,
            username = testUser,
            authType = AuthType.PASSWORD,
            password = testPassword.toByteArray()
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
        while (SshSessionProvider.ptyOutputStream == null && retries < 50) {
            delay(100)
            retries++
        }
        assertTrue("Output stream should be initialized", SshSessionProvider.ptyOutputStream != null)

        // Now send data to the server (Simulating user typing)
        SshSessionProvider.ptyOutputStream?.write("HELLO_SERVER\n".toByteArray())
        SshSessionProvider.ptyOutputStream?.flush()

        // Wait for the server to process and respond
        assertTrue("Server did not receive expected data", commandLatch.await(5, TimeUnit.SECONDS))

        // Verify the mock transcript has HELLO_CLIENT
        retries = 0
        var foundOutput = false
        while (retries < 50) {
            if (SshSessionProvider.mockTestTranscript?.contains("HELLO_CLIENT") == true) {
                foundOutput = true
                break
            }
            delay(100)
            retries++
        }
        assertTrue("Output should contain HELLO_CLIENT", foundOutput)

        job.cancel()
    }
}
