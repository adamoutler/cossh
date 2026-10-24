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
    val expectedRandomart: String? = null,
    val receivedRandomart: String? = null,
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

data class PamPrompt(
    val prompt: String,
    val echo: Boolean,
) {
    val shouldMask: Boolean
        get() {
            if (!echo) return true
            val lower = prompt.lowercase()
            return lower.contains("password") ||
                lower.contains("passphrase") ||
                lower.contains("token") ||
                lower.contains("otp") ||
                lower.contains("pin") ||
                lower.contains("secret") ||
                lower.contains("code")
        }
}

data class KeyboardInteractivePromptRequest(
    val profileId: String,
    val name: String,
    val instruction: String,
    val prompts: List<PamPrompt>,
    val round: Int,
    val deferred: CompletableDeferred<List<CharArray>?>,
)

/**
 * Strips ANSI terminal escape sequences, Unicode Bidi directional overrides,
 * and non-printable control characters from server prompt strings.
 */
fun sanitizeServerPromptText(input: String): String {
    val withoutAnsi = input.replace(Regex("\u001B\\[[;?0-9]*[a-zA-Z]|\u001B[()][A-Z0-9]|\u001B."), "")
    return withoutAnsi.filter { c ->
        c !in '\u202A'..'\u202E' && c !in '\u2066'..'\u2069' && (c >= ' ' || c == '\n' || c == '\t')
    }
}

/**
 * Manages user interaction requests (host key confirmation, interactive credentials/passwords,
 * and multi-challenge keyboard-interactive / PAM prompts) and their coroutine completion resolution.
 */
object UserPromptRegistry {
    private val _promptRequest = MutableStateFlow<HostKeyPromptRequest?>(null)
    val promptRequest: StateFlow<HostKeyPromptRequest?> = _promptRequest.asStateFlow()

    private val _authPromptRequest = MutableStateFlow<AuthPromptRequest?>(null)
    val authPromptRequest: StateFlow<AuthPromptRequest?> = _authPromptRequest.asStateFlow()

    private val _keyboardInteractivePromptRequest = MutableStateFlow<KeyboardInteractivePromptRequest?>(null)
    val keyboardInteractivePromptRequest: StateFlow<KeyboardInteractivePromptRequest?> = _keyboardInteractivePromptRequest.asStateFlow()

    suspend fun requestKeyboardInteractivePrompt(
        profileId: String,
        name: String,
        instruction: String,
        prompts: List<PamPrompt>,
        round: Int,
    ): List<CharArray>? {
        val sanitizedName = sanitizeServerPromptText(name)
        val sanitizedInstruction = sanitizeServerPromptText(instruction)
        val sanitizedPrompts = prompts.map {
            it.copy(prompt = sanitizeServerPromptText(it.prompt))
        }

        val deferred = CompletableDeferred<List<CharArray>?>()
        _keyboardInteractivePromptRequest.value = KeyboardInteractivePromptRequest(
            profileId = profileId,
            name = sanitizedName,
            instruction = sanitizedInstruction,
            prompts = sanitizedPrompts,
            round = round,
            deferred = deferred,
        )
        return deferred.await()
    }

    fun resolveKeyboardInteractivePrompt(responses: List<CharArray>?) {
        _keyboardInteractivePromptRequest.value?.deferred?.complete(responses)
        _keyboardInteractivePromptRequest.value = null
    }

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
        expectedRandomart: String? = null,
        receivedRandomart: String? = null,
    ): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        _promptRequest.value = HostKeyPromptRequest(
            hostname = hostname,
            expectedFingerprint = expectedFingerprint,
            receivedFingerprint = receivedFingerprint,
            isKeyChanged = isKeyChanged,
            deferred = deferred,
            expectedRandomart = expectedRandomart,
            receivedRandomart = receivedRandomart,
        )
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

    @VisibleForTesting
    fun setKeyboardInteractivePromptRequestForTest(request: KeyboardInteractivePromptRequest?) {
        _keyboardInteractivePromptRequest.value = request
    }

    fun clear() {
        _promptRequest.value?.deferred?.cancel()
        _promptRequest.value = null
        _authPromptRequest.value?.deferred?.cancel()
        _authPromptRequest.value = null
        _keyboardInteractivePromptRequest.value?.deferred?.cancel()
        _keyboardInteractivePromptRequest.value = null
    }
}
