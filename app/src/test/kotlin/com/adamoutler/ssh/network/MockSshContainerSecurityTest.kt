package com.adamoutler.ssh.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MockSshContainerSecurityTest {

    private fun findProjectRoot(): File {
        return generateSequence(File(".").canonicalFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").exists() }
            ?: File(".")
    }

    @Test
    fun testMockSshDirectoryStructureAndFilesExist() {
        val rootDir = findProjectRoot()
        val mockDir = File(rootDir, "docker/mock-ssh")
        assertTrue("docker/mock-ssh directory must exist", mockDir.isDirectory)

        val serverPy = File(mockDir, "server.py")
        val testFuzzPy = File(mockDir, "test_fuzz.py")
        val dockerfile = File(mockDir, "Dockerfile")
        val composeFile = File(mockDir, "docker-compose.yml")
        val requirementsTxt = File(mockDir, "requirements.txt")

        assertTrue("server.py must exist", serverPy.isFile)
        assertTrue("test_fuzz.py must exist", testFuzzPy.isFile)
        assertTrue("Dockerfile must exist", dockerfile.isFile)
        assertTrue("docker-compose.yml must exist", composeFile.isFile)
        assertTrue("requirements.txt must exist", requirementsTxt.isFile)
    }

    @Test
    fun testServerPySecurityHardeningInvariants() {
        val rootDir = findProjectRoot()
        val serverPy = File(rootDir, "docker/mock-ssh/server.py")
        assertTrue("server.py must exist", serverPy.isFile)
        val content = serverPy.readText(Charsets.UTF_8)

        // 1. Thread-safe channel_info tracking keyed by chanid; absence of scalar last_channel_kind
        assertTrue("Must initialize channel_info dictionary", content.contains("self.channel_info = {}"))
        assertTrue("Must initialize threading Lock", content.contains("self.lock = threading.Lock()"))
        assertTrue("Must record direct-tcpip channel info", content.contains("'kind': 'direct-tcpip'"))
        assertFalse("Must not use scalar last_channel_kind", content.contains("self.last_channel_kind"))

        // 2. Strict port-forwarding lockdown to ALLOWED_FORWARD_HOSTS and INTERNAL_HTTP_PORT
        assertTrue("Must define ALLOWED_FORWARD_HOSTS", content.contains("ALLOWED_FORWARD_HOSTS = {'127.0.0.1', 'localhost'}"))
        assertTrue("Must define INTERNAL_HTTP_PORT", content.contains("INTERNAL_HTTP_PORT = int(os.environ.get('MOCK_HTTP_PORT', '32223'))"))
        assertTrue("Must enforce allowed forward check", content.contains("norm_addr in ALLOWED_FORWARD_HOSTS and dest_port == INTERNAL_HTTP_PORT"))
        assertFalse("0.0.0.0 must never be allowed for forwarding", content.contains("'0.0.0.0' in ALLOWED_FORWARD_HOSTS"))

        // 3. Reverse port forwarding rejection
        assertTrue("Must implement check_port_forward_request", content.contains("def check_port_forward_request(self, address, port):"))
        assertTrue("Must reject port forward request with False", content.contains("return False"))

        // 4. Socket timeouts and forward concurrency bounding
        assertTrue("Must define MAX_CONCURRENT_FORWARDS_PER_SESSION", content.contains("MAX_CONCURRENT_FORWARDS_PER_SESSION = 8"))
        assertTrue("Channel socket must have timeout", content.contains("channel.settimeout(30.0)"))
        assertTrue("Destination socket must have timeout", content.contains("dest_sock.settimeout(30.0)"))

        // 5. Environment variable sanitization
        assertTrue("Must implement check_channel_env_request", content.contains("def check_channel_env_request(self, channel, name, value):"))
        assertTrue("Must reject LD_PRELOAD", content.contains("LD_PRELOAD"))

        // 6. Public-key auth
        assertTrue("Must advertise publickey in get_allowed_auths", content.contains("'password,publickey'"))
        assertTrue("Must implement check_auth_publickey", content.contains("def check_auth_publickey(self, username, key):"))
    }

    @Test
    fun testTestFuzzPyCoverageAndSecurityAssertions() {
        val rootDir = findProjectRoot()
        val testFuzzPy = File(rootDir, "docker/mock-ssh/test_fuzz.py")
        assertTrue("test_fuzz.py must exist", testFuzzPy.isFile)
        val content = testFuzzPy.readText(Charsets.UTF_8)

        assertTrue("Must include connection and prompt test", content.contains("def test_connection_and_prompt"))
        assertTrue("Must include random fuzzing test", content.contains("def test_long_random_fuzzing"))
        assertTrue("Must include allowed HTTP only port forwarding test", content.contains("def test_port_forwarding_allowed_http_only"))
        assertTrue("Must include concurrent shell and HTTP test", content.contains("def test_port_forwarding_concurrent_shell_and_http"))
        assertTrue("Must include adversarial rejections test", content.contains("def test_port_forwarding_adversarial_rejections"))
        assertTrue("Must include public key auth test", content.contains("def test_publickey_authentication"))

        // Adversarial target assertions
        assertTrue("Must test 0.0.0.0 rejection", content.contains("0.0.0.0"))
        assertTrue("Must test 169.254.169.254 rejection", content.contains("169.254.169.254"))
        assertTrue("Must test reverse port forward rejection", content.contains("request_port_forward"))
    }

    @Test
    fun testDockerConfigurationSecurity() {
        val rootDir = findProjectRoot()
        val dockerfile = File(rootDir, "docker/mock-ssh/Dockerfile").readText(Charsets.UTF_8)
        val compose = File(rootDir, "docker/mock-ssh/docker-compose.yml").readText(Charsets.UTF_8)

        assertTrue("Dockerfile must use Python 3.12 slim", dockerfile.contains("python:3.12-slim"))
        assertTrue("docker-compose must map port 32222", compose.contains("32222:2222"))
        assertTrue("docker-compose must map port 32223", compose.contains("32223:32223"))
        assertTrue("docker-compose must map port 32224", compose.contains("32224:2323"))
    }

    @Test
    fun testLiveMockSshSubprocessExecution() {
        val rootDir = findProjectRoot()
        val serverPy = File(rootDir, "docker/mock-ssh/server.py")
        val testFuzzPy = File(rootDir, "docker/mock-ssh/test_fuzz.py")

        val canRunPythonParamiko = try {
            val probe = ProcessBuilder("python3", "-c", "import paramiko").start()
            probe.waitFor(5, TimeUnit.SECONDS) && probe.exitValue() == 0
        } catch (_: Exception) {
            false
        }

        if (!canRunPythonParamiko) {
            println("Skipping live Python test: python3 or paramiko not available in execution environment")
            return
        }

        val testSshPort = 37222
        val testHttpPort = 37223
        val testTelnetPort = 37224

        val serverPb = ProcessBuilder("python3", serverPy.absolutePath)
        serverPb.environment()["MOCK_SSH_PORT"] = testSshPort.toString()
        serverPb.environment()["MOCK_HTTP_PORT"] = testHttpPort.toString()
        serverPb.environment()["MOCK_TELNET_PORT"] = testTelnetPort.toString()
        serverPb.redirectErrorStream(true)

        var serverProcess: Process? = null
        try {
            serverProcess = serverPb.start()
            val listeningLatch = CountDownLatch(1)

            Thread {
                try {
                    serverProcess.inputStream.bufferedReader().use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            if (line?.contains("Listening for SSH connections") == true) {
                                listeningLatch.countDown()
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }.start()

            assertTrue(
                "Mock SSH server failed to start listening within 10 seconds",
                listeningLatch.await(10, TimeUnit.SECONDS)
            )

            val testPb = ProcessBuilder("python3", testFuzzPy.absolutePath)
            testPb.environment()["MOCK_SSH_PORT"] = testSshPort.toString()
            testPb.environment()["MOCK_HTTP_PORT"] = testHttpPort.toString()
            testPb.environment()["MOCK_TELNET_PORT"] = testTelnetPort.toString()
            testPb.redirectErrorStream(true)

            val testProcess = testPb.start()
            val testOutput = StringBuilder()
            val testReaderThread = Thread {
                testProcess.inputStream.bufferedReader().use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        testOutput.appendLine(line)
                    }
                }
            }
            testReaderThread.start()

            val finished = testProcess.waitFor(30, TimeUnit.SECONDS)
            testReaderThread.join(2000)

            assertTrue("test_fuzz.py timed out after 30 seconds", finished)
            val exitCode = testProcess.exitValue()
            assertTrue(
                "test_fuzz.py failed with exit code $exitCode. Output:\n$testOutput",
                exitCode == 0
            )
            assertTrue(
                "test_fuzz.py must report all tests completed successfully",
                testOutput.contains("All mock SSH tests completed successfully.")
            )
        } finally {
            serverProcess?.destroyForcibly()
        }
    }
}
