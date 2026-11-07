package com.adamoutler.ssh.network

import com.adamoutler.ssh.data.ConnectionProfile
import kotlinx.coroutines.runBlocking
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.Resource
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

class PasswordAuthenticator(
    private val identityPassword: ByteArray? = null,
    private val isMfa: Boolean = false,
    private val stepDescription: String? = null,
) : SshAuthenticator {
    override fun authenticate(client: SSHClient, profile: ConnectionProfile) {
        var currentUsername = profile.username
        val originalPasswordBytes = identityPassword ?: profile.password
        var activePasswordBytes = originalPasswordBytes
        var isRetry = false

        try {
            while (true) {
                val requirePrompt = (!isMfa && currentUsername.isBlank()) || activePasswordBytes == null || isRetry
                var passwordChars: CharArray? = null

                try {
                    if (requirePrompt) {
                        check(!ConnectionStateRepository.isHeadlessTest) { "Headless test: cannot prompt for credentials" }
                        // Wait indefinitely for user input
                        val credentials = runBlocking {
                            ConnectionStateRepository.requestAuthPrompt(
                                profileId = profile.id,
                                requireUsername = if (isMfa) false else currentUsername.isBlank(),
                                isRetry = isRetry,
                                isPartialAuth = isMfa,
                                completedFactor = stepDescription ?: if (isMfa) "Public Key" else null,
                            )
                        } ?: throw UserAuthException("Authentication cancelled by user")

                        if (!isMfa && credentials.username.isNotBlank()) {
                            currentUsername = credentials.username
                        }
                        passwordChars = credentials.password
                    } else {
                        passwordChars = activePasswordBytes?.let { decodeUtf8BytesToChars(it) }
                    }

                    requireNotNull(passwordChars) { "Password required for auth" }

                    val finalChars = passwordChars
                    val passwordFinder = object : PasswordFinder {
                        override fun reqPassword(resource: Resource<*>?): CharArray = finalChars
                        override fun shouldRetry(resource: Resource<*>?): Boolean = false
                    }

                    // Attempt authentication
                    client.authPassword(currentUsername, passwordFinder)
                    return // Exit loop on success
                } catch (e: UserAuthException) {
                    // Prepare for retry on auth failure
                    isRetry = true
                    activePasswordBytes = null // Invalidate bad password
                } finally {
                    // Ensure volatile state sanitization
                    passwordChars?.fill('\u0000')
                }
            }
        } finally {
            originalPasswordBytes?.fill(0)
        }
    }

    companion object {
        fun decodeUtf8BytesToChars(bytes: ByteArray): CharArray {
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
            val byteBuffer = ByteBuffer.wrap(bytes)
            val charBuffer = decoder.decode(byteBuffer)
            try {
                val chars = CharArray(charBuffer.remaining())
                charBuffer.get(chars)
                return chars
            } finally {
                if (charBuffer.hasArray()) {
                    charBuffer.array().fill('\u0000')
                }
            }
        }
    }
}
