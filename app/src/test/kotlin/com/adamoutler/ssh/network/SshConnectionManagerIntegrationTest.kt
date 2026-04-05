package com.adamoutler.ssh.network

import com.adamoutler.ssh.crypto.SSHKeyGenerator
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import kotlinx.coroutines.runBlocking
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator
import org.apache.sshd.server.command.Command
import org.apache.sshd.server.command.CommandFactory
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.server.session.ServerSession
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files

class SshConnectionManagerIntegrationTest {

    private lateinit var sshd: SshServer
    private val testPort = 2222
    private val testUser = "testuser"
    private val testPassword = "testpassword"

    @Before
    fun setUp() {
        sshd = SshServer.setUpDefaultServer()
        sshd.port = testPort
        sshd.keyPairProvider = SimpleGeneratorHostKeyProvider(Files.createTempFile("host", "key"))
        
        sshd.passwordAuthenticator = PasswordAuthenticator { username, password, _ ->
            username == testUser && password == testPassword
        }
        
        sshd.publickeyAuthenticator = PublickeyAuthenticator { username, key, _ ->
            username == testUser // Simplified for testing
        }

        sshd.commandFactory = CommandFactory { channel, command ->
            object : Command {
                private var out: OutputStream? = null
                private var exitCallback: org.apache.sshd.server.Environment? = null
                private var trueExitCallback: org.apache.sshd.server.ExitCallback? = null
                
                override fun setInputStream(inStream: InputStream) {}
                override fun setOutputStream(outStream: OutputStream) {
                    this.out = outStream
                }
                override fun setErrorStream(errStream: OutputStream) {}
                override fun setExitCallback(callback: org.apache.sshd.server.ExitCallback) {
                    this.trueExitCallback = callback
                }

                override fun start(channel: org.apache.sshd.server.channel.ChannelSession?, env: org.apache.sshd.server.Environment?) {
                    Thread {
                        if (command == "echo \"CoSSH_Test\"") {
                            out?.write("CoSSH_Test\n".toByteArray())
                        } else {
                            out?.write("Unknown command\n".toByteArray())
                        }
                        out?.flush()
                        out?.close()
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
    }

    @Test
    fun testHeadlessPasswordConnectionAndCommandExecution() = runBlocking {
        val profile = ConnectionProfile(
            id = "test-1",
            nickname = "Test Server",
            host = "127.0.0.1",
            port = testPort,
            username = testUser,
            authType = AuthType.PASSWORD,
            password = testPassword.toByteArray()
        )

        val manager = SshConnectionManager(net.schmizz.sshj.transport.verification.PromiscuousVerifier())
        val output = manager.connectAndExecute(profile, "echo \"CoSSH_Test\"")

        assertEquals("CoSSH_Test\n", output)
    }

    @Test
    fun testHeadlessPasswordConnectionFailsAndClearsMemory() = runBlocking {
        val passwordBytes = "wrongpassword".toByteArray()
        val profile = ConnectionProfile(
            id = "test-fail",
            nickname = "Test Server",
            host = "127.0.0.1",
            port = testPort,
            username = testUser,
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
            host = "127.0.0.1",
            port = testPort,
            username = testUser,
            authType = AuthType.KEY
        )

        val manager = SshConnectionManager(net.schmizz.sshj.transport.verification.PromiscuousVerifier())
        val output = manager.connectAndExecute(profile, "echo \"CoSSH_Test\"", keyPair)

        assertEquals("CoSSH_Test\n", output)
    }
}