package com.adamoutler.ssh.network

import com.adamoutler.ssh.data.ConnectionProfile
import net.schmizz.keepalive.KeepAliveProvider
import net.schmizz.sshj.SSHClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests for SSH keepalive, dead-peer detection configuration,
 * and baseband cellular anti-thrashing validation (SSH-207).
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
    fun connectionProfile_sanitizeKeepAliveInterval_disabledWhenZeroOrNegative() {
        assertEquals(0, ConnectionProfile.sanitizeKeepAliveInterval(0))
        assertEquals(0, ConnectionProfile.sanitizeKeepAliveInterval(-1))
        assertEquals(0, ConnectionProfile.sanitizeKeepAliveInterval(-60))
    }

    @Test
    fun connectionProfile_sanitizeKeepAliveInterval_clampsValuesUnderTenSecondsToPreventRadioThrashing() {
        // Values in range 1..9 must be clamped to 10s minimum to prevent carrier baseband radio thrashing
        for (seconds in 1..9) {
            assertEquals(
                "Keepalive interval $seconds must be clamped to 10s minimum",
                10,
                ConnectionProfile.sanitizeKeepAliveInterval(seconds),
            )
        }
    }

    @Test
    fun connectionProfile_sanitizeKeepAliveInterval_preservesValidPresets() {
        assertEquals(10, ConnectionProfile.sanitizeKeepAliveInterval(10))
        assertEquals(15, ConnectionProfile.sanitizeKeepAliveInterval(15))
        assertEquals(30, ConnectionProfile.sanitizeKeepAliveInterval(30))
        assertEquals(60, ConnectionProfile.sanitizeKeepAliveInterval(60))
    }

    @Test
    fun connectionProfile_effectiveKeepAliveInterval_propertyAppliesSanitization() {
        val profileDisabled = ConnectionProfile(id = "1", nickname = "test", host = "localhost", keepAliveIntervalSeconds = 0)
        val profileThrashed = ConnectionProfile(id = "2", nickname = "test", host = "localhost", keepAliveIntervalSeconds = 5)
        val profileValid = ConnectionProfile(id = "3", nickname = "test", host = "localhost", keepAliveIntervalSeconds = 30)

        assertEquals(0, profileDisabled.effectiveKeepAliveInterval)
        assertEquals(10, profileThrashed.effectiveKeepAliveInterval)
        assertEquals(30, profileValid.effectiveKeepAliveInterval)
    }
}
