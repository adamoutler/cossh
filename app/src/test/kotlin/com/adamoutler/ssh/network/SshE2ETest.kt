package com.adamoutler.ssh.network

import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SshE2ETest {

    companion object {
        private lateinit var sshContainer: GenericContainer<*>
        const val TEST_USER = "testuser"
        const val TEST_PASSWORD = "testpassword"

        @JvmStatic
        @BeforeClass
        fun setUp() {
            sshContainer = GenericContainer(DockerImageName.parse("lscr.io/linuxserver/openssh-server:latest"))
                .withExposedPorts(2222)
                .withEnv("USER_NAME", TEST_USER)
                .withEnv("USER_PASSWORD", TEST_PASSWORD)
                .withEnv("PASSWORD_ACCESS", "true")
                .withEnv("SUDO_ACCESS", "true")
            sshContainer.start()
            println("Container logs: ${sshContainer.logs}")
            // Wait an extra 5 seconds just to be sure sshd is up
            Thread.sleep(5000)
        }

        @JvmStatic
        @AfterClass
        fun tearDown() {
            sshContainer.stop()
        }
    }

    @Test
    fun testMultiSessionReadWrite() = runBlocking {
        val port = sshContainer.getMappedPort(2222)
        val host = sshContainer.host

        val profile = ConnectionProfile(
            id = "e2e-test",
            nickname = "E2E Server",
            host = host,
            port = port,
            username = TEST_USER,
            authType = AuthType.PASSWORD,
            password = TEST_PASSWORD.toByteArray()
        )

        val manager = SshConnectionManager(net.schmizz.sshj.transport.verification.PromiscuousVerifier())
        
        val latch1 = CountDownLatch(1)
        var session1Output = ""

        // Session 1: Write file
        val job1 = launch(Dispatchers.IO) {
            manager.connectPty(
                profile = profile,
                onConnect = { outStream, _ ->
                    Thread {
                        Thread.sleep(2000) // Wait for shell to be ready
                        outStream.write("echo 'Hello Multi-Session' > /config/testfile.txt\n".toByteArray())
                        outStream.flush()
                        Thread.sleep(1000)
                        outStream.write("exit\n".toByteArray())
                        outStream.flush()
                    }.start()
                },
                onOutput = { bytes, length ->
                    session1Output += String(bytes, 0, length)
                    if (session1Output.contains("exit")) {
                        latch1.countDown()
                    }
                }
            )
        }

        assertTrue("Session 1 timeout", latch1.await(30, TimeUnit.SECONDS))
        job1.cancel()

        // Wait a little before the second session
        Thread.sleep(1000)

        val latch2 = CountDownLatch(1)
        var session2Output = ""

        // Session 2: Read file
        val profile2 = ConnectionProfile(
            id = "e2e-test-2",
            nickname = "E2E Server 2",
            host = host,
            port = port,
            username = TEST_USER,
            authType = AuthType.PASSWORD,
            password = TEST_PASSWORD.toByteArray()
        )

        val job2 = launch(Dispatchers.IO) {
            manager.connectPty(
                profile = profile2,
                onConnect = { outStream, _ ->
                    Thread {
                        Thread.sleep(2000) // Wait for shell
                        outStream.write("cat /config/testfile.txt\n".toByteArray())
                        outStream.flush()
                        Thread.sleep(1000)
                        outStream.write("exit\n".toByteArray())
                        outStream.flush()
                    }.start()
                },
                onOutput = { bytes, length ->
                    session2Output += String(bytes, 0, length)
                    if (session2Output.contains("Hello Multi-Session")) {
                        latch2.countDown()
                    }
                }
            )
        }

        assertTrue("Session 2 timeout or did not find expected text. Output: $session2Output", latch2.await(30, TimeUnit.SECONDS))
        job2.cancel()
    }
}
