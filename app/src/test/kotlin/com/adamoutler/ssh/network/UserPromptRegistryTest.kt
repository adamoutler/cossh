package com.adamoutler.ssh.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UserPromptRegistryTest {

    @Before
    fun setUp() {
        UserPromptRegistry.clear()
    }

    @After
    fun tearDown() {
        UserPromptRegistry.clear()
    }

    @Test
    fun testRequestAuthPrompt_successfulResolution() = runBlocking {
        launch {
            // Wait until the prompt is active
            while (UserPromptRegistry.authPromptRequest.value == null) {
                delay(10)
            }
            val request = UserPromptRegistry.authPromptRequest.value
            assertNotNull(request)
            assertEquals("test-profile", request?.profileId)
            assertTrue(request?.requireUsername == true)
            assertFalse(request?.isRetry == true)

            UserPromptRegistry.resolveAuthPrompt(AuthCredentials("cossh_user", "super_secret".toCharArray()))
        }

        val result = UserPromptRegistry.requestAuthPrompt("test-profile", requireUsername = true, isRetry = false)

        assertNotNull(result)
        assertEquals("cossh_user", result?.username)
        assertArrayEquals("super_secret".toCharArray(), result?.password)
        // Ensure state flow is cleared after resolution
        assertNull(UserPromptRegistry.authPromptRequest.value)
    }

    @Test
    fun testRequestAuthPrompt_cancelledOrNullResolution() = runBlocking {
        launch {
            while (UserPromptRegistry.authPromptRequest.value == null) {
                delay(10)
            }
            val request = UserPromptRegistry.authPromptRequest.value
            assertNotNull(request)
            assertEquals("retry-profile", request?.profileId)
            assertFalse(request?.requireUsername == true)
            assertTrue(request?.isRetry == true)

            UserPromptRegistry.resolveAuthPrompt(null)
        }

        val result = UserPromptRegistry.requestAuthPrompt("retry-profile", requireUsername = false, isRetry = true)

        assertNull(result)
        assertNull(UserPromptRegistry.authPromptRequest.value)
    }

    @Test
    fun testRequestPrompt_acceptResolution() = runBlocking {
        launch {
            while (UserPromptRegistry.promptRequest.value == null) {
                delay(10)
            }
            val request = UserPromptRegistry.promptRequest.value
            assertNotNull(request)
            assertEquals("ssh.example.com", request?.hostname)
            assertNull(request?.expectedFingerprint)
            assertEquals("SHA256:abc123xyz", request?.receivedFingerprint)
            assertFalse(request?.isKeyChanged == true)

            UserPromptRegistry.resolvePrompt(true)
        }

        val accepted = UserPromptRegistry.requestPrompt(
            hostname = "ssh.example.com",
            expectedFingerprint = null,
            receivedFingerprint = "SHA256:abc123xyz",
            isKeyChanged = false,
        )

        assertTrue(accepted)
        assertNull(UserPromptRegistry.promptRequest.value)
    }

    @Test
    fun testRequestPrompt_rejectMitmKeyChanged() = runBlocking {
        launch {
            while (UserPromptRegistry.promptRequest.value == null) {
                delay(10)
            }
            val request = UserPromptRegistry.promptRequest.value
            assertNotNull(request)
            assertEquals("host.mitm.com", request?.hostname)
            assertEquals("SHA256:original", request?.expectedFingerprint)
            assertEquals("SHA256:tampered", request?.receivedFingerprint)
            assertTrue(request?.isKeyChanged == true)

            UserPromptRegistry.resolvePrompt(false)
        }

        val accepted = UserPromptRegistry.requestPrompt(
            hostname = "host.mitm.com",
            expectedFingerprint = "SHA256:original",
            receivedFingerprint = "SHA256:tampered",
            isKeyChanged = true,
        )

        assertFalse(accepted)
        assertNull(UserPromptRegistry.promptRequest.value)
    }

    @Test
    fun testSetPromptRequestForTest() {
        val deferred = CompletableDeferred<Boolean>()
        val testRequest = HostKeyPromptRequest(
            hostname = "test.host",
            expectedFingerprint = null,
            receivedFingerprint = "fp",
            isKeyChanged = false,
            deferred = deferred,
        )

        UserPromptRegistry.setPromptRequestForTest(testRequest)
        assertEquals(testRequest, UserPromptRegistry.promptRequest.value)

        UserPromptRegistry.setPromptRequestForTest(null)
        assertNull(UserPromptRegistry.promptRequest.value)
    }

    @Test
    fun testSetAuthPromptRequestForTest() {
        val deferred = CompletableDeferred<AuthCredentials?>()
        val testRequest = AuthPromptRequest(
            profileId = "profile-test",
            requireUsername = true,
            isRetry = false,
            deferred = deferred,
        )

        UserPromptRegistry.setAuthPromptRequestForTest(testRequest)
        assertEquals(testRequest, UserPromptRegistry.authPromptRequest.value)

        UserPromptRegistry.setAuthPromptRequestForTest(null)
        assertNull(UserPromptRegistry.authPromptRequest.value)
    }

    @Test
    fun testClearCancelsPendingDeferred() {
        val deferredHost = CompletableDeferred<Boolean>()
        val hostRequest = HostKeyPromptRequest("h", null, "f", false, deferredHost)
        UserPromptRegistry.setPromptRequestForTest(hostRequest)

        val deferredAuth = CompletableDeferred<AuthCredentials?>()
        val authRequest = AuthPromptRequest("p", true, false, deferredAuth)
        UserPromptRegistry.setAuthPromptRequestForTest(authRequest)

        UserPromptRegistry.clear()

        assertNull(UserPromptRegistry.promptRequest.value)
        assertNull(UserPromptRegistry.authPromptRequest.value)
        assertTrue(deferredHost.isCancelled)
        assertTrue(deferredAuth.isCancelled)
    }

    @Test
    fun testBackwardsCompatibilityViaConnectionStateRepository() = runBlocking {
        launch {
            while (ConnectionStateRepository.promptRequest.value == null) {
                delay(10)
            }
            ConnectionStateRepository.resolvePrompt(true)
        }

        val result = ConnectionStateRepository.requestPrompt("delegate.host", null, "fp", false)
        assertTrue(result)
        assertNull(ConnectionStateRepository.promptRequest.value)
        assertNull(UserPromptRegistry.promptRequest.value)
    }
}
