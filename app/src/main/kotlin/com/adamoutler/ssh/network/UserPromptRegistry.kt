package com.adamoutler.ssh.network

import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HostKeyPromptRequest(
    val hostname: String,
    // Null if completely new host
    val expectedFingerprint: String?,
    val receivedFingerprint: String,
    // True if MITM warning
    val isKeyChanged: Boolean,
    val deferred: CompletableDeferred<Boolean>,
)

data class AuthCredentials(val username: String, val password: CharArray?) : AutoCloseable {
    fun clear() {
        password?.fill('\u0000')
    }

    override fun close() = clear()
}

data class AuthPromptRequest(
    val profileId: String,
    val requireUsername: Boolean,
    val isRetry: Boolean, // True if prompt is due to UserAuthException
    val deferred: CompletableDeferred<AuthCredentials?>,
)

/**
 * Manages user interaction requests (host key confirmation, interactive credentials/passwords)
 * and their coroutine completion resolution.
 */
object UserPromptRegistry {
    private val _promptRequest = MutableStateFlow<HostKeyPromptRequest?>(null)
    val promptRequest: StateFlow<HostKeyPromptRequest?> = _promptRequest.asStateFlow()

    private val _authPromptRequest = MutableStateFlow<AuthPromptRequest?>(null)
    val authPromptRequest: StateFlow<AuthPromptRequest?> = _authPromptRequest.asStateFlow()

    suspend fun requestAuthPrompt(profileId: String, requireUsername: Boolean, isRetry: Boolean): AuthCredentials? {
        val deferred = CompletableDeferred<AuthCredentials?>()
        _authPromptRequest.value = AuthPromptRequest(profileId, requireUsername, isRetry, deferred)
        return deferred.await()
    }

    fun resolveAuthPrompt(credentials: AuthCredentials?) {
        _authPromptRequest.value?.deferred?.complete(credentials)
        _authPromptRequest.value = null
    }

    suspend fun requestPrompt(
        hostname: String,
        expectedFingerprint: String?,
        receivedFingerprint: String,
        isKeyChanged: Boolean,
    ): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        _promptRequest.value = HostKeyPromptRequest(hostname, expectedFingerprint, receivedFingerprint, isKeyChanged, deferred)
        return deferred.await()
    }

    fun resolvePrompt(accept: Boolean) {
        _promptRequest.value?.deferred?.complete(accept)
        _promptRequest.value = null
    }

    @VisibleForTesting
    fun setPromptRequestForTest(request: HostKeyPromptRequest?) {
        _promptRequest.value = request
    }

    @VisibleForTesting
    fun setAuthPromptRequestForTest(request: AuthPromptRequest?) {
        _authPromptRequest.value = request
    }

    fun clear() {
        _promptRequest.value?.deferred?.cancel()
        _promptRequest.value = null
        _authPromptRequest.value?.deferred?.cancel()
        _authPromptRequest.value = null
    }
}
