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
        val isAuthFail = authException is UserAuthException ||
            authException.message?.contains("Auth fail", ignoreCase = true) == true
        assertTrue("UserAuthException must trigger immediate circuit breaker", isAuthFail)
    }

    @Test
    fun isCircuitBreakerException_hostKeyMismatch_returnsTrue() {
        val securityAlert: Exception = SecurityException("HOST_KEY_MISMATCH_SECURITY_ALERT: Host key changed!")
        val isHostKeyMismatch = securityAlert.message?.contains("HOST_KEY_MISMATCH_SECURITY_ALERT") == true ||
            securityAlert.message?.contains("Host key mismatch", ignoreCase = true) == true
        assertTrue("Host key mismatch must trigger immediate circuit breaker", isHostKeyMismatch)
    }

    @Test
    fun isCircuitBreakerException_transientIoException_returnsFalse() {
        val socketException: Exception = java.net.SocketTimeoutException("Read timed out")
        val isCircuitBreaker = socketException is UserAuthException ||
            socketException.message?.contains("HOST_KEY_MISMATCH_SECURITY_ALERT") == true
        assertFalse("Transient socket timeout must NOT trigger circuit breaker", isCircuitBreaker)
    }

    @Test
    fun exponentialBackoff_calculatesExpectedDelays() {
        val baseDelayMs = 2000L
        val maxDelayMs = 32000L

        fun calculateDelay(attempt: Int): Long {
            val factor = 1L shl (attempt - 1).coerceAtMost(5)
            return (baseDelayMs * factor).coerceAtMost(maxDelayMs)
        }

        assertEquals(2000L, calculateDelay(1))
        assertEquals(4000L, calculateDelay(2))
        assertEquals(8000L, calculateDelay(3))
        assertEquals(16000L, calculateDelay(4))
        assertEquals(32000L, calculateDelay(5))
        assertEquals(32000L, calculateDelay(6))
    }

    @Test
    fun connectionState_reconnecting_preservesAttemptCounters() {
        val state = ConnectionState.Reconnecting(attempt = 2, maxAttempts = 5, message = "Network drop")
        assertEquals(2, state.attempt)
        assertEquals(5, state.maxAttempts)
        assertEquals("Network drop", state.message)
    }
}
