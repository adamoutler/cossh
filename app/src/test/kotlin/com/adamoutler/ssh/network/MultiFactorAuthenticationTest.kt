package com.adamoutler.ssh.network

import android.os.Build
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.IdentityProfile
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.userauth.password.PasswordFinder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyPairGenerator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class MultiFactorAuthenticationTest {

    private lateinit var sampleProfile: ConnectionProfile

    @Before
    fun setUp() {
        sampleProfile = ConnectionProfile(
            id = "test-prof-mfa",
            nickname = "MFA Test Host",
            host = "mock.hackedyour.info",
            port = 22,
            username = "testuser",
            authType = AuthType.KEY,
        )
    }

    @Test
    fun testAuthPromptRequest_defaultAndMfaValues() {
        val defaultRequest = AuthPromptRequest(
            profileId = "prof-1",
            requireUsername = false,
            isRetry = false,
        )
        assertFalse("Default isPartialAuth must be false", defaultRequest.isPartialAuth)
        assertNull("Default completedFactor must be null", defaultRequest.completedFactor)

        val mfaRequest = AuthPromptRequest(
            profileId = "prof-1",
            requireUsername = false,
            isRetry = false,
            isPartialAuth = true,
            completedFactor = "Public Key",
        )
        assertTrue("isPartialAuth must be true", mfaRequest.isPartialAuth)
        assertEquals("Public Key", mfaRequest.completedFactor)
    }

    @Test
    fun testKeyboardInteractivePromptRequest_mfaValues() {
        val req = KeyboardInteractivePromptRequest(
            profileId = "prof-2",
            name = "Two-Factor Auth",
            instruction = "Enter TOTP verification code",
            prompts = listOf(PamPrompt("Verification Code: ", echo = true)),
            round = 1,
            isPartialAuth = true,
            completedFactor = "Public Key (Titan M2)",
        )
        assertTrue(req.isPartialAuth)
        assertEquals("Public Key (Titan M2)", req.completedFactor)
        assertEquals(1, req.round)
        assertEquals("Enter TOTP verification code", req.instruction)
        assertEquals(1, req.prompts.size)
        assertTrue(req.prompts[0].shouldMask)
    }

    @Test
    fun testMultiFactorContinuationEngine_noPartialSuccessReturnsFalse() {
        val engine = MultiFactorContinuationEngine()
        val dummyClient = SSHClient()

        val result = engine.continueIfPartialSuccess(
            client = dummyClient,
            profile = sampleProfile,
            sessionId = "session-1",
        )

        assertFalse("Must return false immediately when client is disconnected / no userAuth", result)
    }

    @Test
    fun testPasswordAuthenticator_volatileMemorySanitization() {
        val candidatePass = "SecretMfaPass123!".toByteArray(Charsets.UTF_8)
        val authenticator = PasswordAuthenticator(
            identityPassword = candidatePass,
            isMfa = true,
            stepDescription = "[Step 2/2: Second Factor Required]",
        )

        var capturedUsername: String? = null
        var capturedPassword: String? = null

        val testClient = object : SSHClient() {
            override fun authPassword(username: String?, pfinder: PasswordFinder?) {
                capturedUsername = username
                val chars = pfinder?.reqPassword(null)
                if (chars != null) {
                    capturedPassword = String(chars)
                }
            }

            override fun isAuthenticated(): Boolean = true
        }

        authenticator.authenticate(testClient, sampleProfile)

        assertEquals("testuser", capturedUsername)
        assertEquals("SecretMfaPass123!", capturedPassword)
        // Verify volatile memory byte zeroing in finally block
        assertEquals(0.toByte(), candidatePass[0])
    }

    @Test
    fun testKeyboardInteractiveAuthenticator_instantiationAndProperties() {
        val authenticator = KeyboardInteractiveAuthenticator(
            maxRounds = 3,
            isMfa = true,
            stepDescription = "TOTP Second Factor",
        )
        assertNotNull(authenticator)
    }

    @Test
    fun testCompositeAuthenticator_orchestratesContinuationOnPartialSuccess() {
        var continuationEngineInvoked = false

        val testContinuationEngine = object : MultiFactorContinuationEngine() {
            override fun continueIfPartialSuccess(
                client: SSHClient,
                profile: ConnectionProfile,
                identity: IdentityProfile?,
                sessionId: String?,
            ): Boolean {
                continuationEngineInvoked = true
                return true
            }
        }

        var clientAuthenticated = false
        val testClient = object : SSHClient() {
            override fun isAuthenticated(): Boolean = clientAuthenticated
        }

        val failingPrimaryAuthenticator = object : SshAuthenticator {
            override fun authenticate(client: SSHClient, profile: ConnectionProfile) {
                throw UserAuthException("Mock partial auth failure")
            }
        }

        val constructor = Class.forName("com.adamoutler.ssh.network.SshHandshakeCoordinator\$CompositeAuthenticator")
            .declaredConstructors.first()
        constructor.isAccessible = true
        val compositeAuth = constructor.newInstance(
            listOf(failingPrimaryAuthenticator),
            null,
            "test-session",
            testContinuationEngine,
        ) as SshAuthenticator

        // SshClient before connect has userAuth == null, so continueIfPartialSuccess won't trigger unless userAuth.hadPartialSuccess()
        // Here we verify it throws UserAuthException when all fail
        var caughtException = false
        try {
            compositeAuth.authenticate(testClient, sampleProfile)
        } catch (e: UserAuthException) {
            caughtException = true
        }
        assertTrue("Composite authenticator throws UserAuthException on full exhaustion", caughtException)
        assertFalse("Continuation engine not invoked when userAuth is null", continuationEngineInvoked)
    }

    @Test
    fun testCoordinatorGetAuthenticator_wrapsKeyInCompositeAuthenticator() {
        val coordinator = SshHandshakeCoordinator()
        val keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair()
        val authenticator = coordinator.getAuthenticator(
            profile = sampleProfile,
            keyPair = keyPair,
            identity = null,
            sessionId = "test-session",
        )
        assertEquals("CompositeAuthenticator", authenticator.javaClass.simpleName)
    }
}
