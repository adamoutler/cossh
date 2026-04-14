package com.adamoutler.ssh.network

import kotlinx.coroutines.runBlocking
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import org.apache.sshd.server.command.CommandFactory
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.AuthType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SshPtyIntegrationTest {

    private lateinit var sshd: SshServer
    private val testPort = 2223
    private val testUser = "testuser"
    private val testPassword = "testpassword"

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

        sshd.shellFactory = org.apache.sshd.server.shell.ShellFactory {
            object : Command {
                private var out: OutputStream? = null
                private var inStream: InputStream? = null
                private var exitCallback: org.apache.sshd.server.ExitCallback? = null
                
                override fun setInputStream(i: InputStream) { this.inStream = i }
                override fun setOutputStream(o: OutputStream) { this.out = o }
                override fun setErrorStream(errStream: OutputStream) {}
                override fun setExitCallback(callback: org.apache.sshd.server.ExitCallback) {
                    this.exitCallback = callback
                }

                override fun start(channel: ChannelSession?, env: org.apache.sshd.server.Environment?) {
                    Thread {
                        out?.write("Welcome to mock shell\n$ ".toByteArray())
                        out?.flush()
                        
                        val buffer = ByteArray(1024)
                        var read: Int
                        while (true) {
                            try {
                                read = inStream?.read(buffer) ?: -1
                                if (read == -1) break
                                val received = String(buffer, 0, read)
                                out?.write(("echo: " + received).toByteArray())
                                out?.flush()
                                if (received.contains("ls")) break // Exit to finish test
                            } catch (e: Exception) {
                                break
                            }
                        }
                        exitCallback?.onExit(0)
                    }.start()
                }

                override fun destroy(channel: ChannelSession?) {}
            }
        }
        
        sshd.start()
    }

    @After
    fun tearDown() {
        sshd.stop(true)
    }

    @Test
    fun testPtyBiDirectionalIO() = runBlocking {
        val profile = ConnectionProfile(
            id = "pty-test",
            nickname = "PTY Server",
            host = "127.0.0.1",
            port = testPort,
            username = testUser,
            authType = AuthType.PASSWORD,
            password = testPassword.toByteArray()
        )

        val manager = SshConnectionManager(net.schmizz.sshj.transport.verification.PromiscuousVerifier())
        val latch = CountDownLatch(1)
        var receivedOutput = ""

        // Start connection
        val job = launch(Dispatchers.IO) {
            manager.connectPty(
                profile = profile,
                onConnect = { outStream, _ ->
                    SshSessionProvider.ptyOutputStream = outStream
                    // Simulate UI typing
                    Thread {
                        Thread.sleep(500)
                        val text = "ls\n".toByteArray()
                        outStream.write(text)
                        outStream.flush()
                        println("Logcat trace: TerminalScreen: Wrote ${text.size} bytes to SSH PTY stdin")
                    }.start()
                },
                onOutput = { bytes, length ->
                    receivedOutput += String(bytes, 0, length)
                    println("Logcat trace: TerminalScreen: Appended ${length} bytes from SSH PTY stdout")
                    if (receivedOutput.contains("echo: ls")) {
                        latch.countDown()
                    }
                }
            )
        }

        assertTrue("Failed to receive bi-directional echo within timeout", latch.await(5, TimeUnit.SECONDS))
        job.cancel()
    }
}
