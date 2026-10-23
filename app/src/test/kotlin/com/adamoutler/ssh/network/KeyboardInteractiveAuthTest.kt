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

class KeyboardInteractiveAuthTest {

    private val testProfile = ConnectionProfile(
        id = "test-pam-profile",
        nickname = "PAM Bastion",
        host = "bastion.example.com",
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
    fun testServerPromptTextSanitization_StripsAnsiAndBidi() {
        val hostileAnsi = "\u001B[31;1mSecurity Warning\u001B[0m: Enter password"
        val sanitizedAnsi = sanitizeServerPromptText(hostileAnsi)
        assertEquals("Security Warning: Enter password", sanitizedAnsi)

        val hostileBidi = "admin\u202E\u2066reversed\u2069\u202C: "
        val sanitizedBidi = sanitizeServerPromptText(hostileBidi)
        assertEquals("adminreversed: ", sanitizedBidi)
    }

    @Test
    fun testHeuristicEchoSafeguard() {
        // Explicit echo false
        val standardPassword = PamPrompt(prompt = "Password: ", echo = false)
        assertTrue(standardPassword.shouldMask)

        // Misconfigured server sending echo true on sensitive keyword prompts
        val echoTruePassword = PamPrompt(prompt = "Password: ", echo = true)
        assertTrue(echoTruePassword.shouldMask)

        val echoTrueOtp = PamPrompt(prompt = "Enter 6-digit OTP code: ", echo = true)
        assertTrue(echoTrueOtp.shouldMask)

        val echoTrueToken = PamPrompt(prompt = "Hardware token PIN: ", echo = true)
        assertTrue(echoTrueToken.shouldMask)

        val echoTruePassphrase = PamPrompt(prompt = "Secondary passphrase: ", echo = true)
        assertTrue(echoTruePassphrase.shouldMask)

        // Non-sensitive prompts with echo true remain unmasked
        val usernamePrompt = PamPrompt(prompt = "Username: ", echo = true)
        assertFalse(usernamePrompt.shouldMask)

        val infoPrompt = PamPrompt(prompt = "Press enter to acknowledge: ", echo = true)
        assertFalse(infoPrompt.shouldMask)
    }

    @Test
    fun testSinglePromptResolution_ReturnsExpectedResponse() {
        val allocatedBuffers = Collections.synchronizedList(mutableListOf<CharArray>())
        val authenticator = KeyboardInteractiveAuthenticator()
        val provider = authenticator.createChallengeResponseProvider(testProfile, allocatedBuffers)

        // Launch async consumer answering the prompt
        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            while (UserPromptRegistry.keyboardInteractivePromptRequest.value == null) {
                Thread.sleep(10)
            }
            val req = UserPromptRegistry.keyboardInteractivePromptRequest.value!!
            assertEquals("test-pam-profile", req.profileId)
            assertEquals("PAM Authentication", req.name)
            assertEquals(1, req.prompts.size)
            assertEquals("Password: ", req.prompts[0].prompt)
            assertEquals(1, req.round)

            UserPromptRegistry.resolveKeyboardInteractivePrompt(listOf("secret123".toCharArray()))
        }

        provider.init(null, "PAM Authentication", "Please enter credentials")
        val resp = provider.getResponse("Password: ", false)
        assertEquals("secret123", String(resp))
        assertEquals(1, allocatedBuffers.size)
    }

    @Test
    fun testMultiStageChallengeExchange_TwoFactorTotp() {
        val allocatedBuffers = Collections.synchronizedList(mutableListOf<CharArray>())
        val authenticator = KeyboardInteractiveAuthenticator()
        val provider = authenticator.createChallengeResponseProvider(testProfile, allocatedBuffers)

        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            // Answer Round 1
            while (UserPromptRegistry.keyboardInteractivePromptRequest.value == null) {
                Thread.sleep(10)
            }
            val req1 = UserPromptRegistry.keyboardInteractivePromptRequest.value!!
            assertEquals(1, req1.round)
            UserPromptRegistry.resolveKeyboardInteractivePrompt(listOf("my-password".toCharArray()))

            // Answer Round 2
            while (UserPromptRegistry.keyboardInteractivePromptRequest.value == null ||
                UserPromptRegistry.keyboardInteractivePromptRequest.value?.round == 1
            ) {
                Thread.sleep(10)
            }
            val req2 = UserPromptRegistry.keyboardInteractivePromptRequest.value!!
            assertEquals(2, req2.round)
            assertEquals("PAM Stage 2", req2.name)
            UserPromptRegistry.resolveKeyboardInteractivePrompt(listOf("123456".toCharArray()))
        }

        // Round 1: Primary Password
        provider.init(null, "PAM Stage 1", "Password Auth")
        val passResp = provider.getResponse("Password: ", false)
        assertEquals("my-password", String(passResp))

        // Round 2: 2FA TOTP code
        provider.init(null, "PAM Stage 2", "2FA Verification")
        val totpResp = provider.getResponse("Enter 6-digit TOTP code: ", false)
        assertEquals("123456", String(totpResp))

        assertEquals(2, allocatedBuffers.size)
    }

    @Test
    fun testBoundedChallengeLoop_ExceedingMaxRoundsAbortsWithException() {
        val allocatedBuffers = Collections.synchronizedList(mutableListOf<CharArray>())
        val authenticator = KeyboardInteractiveAuthenticator(maxRounds = 5)
        val provider = authenticator.createChallengeResponseProvider(testProfile, allocatedBuffers)

        try {
            // Try triggering 6 rounds when max is 5
            for (round in 1..6) {
                provider.init(null, "Loop $round", "Infinite challenge")
            }
            fail("Expected UserAuthException on exceeding max rounds")
        } catch (e: UserAuthException) {
            assertTrue("Message should mention maximum rounds exceeded", e.message?.contains("Maximum challenge rounds") == true)
        }
    }

    @Test
    fun testUserCancellation_AbortsAuthentication() {
        val allocatedBuffers = Collections.synchronizedList(mutableListOf<CharArray>())
        val authenticator = KeyboardInteractiveAuthenticator()
        val provider = authenticator.createChallengeResponseProvider(testProfile, allocatedBuffers)

        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            while (UserPromptRegistry.keyboardInteractivePromptRequest.value == null) {
                Thread.sleep(10)
            }
            // User dismisses dialog
            UserPromptRegistry.resolveKeyboardInteractivePrompt(null)
        }

        provider.init(null, "PAM Auth", "Password prompt")
        try {
            provider.getResponse("Password: ", false)
            fail("Expected UserAuthException on cancellation")
        } catch (e: UserAuthException) {
            assertTrue("Message should mention cancelled by user", e.message?.contains("cancelled by user") == true)
        }
    }
}
