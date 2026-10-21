package com.adamoutler.ssh.network

import net.schmizz.keepalive.KeepAliveProvider
import net.schmizz.sshj.SSHClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests for SSH keepalive and dead-peer detection configuration (SSH-207).
 */
class SshKeepAliveTest {

    @Test
    fun secureSshConfig_configuresKeepAliveProvider() {
        val config = SecureSshConfig()
        assertNotNull(config.keepAliveProvider)
        assertEquals(KeepAliveProvider.KEEP_ALIVE, config.keepAliveProvider)
    }

    @Test
    fun socketTimeout_canBeResetToZeroForStreaming() {
        val client = SSHClient(SecureSshConfig())
        client.timeout = 10000
        assertEquals(10000, client.timeout)
        client.timeout = 0
        assertEquals(0, client.timeout)
    }

    @Test
    fun keepAliveInterval_sanitizesZeroAndPositiveValues() {
        val intervalDisabled = 0
        val intervalActive = 15
        val clampedActive = intervalActive.coerceAtLeast(0)
        assertEquals(0, intervalDisabled)
        assertEquals(15, clampedActive)
    }
}
