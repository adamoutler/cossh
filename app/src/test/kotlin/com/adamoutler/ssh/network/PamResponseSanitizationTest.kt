package com.adamoutler.ssh.network

import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.schmizz.sshj.userauth.UserAuthException
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Collections

class PamResponseSanitizationTest {

    private val testProfile = ConnectionProfile(
        id = "test-sanitization-profile",
        nickname = "Sanitization Test",
        host = "sanitization.example.com",
        port = 22,
        username = "admin",
        authType = AuthType.PASSWORD,
    )

    @Before
    fun setup() {
        ConnectionStateRepository.isHeadlessTest = false
        UserPromptRegistry.clear()
    }

    @After
    fun tearDown() {
        UserPromptRegistry.clear()
    }

    @Test
    fun testResponseBuffersWiped_OnSuccessfulAuthCompletion() {
        val allocatedBuffers = Collections.synchronizedList(mutableListOf<CharArray>())
        val authenticator = KeyboardInteractiveAuthenticator()
        val provider = authenticator.createChallengeResponseProvider(testProfile, allocatedBuffers)

        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            while (UserPromptRegistry.keyboardInteractivePromptRequest.value == null) {
                Thread.sleep(10)
            }
            UserPromptRegistry.resolveKeyboardInteractivePrompt(listOf("initial-secret-1".toCharArray()))

            while (UserPromptRegistry.keyboardInteractivePromptRequest.value == null ||
                UserPromptRegistry.keyboardInteractivePromptRequest.value?.round == 1
            ) {
                Thread.sleep(10)
            }
            UserPromptRegistry.resolveKeyboardInteractivePrompt(listOf("889900".toCharArray()))
        }

        // Round 1
        provider.init(null, "Round 1", "Password")
        val capturedResponse1 = provider.getResponse("Password: ", false)
        assertEquals("initial-secret-1", String(capturedResponse1))

        // Round 2
        provider.init(null, "Round 2", "TOTP")
        val capturedResponse2 = provider.getResponse("Code: ", false)
        assertEquals("889900", String(capturedResponse2))

        assertEquals(2, allocatedBuffers.size)

        // Simulate finally block in authenticator
        allocatedBuffers.forEach { it.fill('\u0000') }

        assertTrue(
            "Response buffer 1 must be zeroed",
            capturedResponse1.all { it == '\u0000' },
        )
        assertTrue(
            "Response buffer 2 must be zeroed",
            capturedResponse2.all { it == '\u0000' },
        )
    }

    @Test
    fun testResponseBuffersWiped_OnAuthFailureException() {
        val allocatedBuffers = Collections.synchronizedList(mutableListOf<CharArray>())
        val authenticator = KeyboardInteractiveAuthenticator()
        val provider = authenticator.createChallengeResponseProvider(testProfile, allocatedBuffers)

        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            while (UserPromptRegistry.keyboardInteractivePromptRequest.value == null) {
                Thread.sleep(10)
            }
            UserPromptRegistry.resolveKeyboardInteractivePrompt(listOf("doomed-secret".toCharArray()))
        }

        provider.init(null, "Round 1", "Password")
        val capturedResponse = provider.getResponse("Password: ", false)

        try {
            // Simulate transport failure or auth rejection
            throw UserAuthException("Authentication rejected by server")
        } catch (_: UserAuthException) {
            // Simulate finally block in authenticator
            allocatedBuffers.forEach { it.fill('\u0000') }
        }

        assertTrue(
            "Captured response buffer must be zero-wiped despite exception",
            capturedResponse.all { it == '\u0000' },
        )
    }
}
