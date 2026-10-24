package com.adamoutler.ssh.network

import com.adamoutler.ssh.data.KnockProtocol
import com.adamoutler.ssh.data.PortKnockStep
import com.adamoutler.ssh.data.PortKnockingConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class PortKnockingManagerTest {

    @Before
    fun setUp() {
        PortKnockingManager.resetCooldownForTesting()
    }

    @After
    fun tearDown() {
        PortKnockingManager.resetCooldownForTesting()
    }

    @Test
    fun `executePortKnockSequence with empty steps returns 0`() = runTest {
        val config = PortKnockingConfig(steps = emptyList())
        val count = PortKnockingManager.executePortKnockSequence("192.168.1.100", config)
        assertEquals(0, count)
    }

    @Test
    fun `executePortKnockSequence dispatches sequential TCP and UDP knocks`() = runTest {
        val steps = listOf(
            PortKnockStep(7000, KnockProtocol.TCP),
            PortKnockStep(8000, KnockProtocol.UDP),
            PortKnockStep(9000, KnockProtocol.TCP),
        )
        val config = PortKnockingConfig(
            steps = steps,
            stepDelayMs = 0L,
            postKnockDelayMs = 0L,
            probeTimeoutMs = 250,
        )

        val tcpProbes = mutableListOf<Pair<String, Int>>()
        val udpProbes = mutableListOf<Pair<String, Int>>()
        val progressReports = mutableListOf<Triple<PortKnockStep, Int, Int>>()

        val count = PortKnockingManager.executePortKnockSequence(
            host = "myserver.example.com",
            config = config,
            onProgress = { step, idx, total ->
                progressReports.add(Triple(step, idx, total))
            },
            socketConnect = { host, port, timeoutMs ->
                assertEquals("myserver.example.com", host)
                assertEquals(250, timeoutMs)
                tcpProbes.add(host to port)
            },
            udpSend = { host, port ->
                assertEquals("myserver.example.com", host)
                udpProbes.add(host to port)
            },
        )

        assertEquals(3, count)
        assertEquals(listOf("myserver.example.com" to 7000, "myserver.example.com" to 9000), tcpProbes)
        assertEquals(listOf("myserver.example.com" to 8000), udpProbes)
        assertEquals(3, progressReports.size)
        assertEquals(1, progressReports[0].second)
        assertEquals(3, progressReports[0].third)
        assertEquals(7000, progressReports[0].first.port)
        assertEquals(3, progressReports[2].second)
        assertEquals(9000, progressReports[2].first.port)
    }

    @Test
    fun `executePortKnockSequence handles socket timeout or refusal gracefully`() = runTest {
        val steps = listOf(
            PortKnockStep(1111, KnockProtocol.TCP),
            PortKnockStep(2222, KnockProtocol.TCP),
        )
        val config = PortKnockingConfig(steps = steps, stepDelayMs = 0L, postKnockDelayMs = 0L)

        val attemptedPorts = mutableListOf<Int>()
        val count = PortKnockingManager.executePortKnockSequence(
            host = "firewall.local",
            config = config,
            socketConnect = { _, port, _ ->
                attemptedPorts.add(port)
                throw IOException("Connection refused (expected for port knock)")
            },
        )

        assertEquals(2, count)
        assertEquals(listOf(1111, 2222), attemptedPorts)
    }

    @Test
    fun `isRateLimited enforces 5-second cooldown per host`() = runTest {
        val host = "protected.internal"
        val steps = listOf(PortKnockStep(4444, KnockProtocol.TCP))
        val config = PortKnockingConfig(steps = steps, stepDelayMs = 0L, postKnockDelayMs = 0L)

        assertFalse(PortKnockingManager.isRateLimited(host))

        val count1 = PortKnockingManager.executePortKnockSequence(
            host = host,
            config = config,
            socketConnect = { _, _, _ -> },
        )
        assertEquals(1, count1)
        assertTrue(PortKnockingManager.isRateLimited(host))

        // Immediately executing again should be blocked by rate limit cooldown
        val count2 = PortKnockingManager.executePortKnockSequence(
            host = host,
            config = config,
            socketConnect = { _, _, _ -> },
        )
        assertEquals(0, count2)

        // Simulating expiry after 5001ms
        val simulatedExpiry = System.currentTimeMillis() + 5001L
        assertFalse(PortKnockingManager.isRateLimited(host, currentTimeMs = simulatedExpiry))
    }
}
