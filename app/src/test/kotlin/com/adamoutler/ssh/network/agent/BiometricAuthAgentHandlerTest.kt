package com.adamoutler.ssh.network.agent

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class BiometricAuthAgentHandlerTest {

    private val keyBlob1 = byteArrayOf(1, 2, 3, 4)
    private val keyBlob2 = byteArrayOf(5, 6, 7, 8)
    private val dummySignature = byteArrayOf(9, 9, 9, 9)

    private val testKeys = listOf(
        AgentIdentityKey(
            alias = "key-prod",
            publicKeyBlob = keyBlob1,
            comment = "prod-key",
            signer = { _ -> dummySignature },
        ),
        AgentIdentityKey(
            alias = "key-dev",
            publicKeyBlob = keyBlob2,
            comment = "dev-key",
            signer = { _ -> dummySignature },
        ),
    )

    @Test
    fun requestIdentities_returnsOnlyWhitelistedKeys() = runBlocking {
        val handler = BiometricAuthAgentHandler(
            confirmationBridge = { _, _, _ -> true },
            availableKeys = testKeys,
            allowedKeyAliases = setOf("key-prod"), // Only key-prod whitelisted
        )

        val request = byteArrayOf(BiometricAuthAgentHandler.SSH2_AGENTC_REQUEST_IDENTITIES)
        val response = handler.processMessage(request)

        assertTrue(response.size >= 5)
        val msgType = response[4]
        assertEquals(BiometricAuthAgentHandler.SSH2_AGENT_IDENTITIES_ANSWER, msgType)

        // Count of keys should be 1
        val keyCount = ByteBuffer.wrap(response, 5, 4).int
        assertEquals(1, keyCount)

        // Read first key blob length and bytes
        val blobLen = ByteBuffer.wrap(response, 9, 4).int
        val blob = response.copyOfRange(13, 13 + blobLen)
        assertArrayEquals(keyBlob1, blob)
    }

    @Test
    fun requestIdentities_returnsZeroKeysWhenWhitelistIsEmpty() = runBlocking {
        val handler = BiometricAuthAgentHandler(
            confirmationBridge = { _, _, _ -> true },
            availableKeys = testKeys,
            allowedKeyAliases = emptySet(), // Empty whitelist
        )

        val request = byteArrayOf(BiometricAuthAgentHandler.SSH2_AGENTC_REQUEST_IDENTITIES)
        val response = handler.processMessage(request)

        assertEquals(BiometricAuthAgentHandler.SSH2_AGENT_IDENTITIES_ANSWER, response[4])
        val keyCount = ByteBuffer.wrap(response, 5, 4).int
        assertEquals(0, keyCount)
    }

    @Test
    fun signRequest_succeedsWhenWhitelistedAndBiometricConfirmed() = runBlocking {
        var confirmedKeyAlias: String? = null
        val handler = BiometricAuthAgentHandler(
            confirmationBridge = { alias, _, _ ->
                confirmedKeyAlias = alias
                true
            },
            availableKeys = testKeys,
            allowedKeyAliases = setOf("key-prod"),
            destinationHost = "bastion.internal",
        )

        val challenge = "ssh-auth-challenge".toByteArray(Charsets.UTF_8)
        val signRequestPayload = createSignRequestPayload(keyBlob1, challenge)

        val response = handler.processMessage(signRequestPayload)

        assertEquals(BiometricAuthAgentHandler.SSH2_AGENT_SIGN_RESPONSE, response[4])
        assertEquals("key-prod", confirmedKeyAlias)

        // Verify signature response blob
        val sigLen = ByteBuffer.wrap(response, 5, 4).int
        val sig = response.copyOfRange(9, 9 + sigLen)
        assertArrayEquals(dummySignature, sig)
    }

    @Test
    fun signRequest_failsWhenKeyNotWhitelisted() = runBlocking {
        val handler = BiometricAuthAgentHandler(
            confirmationBridge = { _, _, _ -> true },
            availableKeys = testKeys,
            allowedKeyAliases = setOf("key-prod"), // key-dev is NOT in whitelist
        )

        val challenge = "challenge".toByteArray(Charsets.UTF_8)
        val signRequestPayload = createSignRequestPayload(keyBlob2, challenge) // requesting keyBlob2 (key-dev)

        val response = handler.processMessage(signRequestPayload)

        assertEquals(BiometricAuthAgentHandler.SSH_AGENT_FAILURE, response[4])
    }

    @Test
    fun signRequest_failsWhenBiometricConfirmationDeclined() = runBlocking {
        val handler = BiometricAuthAgentHandler(
            confirmationBridge = { _, _, _ -> false }, // User cancelled or failed biometric
            availableKeys = testKeys,
            allowedKeyAliases = setOf("key-prod"),
        )

        val challenge = "challenge".toByteArray(Charsets.UTF_8)
        val signRequestPayload = createSignRequestPayload(keyBlob1, challenge)

        val response = handler.processMessage(signRequestPayload)

        assertEquals(BiometricAuthAgentHandler.SSH_AGENT_FAILURE, response[4])
    }

    @Test
    fun processMessage_blocksForwardLoopBeyondMaxAllowedHops() = runBlocking {
        val handler = BiometricAuthAgentHandler(
            confirmationBridge = { _, _, _ -> true },
            availableKeys = testKeys,
            allowedKeyAliases = setOf("key-prod"),
        )

        val request = byteArrayOf(BiometricAuthAgentHandler.SSH2_AGENTC_REQUEST_IDENTITIES)

        // Hop 0 allowed
        val resp0 = handler.processMessage(request, currentHopCount = 0)
        assertEquals(BiometricAuthAgentHandler.SSH2_AGENT_IDENTITIES_ANSWER, resp0[4])

        // Hop 1 blocked (MAX_ALLOWED_HOPS = 1)
        val resp1 = handler.processMessage(request, currentHopCount = 1)
        assertEquals(BiometricAuthAgentHandler.SSH_AGENT_FAILURE, resp1[4])

        // Hop 2 blocked
        val resp2 = handler.processMessage(request, currentHopCount = 2)
        assertEquals(BiometricAuthAgentHandler.SSH_AGENT_FAILURE, resp2[4])
    }

    private fun createSignRequestPayload(keyBlob: ByteArray, challenge: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(BiometricAuthAgentHandler.SSH2_AGENTC_SIGN_REQUEST.toInt())

        // string key blob
        out.write(ByteBuffer.allocate(4).putInt(keyBlob.size).array())
        out.write(keyBlob)

        // string data to sign
        out.write(ByteBuffer.allocate(4).putInt(challenge.size).array())
        out.write(challenge)

        // uint32 flags (0)
        out.write(ByteBuffer.allocate(4).putInt(0).array())

        val payload = out.toByteArray()
        val framed = ByteArray(4 + payload.size)
        ByteBuffer.wrap(framed).putInt(payload.size).put(payload)
        return framed
    }
}
