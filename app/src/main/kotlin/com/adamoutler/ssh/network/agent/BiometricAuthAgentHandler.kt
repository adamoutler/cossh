package com.adamoutler.ssh.network.agent

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Biometric confirmation bridge for hardware-backed SSH-Agent sign requests.
 * Evaluated per-use before any private key signing operation is authorized.
 */
fun interface BiometricAgentConfirmationBridge {
    suspend fun confirmSignRequest(
        keyAlias: String,
        destinationHost: String?,
        challenge: ByteArray,
    ): Boolean
}

/**
 * Hardware-backed or FIDO2 identity key managed by the agent.
 */
data class AgentIdentityKey(
    val alias: String,
    val publicKeyBlob: ByteArray,
    val comment: String = "",
    val signer: suspend (challenge: ByteArray) -> ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AgentIdentityKey) return false
        return alias == other.alias && publicKeyBlob.contentEquals(other.publicKeyBlob)
    }

    override fun hashCode(): Int = 31 * alias.hashCode() + publicKeyBlob.contentHashCode()
}

/**
 * Hardened OpenSSH Agent handler enforcing:
 * 1. Strict per-profile identity whitelisting (returns 0 keys if whitelist is empty).
 * 2. Per-use Biometric authorization via [BiometricAgentConfirmationBridge].
 * 3. Forward loop guard (`MAX_ALLOWED_HOPS = 1`) to eliminate unlimited agent forwarding recursion attacks.
 */
class BiometricAuthAgentHandler(
    private val confirmationBridge: BiometricAgentConfirmationBridge,
    private val availableKeys: List<AgentIdentityKey>,
    private val allowedKeyAliases: Set<String>,
    private val destinationHost: String? = null,
) {
    companion object {
        const val SSH_AGENT_FAILURE: Byte = 5
        const val SSH_AGENT_SUCCESS: Byte = 6
        const val SSH2_AGENTC_REQUEST_IDENTITIES: Byte = 11
        const val SSH2_AGENT_IDENTITIES_ANSWER: Byte = 12
        const val SSH2_AGENTC_SIGN_REQUEST: Byte = 13
        const val SSH2_AGENT_SIGN_RESPONSE: Byte = 14

        const val MAX_ALLOWED_HOPS = 1
    }

    /**
     * Processes an incoming raw wire agent message.
     * Handles framed messages (`[4-byte len][msgType][payload]`) and unadorned payload messages.
     * Returns standard framed agent wire message (`[4-byte len][msgType][payload]`).
     */
    suspend fun processMessage(requestBytes: ByteArray, currentHopCount: Int = 0): ByteArray {
        // Enforce Loop Guard: Forwarding beyond MAX_ALLOWED_HOPS is blocked immediately
        if (currentHopCount >= MAX_ALLOWED_HOPS) {
            return buildFailure()
        }

        if (requestBytes.isEmpty()) {
            return buildFailure()
        }

        // Determine if request includes 4-byte framing length
        val (msgType, payload) = if (requestBytes.size >= 5) {
            val len = ByteBuffer.wrap(requestBytes, 0, 4).int
            if (len == requestBytes.size - 4) {
                Pair(requestBytes[4], requestBytes.copyOfRange(5, requestBytes.size))
            } else {
                Pair(requestBytes[0], requestBytes.copyOfRange(1, requestBytes.size))
            }
        } else {
            Pair(requestBytes[0], requestBytes.copyOfRange(1, requestBytes.size))
        }

        return when (msgType) {
            SSH2_AGENTC_REQUEST_IDENTITIES -> handleRequestIdentities()
            SSH2_AGENTC_SIGN_REQUEST -> handleSignRequest(payload)
            else -> buildFailure()
        }
    }

    private fun handleRequestIdentities(): ByteArray {
        // Enforce Per-Profile Identity Whitelist:
        // If allowedKeyAliases is empty, strictly zero keys are exposed.
        val authorizedKeys = if (allowedKeyAliases.isEmpty()) {
            emptyList()
        } else {
            availableKeys.filter { it.alias in allowedKeyAliases }
        }

        val out = ByteArrayOutputStream()
        // Message Type
        out.write(SSH2_AGENT_IDENTITIES_ANSWER.toInt())
        // uint32 number of keys
        out.write(ByteBuffer.allocate(4).putInt(authorizedKeys.size).array())

        for (key in authorizedKeys) {
            // string: public key blob
            out.write(ByteBuffer.allocate(4).putInt(key.publicKeyBlob.size).array())
            out.write(key.publicKeyBlob)
            // string: comment
            val commentBytes = key.comment.toByteArray(Charsets.UTF_8)
            out.write(ByteBuffer.allocate(4).putInt(commentBytes.size).array())
            out.write(commentBytes)
        }

        return frameMessage(out.toByteArray())
    }

    private suspend fun handleSignRequest(payload: ByteArray): ByteArray {
        if (payload.size < 8) return buildFailure()

        var offset = 0
        // Read public key blob
        if (offset + 4 > payload.size) return buildFailure()
        val keyBlobLen = ByteBuffer.wrap(payload, offset, 4).int
        offset += 4
        if (keyBlobLen < 0 || offset + keyBlobLen > payload.size) return buildFailure()
        val requestedKeyBlob = payload.copyOfRange(offset, offset + keyBlobLen)
        offset += keyBlobLen

        // Read data to sign
        if (offset + 4 > payload.size) return buildFailure()
        val dataLen = ByteBuffer.wrap(payload, offset, 4).int
        offset += 4
        if (dataLen < 0 || offset + dataLen > payload.size) return buildFailure()
        val dataToSign = payload.copyOfRange(offset, offset + dataLen)
        offset += dataLen

        // 1. Whitelist Verification
        if (allowedKeyAliases.isEmpty()) {
            return buildFailure()
        }

        val matchedKey = availableKeys.firstOrNull { key ->
            key.alias in allowedKeyAliases && key.publicKeyBlob.contentEquals(requestedKeyBlob)
        } ?: return buildFailure()

        // 2. Per-Use Biometric Authorization Bridge
        val isConfirmed = try {
            confirmationBridge.confirmSignRequest(
                keyAlias = matchedKey.alias,
                destinationHost = destinationHost,
                challenge = dataToSign,
            )
        } catch (_: Throwable) {
            false
        }

        if (!isConfirmed) {
            return buildFailure()
        }

        // 3. Cryptographic Signature Generation
        val signatureBlob = try {
            matchedKey.signer(dataToSign)
        } catch (_: Throwable) {
            return buildFailure()
        }

        // Construct SSH2_AGENT_SIGN_RESPONSE
        val out = ByteArrayOutputStream()
        out.write(SSH2_AGENT_SIGN_RESPONSE.toInt())
        out.write(ByteBuffer.allocate(4).putInt(signatureBlob.size).array())
        out.write(signatureBlob)

        return frameMessage(out.toByteArray())
    }

    private fun buildFailure(): ByteArray {
        return frameMessage(byteArrayOf(SSH_AGENT_FAILURE))
    }

    private fun frameMessage(payload: ByteArray): ByteArray {
        val framed = ByteArray(4 + payload.size)
        ByteBuffer.wrap(framed).putInt(payload.size).put(payload)
        return framed
    }
}
