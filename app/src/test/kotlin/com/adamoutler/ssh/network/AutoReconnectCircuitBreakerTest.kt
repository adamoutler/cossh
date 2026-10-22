package com.adamoutler.ssh.network

import net.schmizz.sshj.userauth.UserAuthException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Auto-Reconnect circuit breakers and TOFU zero-prompt guard (SSH-208).
 */
class AutoReconnectCircuitBreakerTest {

    @Test
    fun isCircuitBreakerException_userAuthException_returnsTrue() {
        val authException: Exception = UserAuthException("Authentication failed for user")
        assertTrue("UserAuthException must trigger immediate circuit breaker", SshService.isCircuitBreakerException(authException))
    }

    @Test
    fun isCircuitBreakerException_hostKeyMismatch_returnsTrue() {
        val securityAlert: Exception = SecurityException("HOST_KEY_MISMATCH_SECURITY_ALERT: Host key changed!")
        assertTrue("Host key mismatch must trigger immediate circuit breaker", SshService.isCircuitBreakerException(securityAlert))
    }

    @Test
    fun isCircuitBreakerException_transientIoException_returnsFalse() {
        val socketException: Exception = java.net.SocketTimeoutException("Read timed out")
        assertFalse("Transient socket timeout must NOT trigger circuit breaker", SshService.isCircuitBreakerException(socketException))
    }

    @Test
    fun exponentialBackoff_calculatesExpectedDelays() {
        assertEquals(2000L, SshService.calculateBackoffDelay(1))
        assertEquals(4000L, SshService.calculateBackoffDelay(2))
        assertEquals(8000L, SshService.calculateBackoffDelay(3))
        assertEquals(16000L, SshService.calculateBackoffDelay(4))
        assertEquals(32000L, SshService.calculateBackoffDelay(5))
        assertEquals(60000L, SshService.calculateBackoffDelay(6, maxDelayMs = 60000L))
    }

    @Test
    fun connectionState_reconnecting_preservesAttemptCounters() {
        val state = ConnectionState.Reconnecting(attempt = 2, maxAttempts = 5, message = "Network drop")
        assertEquals(2, state.attempt)
        assertEquals(5, state.maxAttempts)
        assertEquals("Network drop", state.message)
    }
}
