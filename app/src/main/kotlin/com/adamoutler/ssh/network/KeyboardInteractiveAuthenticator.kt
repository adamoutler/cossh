package com.adamoutler.ssh.network

import com.adamoutler.ssh.data.ConnectionProfile
import kotlinx.coroutines.runBlocking
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.userauth.method.AuthKeyboardInteractive
import net.schmizz.sshj.userauth.method.ChallengeResponseProvider
import net.schmizz.sshj.userauth.password.Resource
import java.util.Collections

/**
 * Implements RFC 4256 Keyboard-Interactive and PAM authentication with support for
 * multi-stage challenge rounds (e.g. PAM Password -> Duo / TOTP code),
 * bounded exchange depth (max 5 rounds), and immediate volatile memory zero-wiping.
 */
class KeyboardInteractiveAuthenticator(
    private val maxRounds: Int = 5,
    private val isMfa: Boolean = false,
    private val stepDescription: String? = null,
) : SshAuthenticator {

    override fun authenticate(client: SSHClient, profile: ConnectionProfile) {
        val currentUsername = profile.username.ifBlank {
            throw IllegalArgumentException("Username required for keyboard-interactive authentication")
        }

        check(!ConnectionStateRepository.isHeadlessTest) {
            "Headless test: cannot prompt for keyboard-interactive credentials"
        }

        val allocatedBuffers = Collections.synchronizedList(mutableListOf<CharArray>())
        try {
            val provider = createChallengeResponseProvider(profile, allocatedBuffers)
            val authMethod = AuthKeyboardInteractive(provider)
            client.auth(currentUsername, authMethod)
        } finally {
            allocatedBuffers.forEach { it.fill('\u0000') }
            allocatedBuffers.clear()
        }
    }

    internal fun createChallengeResponseProvider(
        profile: ConnectionProfile,
        allocatedBuffers: MutableList<CharArray>,
    ): ChallengeResponseProvider {
        var roundCount = 0
        var currentName = ""
        var currentInstruction = ""

        return object : ChallengeResponseProvider {
            override fun getSubmethods(): List<String> = emptyList()

            override fun init(resource: Resource<*>?, name: String?, instruction: String?) {
                roundCount++
                if (roundCount > maxRounds) {
                    throw UserAuthException("Maximum challenge rounds ($maxRounds) exceeded")
                }
                currentName = name ?: ""
                currentInstruction = instruction ?: ""
            }

            override fun getResponse(prompt: String, echo: Boolean): CharArray {
                val pamPrompt = PamPrompt(prompt = prompt, echo = echo)
                val responses = runBlocking {
                    ConnectionStateRepository.requestKeyboardInteractivePrompt(
                        profileId = profile.id,
                        name = currentName,
                        instruction = currentInstruction,
                        prompts = listOf(pamPrompt),
                        round = roundCount,
                        isPartialAuth = isMfa,
                        completedFactor = stepDescription ?: if (isMfa) "Public Key" else null,
                    )
                } ?: throw UserAuthException("Keyboard-interactive authentication cancelled by user")

                val response = responses.firstOrNull()
                    ?: throw UserAuthException("No response provided for challenge prompt")

                allocatedBuffers.add(response)
                return response
            }

            override fun shouldRetry(): Boolean = false
        }
    }
}
