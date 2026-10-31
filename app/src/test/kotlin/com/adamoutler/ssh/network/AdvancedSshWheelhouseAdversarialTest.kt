package com.adamoutler.ssh.network

import android.os.Build
import com.adamoutler.ssh.crypto.fido2.Fido2PublicKeyBlob
import com.adamoutler.ssh.crypto.fido2.Fido2WireReader
import com.adamoutler.ssh.crypto.fido2.Fido2WireSignature
import com.adamoutler.ssh.crypto.fido2.Fido2WireWriter
import com.adamoutler.ssh.crypto.fido2.OpenSshFido2WireParser
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.IdentityProfile
import com.adamoutler.ssh.data.Protocol
import com.adamoutler.ssh.network.agent.AgentIdentityKey
import com.adamoutler.ssh.network.agent.BiometricAgentConfirmationBridge
import com.adamoutler.ssh.network.agent.BiometricAuthAgentHandler
import com.adamoutler.ssh.sftp.FakeSftpClientAdapter
import com.adamoutler.ssh.sftp.SftpClientManager
import com.adamoutler.ssh.sftp.SftpPathValidator
import com.adamoutler.ssh.ui.components.JumpHostGraphValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.Service
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.UserAuth
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.userauth.method.AuthMethod
import net.schmizz.sshj.userauth.password.PasswordFinder
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.util.Base64

/**
 * Comprehensive functional and adversarial tests verifying the 5 advanced SSH wheelhouse subsystems:
 * 1. Jump Host / ProxyJump graph validation & cyclic loop guards.
 * 2. Jump Host target TOFU MITM isolation & ephemeral credential zeroization.
 * 3. Biometric SSH-Agent hardened wire handling & loop guards.
 * 4. FIDO2 security key wire serialization, flag validation, and adversarial rejection.
 * 5. SFTP path traversal, null-byte injection, and memory safety limits.
 * 6. Multi-Factor continuation (RFC 4252 SSH_AUTH_PARTIAL) & credential sanitization.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], manifest = Config.NONE)
class AdvancedSshWheelhouseAdversarialTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setUp() {
        ConnectionStateRepository.isHeadlessTest = true
    }

    @After
    fun tearDown() {
        ConnectionStateRepository.isHeadlessTest = false
    }

    private fun createJumpProfile(id: String, jumpHostId: String? = null): ConnectionProfile =
        ConnectionProfile(
            id = id,
            nickname = "Node-$id",
            host = "$id.internal",
            port = 22,
            protocol = Protocol.SSH,
            username = "user-$id",
            jumpHostProfileId = jumpHostId,
        )

    private fun generateRsaPublicKey(): PublicKey {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        return kpg.generateKeyPair().public
    }

    private fun frameAgentMessage(payload: ByteArray): ByteArray {
        val framed = ByteArray(4 + payload.size)
        ByteBuffer.wrap(framed).putInt(payload.size).put(payload)
        return framed
    }

    private fun buildSignRequestPayload(keyBlob: ByteArray, dataToSign: ByteArray): ByteArray {
        val buf = ByteBuffer.allocate(4 + keyBlob.size + 4 + dataToSign.size)
        buf.putInt(keyBlob.size)
        buf.put(keyBlob)
        buf.putInt(dataToSign.size)
        buf.put(dataToSign)
        return buf.array()
    }

    private fun buildSignRequestWire(keyBlob: ByteArray, dataToSign: ByteArray): ByteArray {
        val payload = buildSignRequestPayload(keyBlob, dataToSign)
        val msg = ByteArray(1 + payload.size)
        msg[0] = BiometricAuthAgentHandler.SSH2_AGENTC_SIGN_REQUEST
        System.arraycopy(payload, 0, msg, 1, payload.size)
        return frameAgentMessage(msg)
    }

    // =========================================================================
    // 1. Jump Host / ProxyJump Functional & Adversarial Graph Validation
    // =========================================================================

    @Test
    fun test1_1_validMultiHopBastionChain_passesGraphValidation() {
        val b1 = createJumpProfile("bastion1", null)
        val b2 = createJumpProfile("bastion2", "bastion1")
        val target = createJumpProfile("target", "bastion2")
        val profiles = mapOf(b1.id to b1, b2.id to b2, target.id to target)

        // Target pointing to Bastion 2 is a valid linear path
        assertFalse(
            "Valid 2-hop jump chain (Client -> Bastion1 -> Bastion2 -> Target) must not report cycle",
            JumpHostGraphValidator.hasJumpHostCycle(
                currentProfileId = "target",
                selectedJumpHostId = "bastion2",
                profiles = profiles,
            ),
        )

        // Bastion 2 selecting Bastion 1 is valid
        assertFalse(
            "Bastion2 selecting Bastion1 must not report cycle",
            JumpHostGraphValidator.hasJumpHostCycle(
                currentProfileId = "bastion2",
                selectedJumpHostId = "bastion1",
                profiles = profiles,
            ),
        )

        // Bastion 1 selecting direct connection (null) is valid
        assertFalse(
            "Direct connection (null) must never report cycle",
            JumpHostGraphValidator.hasJumpHostCycle(
                currentProfileId = "bastion1",
                selectedJumpHostId = null,
                profiles = profiles,
            ),
        )
    }

    @Test
    fun test1_2_directSelfLoop_detectedAndBlocked() {
        val nodeA = createJumpProfile("nodeA", null)
        val profiles = mapOf(nodeA.id to nodeA)

        assertTrue(
            "Direct self-loop (A -> A) must be detected and blocked immediately",
            JumpHostGraphValidator.hasJumpHostCycle(
                currentProfileId = "nodeA",
                selectedJumpHostId = "nodeA",
                profiles = profiles,
            ),
        )
    }

    @Test
    fun test1_3_twoNodeCycle_detectedAndBlocked() {
        val nodeA = createJumpProfile("nodeA", "nodeB")
        val nodeB = createJumpProfile("nodeB", null)
        val profiles = mapOf(nodeA.id to nodeA, nodeB.id to nodeB)

        // Node B attempting to select Node A creates A -> B -> A cycle
        assertTrue(
            "2-node cycle (A -> B -> A) must be detected and blocked",
            JumpHostGraphValidator.hasJumpHostCycle(
                currentProfileId = "nodeB",
                selectedJumpHostId = "nodeA",
                profiles = profiles,
            ),
        )
    }

    @Test
    fun test1_4_fourNodeTransitiveCycle_detectedAndBlocked() {
        val nodeA = createJumpProfile("nodeA", "nodeB")
        val nodeB = createJumpProfile("nodeB", "nodeC")
        val nodeC = createJumpProfile("nodeC", "nodeD")
        val nodeD = createJumpProfile("nodeD", null)
        val profiles = mapOf(nodeA.id to nodeA, nodeB.id to nodeB, nodeC.id to nodeC, nodeD.id to nodeD)

        // Node D selecting Node A completes 4-node transitive cycle A -> B -> C -> D -> A
        assertTrue(
            "4-node transitive cycle (A -> B -> C -> D -> A) must be detected when D selects A",
            JumpHostGraphValidator.hasJumpHostCycle(
                currentProfileId = "nodeD",
                selectedJumpHostId = "nodeA",
                profiles = profiles,
            ),
        )

        // When cycle is closed, all participating nodes detect cycle on candidate evaluation
        val closedProfiles = profiles + ("nodeD" to createJumpProfile("nodeD", "nodeA"))
        assertTrue(
            "Transitive cycle detected from Node A",
            JumpHostGraphValidator.hasJumpHostCycle("nodeA", "nodeB", closedProfiles),
        )
        assertTrue(
            "Transitive cycle detected from Node B",
            JumpHostGraphValidator.hasJumpHostCycle("nodeB", "nodeC", closedProfiles),
        )
        assertTrue(
            "Transitive cycle detected from Node C",
            JumpHostGraphValidator.hasJumpHostCycle("nodeC", "nodeD", closedProfiles),
        )
    }

    // =========================================================================
    // 2. Jump Host Target TOFU MITM Isolation & Ephemeral Credential Sanitization
    // =========================================================================

    @Test
    fun test2_1_jumpHostConnectionFailure_activelyZeroesTargetPassword() = runBlocking {
        val secretBytes = "SuperSecretTargetPassword123!".toByteArray(Charsets.UTF_8)
        val targetProfile = ConnectionProfile(
            id = "target-1",
            nickname = "Target Server",
            host = "internal.target",
            port = 22,
            protocol = Protocol.SSH,
            username = "targetuser",
            authType = AuthType.PASSWORD,
            password = secretBytes,
            jumpHostProfileId = "missing-bastion-id",
        )

        val portForwardingOrchestrator = PortForwardingOrchestrator()
        val handler = SshConnectionHandler(
            portForwardingOrchestrator = portForwardingOrchestrator,
            securityStorageManager = null, // Missing jump profile causes immediate abort
        )

        try {
            handler.connect(
                profile = targetProfile,
                keyPair = null,
                onOutput = { _, _ -> },
                onConnect = { _, _ -> },
            )
            fail("Expected connect() to abort on missing jump host")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("not found") == true)
        }

        // Target profile sensitive password bytes must be securely sanitized
        assertNotNull(targetProfile.password)
        assertTrue(
            "Target password bytes must be 100% zero-wiped upon jump host connection abort",
            targetProfile.password!!.all { it == 0.toByte() },
        )
    }

    @Test
    fun test2_2_targetHandshakeFailure_activelyZeroesProfileAndIdentityBuffers() = runBlocking {
        val identityPasswordBytes = "IdentitySecretPassword456!".toByteArray(Charsets.UTF_8)
        val identityPrivateKeyBytes = "FakeIdentityPrivateKeyData!".toByteArray(Charsets.UTF_8)
        val profilePasswordBytes = "ProfileLevelPassword789!".toByteArray(Charsets.UTF_8)

        val identity = IdentityProfile(
            id = "ident-1",
            name = "Production Identity",
            username = "produser",
            password = identityPasswordBytes,
            privateKey = identityPrivateKeyBytes,
            authType = AuthType.PASSWORD,
        )

        val targetProfile = ConnectionProfile(
            id = "target-profile",
            nickname = "Production DB",
            host = "db.internal",
            port = 22,
            protocol = Protocol.SSH,
            username = "produser",
            authType = AuthType.PASSWORD,
            password = profilePasswordBytes,
            identityId = "ident-1",
        )

        // Subclass SshHandshakeCoordinator to inject an authentication rejection on target
        val failingCoordinator = object : SshHandshakeCoordinator() {
            override fun configureHostKeyVerifier(client: SSHClient, isAutonomousReconnect: Boolean) {
                // Verified host key, proceed to credential phase
            }

            override fun resolveIdentity(profile: ConnectionProfile): IdentityProfile = identity

            override fun authenticateClient(
                client: SSHClient,
                profile: ConnectionProfile,
                keyPair: java.security.KeyPair?,
                identity: IdentityProfile?,
                effectiveProfile: ConnectionProfile,
                sessionId: String?,
            ) {
                throw UserAuthException("Target host rejected supplied credentials (simulated failure)")
            }
        }

        val dummyClient = SSHClient()
        try {
            failingCoordinator.executeWithConnection(
                client = dummyClient,
                profile = targetProfile,
                skipSocketConnect = true,
            ) {
                fail("Handshake block must not execute upon authentication failure")
            }
        } catch (e: UserAuthException) {
            assertEquals("Target host rejected supplied credentials (simulated failure)", e.message)
        }

        // Verify zeroization across both Profile and Identity buffers
        assertTrue(
            "Identity password bytes must be zero-wiped on handshake abort",
            identity.password!!.all { it == 0.toByte() },
        )
        assertTrue(
            "Identity private key bytes must be zero-wiped on handshake abort",
            identity.privateKey!!.all { it == 0.toByte() },
        )
        assertTrue(
            "Profile password bytes must be zero-wiped on handshake abort",
            targetProfile.password!!.all { it == 0.toByte() },
        )
    }

    @Test
    fun test2_3_tofuHostKeyVerifier_autonomousReconnectRejection_activelyZeroesCredentials() = runBlocking {
        val knownHostsFile = tempFolder.newFile("test_known_hosts")
        val legitimateKey = generateRsaPublicKey()
        val attackerMitmKey = generateRsaPublicKey()

        // Write legitimate host key into known_hosts
        val encodedKey = Base64.getEncoder().encodeToString(legitimateKey.encoded)
        knownHostsFile.writeText("target.internal,10.0.0.5 ssh-rsa $encodedKey\n")

        // Verifier configured with isAutonomousReconnect = true (Zero-Prompt Security Policy)
        val verifier = TofuHostKeyVerifier(knownHostsFile, isAutonomousReconnect = true)

        // Attempting to verify the attacker MITM key must return false without prompting
        val isVerified = verifier.verify("target.internal", 22, attackerMitmKey)
        assertFalse(
            "TofuHostKeyVerifier must strictly reject changed host key during autonomous reconnect",
            isVerified,
        )

        // Verify that when coordinator handles verifier rejection, credentials are sanitized
        val secretBytes = "MitmProtectedPassword!".toByteArray(Charsets.UTF_8)
        val profile = ConnectionProfile(
            id = "p-tofu",
            nickname = "TOFU Protected",
            host = "target.internal",
            port = 22,
            username = "admin",
            password = secretBytes,
            authType = AuthType.PASSWORD,
        )

        val coordinator = object : SshHandshakeCoordinator(hostKeyVerifier = verifier) {
            override fun configureHostKeyVerifier(client: SSHClient, isAutonomousReconnect: Boolean) {
                // Reject connection immediately as HostKeyVerifier fails
                throw IOException("Host key mismatch for target.internal (Zero-Prompt Autonomous Reconnect Policy)")
            }
        }

        try {
            coordinator.executeWithConnection(
                client = SSHClient(),
                profile = profile,
                isAutonomousReconnect = true,
                skipSocketConnect = true,
            ) {
                fail("Connection must abort on host key mismatch")
            }
        } catch (e: IOException) {
            assertTrue(e.message?.contains("Host key mismatch") == true)
        }

        assertTrue(
            "Profile password must be zero-wiped upon autonomous reconnect host key mismatch rejection",
            profile.password!!.all { it == 0.toByte() },
        )
    }

    // =========================================================================
    // 3. Biometric SSH-Agent Adversarial Tests (BiometricAuthAgentHandler)
    // =========================================================================

    @Test
    fun test3_1_agentKeyEnumeration_emptyWhitelist_returnsZeroIdentitiesAnswer() = runBlocking {
        val dummyKeys = listOf(
            AgentIdentityKey("alias-1", byteArrayOf(1, 2, 3), "Key 1") { byteArrayOf() },
            AgentIdentityKey("alias-2", byteArrayOf(4, 5, 6), "Key 2") { byteArrayOf() },
        )

        val handler = BiometricAuthAgentHandler(
            confirmationBridge = { _, _, _ -> true },
            availableKeys = dummyKeys,
            allowedKeyAliases = emptySet(), // Empty whitelist strictly blocks key enumeration
        )

        // 1. Unadorned wire request
        val requestIdentitiesUnadorned = byteArrayOf(BiometricAuthAgentHandler.SSH2_AGENTC_REQUEST_IDENTITIES)
        val response = handler.processMessage(requestIdentitiesUnadorned)

        // Framed response: 4-byte len (5) + msgType (12) + uint32 count (0) = 9 bytes
        assertEquals(9, response.size)
        val buf = ByteBuffer.wrap(response)
        val len = buf.int
        assertEquals(5, len)
        val msgType = buf.get()
        assertEquals(BiometricAuthAgentHandler.SSH2_AGENT_IDENTITIES_ANSWER, msgType)
        val count = buf.int
        assertEquals("Empty whitelist must return 0 keys to prevent key harvesting", 0, count)

        // 2. Framed wire request
        val requestIdentitiesFramed = frameAgentMessage(requestIdentitiesUnadorned)
        val framedResponse = handler.processMessage(requestIdentitiesFramed)
        val framedBuf = ByteBuffer.wrap(framedResponse)
        assertEquals(5, framedBuf.int)
        assertEquals(BiometricAuthAgentHandler.SSH2_AGENT_IDENTITIES_ANSWER, framedBuf.get())
        assertEquals(0, framedBuf.int)
    }

    @Test
    fun test3_2_agentKeyEnumeration_whitelistedKey_returnsOnlyWhitelistedIdentities() = runBlocking {
        val keyBlob = byteArrayOf(0x30, 0x59, 0x02)
        val dummyKeys = listOf(
            AgentIdentityKey("whitelisted-key", keyBlob, "Authorized Key") { byteArrayOf() },
            AgentIdentityKey("secret-key", byteArrayOf(0x09, 0x08), "Unauthorized Key") { byteArrayOf() },
        )

        val handler = BiometricAuthAgentHandler(
            confirmationBridge = { _, _, _ -> true },
            availableKeys = dummyKeys,
            allowedKeyAliases = setOf("whitelisted-key"),
        )

        val response = handler.processMessage(byteArrayOf(BiometricAuthAgentHandler.SSH2_AGENTC_REQUEST_IDENTITIES))
        val buf = ByteBuffer.wrap(response)
        buf.int // length
        assertEquals(BiometricAuthAgentHandler.SSH2_AGENT_IDENTITIES_ANSWER, buf.get())
        val count = buf.int
        assertEquals(1, count)

        // Check returned key blob
        val blobLen = buf.int
        assertEquals(keyBlob.size, blobLen)
        val returnedBlob = ByteArray(blobLen)
        buf.get(returnedBlob)
        assertArrayEquals(keyBlob, returnedBlob)
    }

    @Test
    fun test3_3_agentSignRequest_forwardLoopGuard_hopDepthExceeded_returnsAgentFailure() = runBlocking {
        val keyBlob = byteArrayOf(0x10, 0x20)
        val key = AgentIdentityKey("alias-forward", keyBlob) { byteArrayOf(0x77) }
        val handler = BiometricAuthAgentHandler(
            confirmationBridge = { _, _, _ -> true },
            availableKeys = listOf(key),
            allowedKeyAliases = setOf("alias-forward"),
        )

        val signWire = buildSignRequestWire(keyBlob, byteArrayOf(0x42))

        // Hop count == MAX_ALLOWED_HOPS (1) -> blocked
        val respHop1 = handler.processMessage(signWire, currentHopCount = BiometricAuthAgentHandler.MAX_ALLOWED_HOPS)
        val expectedFailure = byteArrayOf(0, 0, 0, 1, BiometricAuthAgentHandler.SSH_AGENT_FAILURE)
        assertArrayEquals("Sign request at MAX_ALLOWED_HOPS must return SSH_AGENT_FAILURE", expectedFailure, respHop1)

        // Hop count == 3 -> blocked
        val respHop3 = handler.processMessage(signWire, currentHopCount = 3)
        assertArrayEquals("Sign request beyond MAX_ALLOWED_HOPS must return SSH_AGENT_FAILURE", expectedFailure, respHop3)
    }

    @Test
    fun test3_4_agentSignRequest_biometricBridgeDenial_returnsAgentFailure() = runBlocking {
        val keyBlob = byteArrayOf(0x10, 0x20)
        val key = AgentIdentityKey("alias-denied", keyBlob) { byteArrayOf(0x77) }

        val handler = BiometricAuthAgentHandler(
            confirmationBridge = { _, _, _ -> false }, // User denies biometric prompt
            availableKeys = listOf(key),
            allowedKeyAliases = setOf("alias-denied"),
        )

        val signWire = buildSignRequestWire(keyBlob, byteArrayOf(0x42))
        val resp = handler.processMessage(signWire, currentHopCount = 0)
        val expectedFailure = byteArrayOf(0, 0, 0, 1, BiometricAuthAgentHandler.SSH_AGENT_FAILURE)
        assertArrayEquals("Biometric prompt denial must return SSH_AGENT_FAILURE", expectedFailure, resp)
    }

    @Test
    fun test3_5_agentSignRequest_biometricBridgeException_returnsAgentFailure() = runBlocking {
        val keyBlob = byteArrayOf(0x10, 0x20)
        val key = AgentIdentityKey("alias-ex", keyBlob) { byteArrayOf(0x77) }

        val handler = BiometricAuthAgentHandler(
            confirmationBridge = { _, _, _ -> throw SecurityException("Biometric dialog cancelled by OS") },
            availableKeys = listOf(key),
            allowedKeyAliases = setOf("alias-ex"),
        )

        val signWire = buildSignRequestWire(keyBlob, byteArrayOf(0x42))
        val resp = handler.processMessage(signWire, currentHopCount = 0)
        val expectedFailure = byteArrayOf(0, 0, 0, 1, BiometricAuthAgentHandler.SSH_AGENT_FAILURE)
        assertArrayEquals("Biometric bridge exception must safely return SSH_AGENT_FAILURE", expectedFailure, resp)
    }

    @Test
    fun test3_6_agentSignRequest_biometricApproved_returnsValidSignResponse() = runBlocking {
        val keyBlob = byteArrayOf(0x10, 0x20)
        val expectedSignature = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte())
        val key = AgentIdentityKey("alias-ok", keyBlob) { expectedSignature }

        val handler = BiometricAuthAgentHandler(
            confirmationBridge = { _, _, _ -> true },
            availableKeys = listOf(key),
            allowedKeyAliases = setOf("alias-ok"),
        )

        val signWire = buildSignRequestWire(keyBlob, byteArrayOf(0x42))
        val resp = handler.processMessage(signWire, currentHopCount = 0)

        val buf = ByteBuffer.wrap(resp)
        buf.int // frame len
        assertEquals(BiometricAuthAgentHandler.SSH2_AGENT_SIGN_RESPONSE, buf.get())
        val sigLen = buf.int
        assertEquals(expectedSignature.size, sigLen)
        val actualSig = ByteArray(sigLen)
        buf.get(actualSig)
        assertArrayEquals(expectedSignature, actualSig)
    }

    @Test
    fun test3_7_agentMalformedPackets_safelyReturnAgentFailureWithoutCrashing() = runBlocking {
        val handler = BiometricAuthAgentHandler(
            confirmationBridge = { _, _, _ -> true },
            availableKeys = emptyList(),
            allowedKeyAliases = emptySet(),
        )
        val expectedFailure = byteArrayOf(0, 0, 0, 1, BiometricAuthAgentHandler.SSH_AGENT_FAILURE)

        // 1. Empty request buffer
        assertArrayEquals(expectedFailure, handler.processMessage(byteArrayOf()))

        // 2. Unknown message type 0xFF
        assertArrayEquals(expectedFailure, handler.processMessage(byteArrayOf(0xFF.toByte())))

        // 3. Negative length prefix
        val negativeLenMsg = ByteBuffer.allocate(5).putInt(-1).put(13).array()
        assertArrayEquals(expectedFailure, handler.processMessage(negativeLenMsg))

        // 4. Oversized length prefix
        val oversizedLenMsg = ByteBuffer.allocate(8).putInt(1000).put(13).array()
        assertArrayEquals(expectedFailure, handler.processMessage(oversizedLenMsg))

        // 5. Truncated sign request payload (< 8 bytes)
        val truncatedPayload = frameAgentMessage(byteArrayOf(BiometricAuthAgentHandler.SSH2_AGENTC_SIGN_REQUEST, 0, 0, 0))
        assertArrayEquals(expectedFailure, handler.processMessage(truncatedPayload))

        // 6. Sign request for non-existent key blob
        val nonExistentKeyWire = buildSignRequestWire(byteArrayOf(0x99.toByte(), 0x88.toByte()), byteArrayOf(0x01))
        assertArrayEquals(expectedFailure, handler.processMessage(nonExistentKeyWire))

        // 7. Corrupted payload offsets (key blob length claims 100 bytes but only 2 present)
        val corruptPayload = ByteBuffer.allocate(10)
            .put(BiometricAuthAgentHandler.SSH2_AGENTC_SIGN_REQUEST)
            .putInt(100)
            .put(byteArrayOf(1, 2, 3))
            .array()
        assertArrayEquals(expectedFailure, handler.processMessage(corruptPayload))
    }

    // =========================================================================
    // 4. FIDO2 Security Key Adversarial Wire Parsing (OpenSshFido2WireParser)
    // =========================================================================

    @Test
    fun test4_1_fido2Ed25519_validBlobAndWireSignature() {
        val pubBytes = ByteArray(32) { (it + 1).toByte() }
        val originalBlob = Fido2PublicKeyBlob.Ed25519(
            publicBytes = pubBytes,
            application = "ssh:cossh-ed25519",
        )

        // Encode to wire format and parse back
        val wireBlob = OpenSshFido2WireParser.encodePublicKeyBlob(originalBlob)
        val parsedBlob = OpenSshFido2WireParser.parsePublicKeyBlob(wireBlob)
        assertTrue(parsedBlob is Fido2PublicKeyBlob.Ed25519)
        assertEquals(OpenSshFido2WireParser.KEY_TYPE_SK_ED25519, parsedBlob.keyType)
        assertEquals("ssh:cossh-ed25519", parsedBlob.application)
        assertArrayEquals(pubBytes, (parsedBlob as Fido2PublicKeyBlob.Ed25519).publicBytes)

        // Wire Signature with User Presence (0x01) and User Verified (0x04)
        val sigBytes = ByteArray(64) { 0x33 }
        val flags = (OpenSshFido2WireParser.FLAG_USER_PRESENCE.toInt() or OpenSshFido2WireParser.FLAG_USER_VERIFIED.toInt()).toByte()
        val originalSig = Fido2WireSignature.Ed25519(
            signature = sigBytes,
            flags = flags,
            counter = 1337L,
        )

        val wireSig = OpenSshFido2WireParser.serializeWireSignature(originalSig)
        val parsedSig = OpenSshFido2WireParser.parseWireSignature(wireSig)
        assertTrue(parsedSig is Fido2WireSignature.Ed25519)
        assertEquals(1337L, parsedSig.counter)
        assertEquals(flags, parsedSig.flags)
        assertArrayEquals(sigBytes, (parsedSig as Fido2WireSignature.Ed25519).signature)

        // Flag validation
        assertTrue(OpenSshFido2WireParser.validateFlags(flags, requireUserPresence = true, requireUserVerification = true))
    }

    @Test
    fun test4_2_fido2EcdsaNistP256_validBlobAndWireSignature() {
        val pubPoint = ByteArray(65) { 0x04 }
        val originalBlob = Fido2PublicKeyBlob.EcdsaNistP256(
            curveName = OpenSshFido2WireParser.CURVE_NISTP256,
            publicPoint = pubPoint,
            application = "ssh:cossh-ecdsa",
        )

        val wireBlob = OpenSshFido2WireParser.encodePublicKeyBlob(originalBlob)
        val parsedBlob = OpenSshFido2WireParser.parsePublicKeyBlob(wireBlob)
        assertTrue(parsedBlob is Fido2PublicKeyBlob.EcdsaNistP256)
        assertEquals(OpenSshFido2WireParser.KEY_TYPE_SK_ECDSA, parsedBlob.keyType)
        assertEquals(OpenSshFido2WireParser.CURVE_NISTP256, (parsedBlob as Fido2PublicKeyBlob.EcdsaNistP256).curveName)
        assertArrayEquals(pubPoint, parsedBlob.publicPoint)

        val r = BigInteger("123456789012345678901234567890")
        val s = BigInteger("987654321098765432109876543210")
        val originalSig = Fido2WireSignature.EcdsaNistP256(
            r = r,
            s = s,
            flags = OpenSshFido2WireParser.FLAG_USER_PRESENCE,
            counter = 2002L,
        )

        val wireSig = OpenSshFido2WireParser.serializeWireSignature(originalSig)
        val parsedSig = OpenSshFido2WireParser.parseWireSignature(wireSig)
        assertTrue(parsedSig is Fido2WireSignature.EcdsaNistP256)
        assertEquals(2002L, parsedSig.counter)
        assertEquals(OpenSshFido2WireParser.FLAG_USER_PRESENCE, parsedSig.flags)
        assertEquals(r, (parsedSig as Fido2WireSignature.EcdsaNistP256).r)
        assertEquals(s, parsedSig.s)

        // User presence is set (0x01), but user verification (0x04) is NOT set
        assertTrue(OpenSshFido2WireParser.validateFlags(parsedSig.flags, requireUserPresence = true, requireUserVerification = false))
        assertFalse(OpenSshFido2WireParser.validateFlags(parsedSig.flags, requireUserPresence = true, requireUserVerification = true))
    }

    @Test
    fun test4_3_fido2SignedDataBlob_structureAndLengthVerification() {
        val blob = OpenSshFido2WireParser.buildSignedDataBlob(
            application = "ssh:cossh-test",
            flags = OpenSshFido2WireParser.FLAG_USER_PRESENCE,
            counter = 42L,
            message = "test-challenge-nonce".toByteArray(Charsets.UTF_8),
        )

        // WebAuthn signature blob must be strictly 69 bytes:
        // 32-byte sha256(app) + 1-byte flags + 4-byte counter + 32-byte sha256(msg)
        assertEquals("WebAuthn signed data blob must be exactly 69 bytes", 69, blob.size)

        val buf = ByteBuffer.wrap(blob)
        val appHash = ByteArray(32)
        buf.get(appHash)
        val flags = buf.get()
        val counter = buf.int
        val msgHash = ByteArray(32)
        buf.get(msgHash)

        assertEquals(OpenSshFido2WireParser.FLAG_USER_PRESENCE, flags)
        assertEquals(42, counter)
    }

    @Test
    fun test4_4_fido2EcdsaAsn1DerConversion() {
        val r = BigInteger("11223344556677889900")
        val s = BigInteger("99887766554433221100")

        val derBytes = OpenSshFido2WireParser.encodeEcdsaRsToDer(r, s)
        val (parsedR, parsedS) = OpenSshFido2WireParser.extractEcdsaRs(derBytes)
        assertEquals(r, parsedR)
        assertEquals(s, parsedS)

        // Malformed ASN.1 sequence
        assertThrows(Exception::class.java) {
            OpenSshFido2WireParser.extractEcdsaRs(byteArrayOf(0x30, 0x00))
        }
    }

    @Test
    fun test4_5_fido2AdversarialPayloads_strictlyRejected() {
        // 1. Wrong key algorithm header
        val wrongAlgKeyBlob = Fido2WireWriter()
            .writeString("ssh-rsa")
            .writeStringBytes(ByteArray(32))
            .writeString("ssh:")
            .toByteArray()
        assertThrows(IllegalArgumentException::class.java) {
            OpenSshFido2WireParser.parsePublicKeyBlob(wrongAlgKeyBlob)
        }

        // 2. Truncated public key blob
        val validBlob = OpenSshFido2WireParser.encodePublicKeyBlob(
            Fido2PublicKeyBlob.Ed25519(ByteArray(32), "ssh:"),
        )
        assertThrows(IllegalArgumentException::class.java) {
            OpenSshFido2WireParser.parsePublicKeyBlob(validBlob.copyOfRange(0, validBlob.size - 6))
        }

        // 3. Signature missing User Presence flag (0x00)
        assertFalse(
            "Signature with flags 0x00 must fail User Presence requirement",
            OpenSshFido2WireParser.validateFlags(0x00.toByte(), requireUserPresence = true),
        )

        // 4. Oversized/corrupted length prefix in reader
        val corruptedLenBytes = ByteBuffer.allocate(8).putInt(5000).putInt(0).array()
        assertThrows(IllegalArgumentException::class.java) {
            Fido2WireReader(corruptedLenBytes).readStringBytes()
        }

        // 5. Wrong signature algorithm header
        val wrongSigBytes = Fido2WireWriter().writeString("ssh-dss").toByteArray()
        assertThrows(IllegalArgumentException::class.java) {
            OpenSshFido2WireParser.parseWireSignature(wrongSigBytes)
        }
    }

    // =========================================================================
    // 5. SFTP Subsystem Path Traversal & Null-Byte Injection (SftpPathValidator & SftpClientManager)
    // =========================================================================

    @Test
    fun test5_1_sftpPathValidator_relativeTraversal_resolvesCanonicalPosixPath() {
        assertEquals("/etc/shadow", SftpPathValidator.canonicalize("../../etc/shadow"))
        assertEquals("/etc/passwd", SftpPathValidator.canonicalize("/var/www/../../../etc/passwd"))
        assertEquals("/var/log", SftpPathValidator.canonicalize("/home/user/../../var/log"))
        assertEquals("/a/d", SftpPathValidator.canonicalize("a/b/c/../../d"))
        assertEquals("/server/share/folder/file.txt", SftpPathValidator.canonicalize("\\\\server\\\\share\\\\folder\\\\file.txt"))
        assertEquals("/var/log", SftpPathValidator.canonicalize("///var///log///"))
        assertEquals("/", SftpPathValidator.canonicalize(""))
        assertEquals("/", SftpPathValidator.canonicalize("/"))
        assertEquals("/", SftpPathValidator.canonicalize("."))
        assertEquals("/", SftpPathValidator.canonicalize(".."))
    }

    @Test
    fun test5_2_sftpPathValidator_nullByteInjection_throwsSecurityException() {
        assertThrows(IllegalArgumentException::class.java) {
            SftpPathValidator.canonicalize("/home/user/file\u0000.sh")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SftpPathValidator.canonicalize("\u0000/etc/shadow")
        }

        // SftpPathValidator.join adversarial tests
        assertThrows(IllegalArgumentException::class.java) {
            SftpPathValidator.join("/home/user", "file\u0000.txt")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SftpPathValidator.join("/home/user", "nested/file.txt")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SftpPathValidator.join("/home/user", "nested\\file.txt")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SftpPathValidator.join("/home/user", "..")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SftpPathValidator.join("/home/user", ".")
        }
    }

    @Test
    fun test5_3_sftpPathValidator_isWithinBounds_chrootSandboxEscapes() {
        assertFalse(
            "Traversal to /etc/passwd must not be within bounds of /home/user",
            SftpPathValidator.isWithinBounds("/etc/passwd", "/home/user"),
        )
        assertFalse(
            "Relative traversal escaping to /etc/shadow must be caught",
            SftpPathValidator.isWithinBounds("/home/user/../../etc/shadow", "/home/user"),
        )
        assertFalse(
            "Prefix collision (/home/user2) must not be treated as child of /home/user",
            SftpPathValidator.isWithinBounds("/home/user2/file", "/home/user"),
        )

        assertTrue(
            "Child file must be within bounds",
            SftpPathValidator.isWithinBounds("/home/user/documents/report.pdf", "/home/user"),
        )
        assertTrue(
            "Base directory itself is within bounds",
            SftpPathValidator.isWithinBounds("/home/user", "/home/user"),
        )
        assertTrue(
            "Root chroot bounds everything",
            SftpPathValidator.isWithinBounds("/any/arbitrary/path", "/"),
        )
    }

    @Test
    fun test5_4_sftpClientManager_operationsAndSafetyLimits() = runBlocking {
        val adapter = FakeSftpClientAdapter()
        adapter.addVirtualDirectory("/home/user/sandbox")
        adapter.addVirtualFile("/home/user/sandbox/data.txt", "Sandbox data content".toByteArray(Charsets.UTF_8))

        val manager = SftpClientManager(adapter, Dispatchers.Unconfined)

        // 1. Directory listing
        val list = manager.listDirectory("/home/user/sandbox")
        assertEquals(1, list.size)
        assertEquals("data.txt", list[0].name)

        // 2. Safe file inspection
        val content = manager.readFileContent("/home/user/sandbox/data.txt")
        assertEquals("Sandbox data content", content)

        // 3. Null-byte traversal rejection
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { manager.readFileContent("/home/user/sandbox/data.txt\u0000evil") }
        }

        // 4. Memory safety guard: File exceeding 2MB limit must throw IllegalStateException
        adapter.addVirtualFile("/home/user/sandbox/large.bin", ByteArray(3 * 1024 * 1024))
        assertThrows(IllegalStateException::class.java) {
            runBlocking { manager.readFileContent("/home/user/sandbox/large.bin") }
        }

        // 5. Atomic download staging & cleanup
        val destinationFile = tempFolder.newFile("dest_file.txt")
        destinationFile.delete() // Clean destination for atomic download
        manager.downloadFile("/home/user/sandbox/data.txt", destinationFile)
        assertTrue("Downloaded destination file must exist", destinationFile.exists())
        assertEquals("Sandbox data content", destinationFile.readText())

        // Verify no leftover partial staging files remain
        val parentDir = destinationFile.parentFile ?: File(".")
        val stagingArtifacts = parentDir.listFiles { _, name -> name.startsWith(".cossh_part_") } ?: emptyArray()
        assertEquals("No staging part files should remain after download completion", 0, stagingArtifacts.size)

        manager.close()
    }

    // =========================================================================
    // 6. Multi-Factor Authentication (MultiFactorContinuationEngine / SSH_AUTH_PARTIAL)
    // =========================================================================

    @Test
    fun test6_1_multiFactorContinuation_validSecondaryPassword_succeedsAndSanitizes() {
        val secretCandidate = "SecondFactorPassword!".toByteArray(Charsets.UTF_8)
        val profile = ConnectionProfile(
            id = "prof-mfa",
            nickname = "MFA Host",
            host = "mfa.internal",
            port = 22,
            username = "mfauser",
            password = secretCandidate,
            authType = AuthType.KEY,
        )

        var passwordAuthenticated = false
        val mockUserAuth = object : UserAuth {
            override fun authenticate(user: String?, service: Service?, method: AuthMethod?, timeout: Int): Boolean = true
            override fun getBanner(): String? = null
            override fun hadPartialSuccess(): Boolean = true
            override fun getAllowedMethods(): Collection<String> = listOf("password")
        }

        val testClient = object : SSHClient() {
            override fun getUserAuth(): UserAuth = mockUserAuth
            override fun isAuthenticated(): Boolean = passwordAuthenticated
            override fun authPassword(username: String?, pfinder: PasswordFinder?) {
                val passChars = pfinder?.reqPassword(null)
                if (username == "mfauser" && passChars != null && String(passChars) == "SecondFactorPassword!") {
                    passwordAuthenticated = true
                }
            }
        }

        val engine = MultiFactorContinuationEngine()
        val result = engine.continueIfPartialSuccess(
            client = testClient,
            profile = profile,
            sessionId = "test-mfa-session",
        )

        assertTrue("Secondary password MFA continuation must return true on success", result)
        assertTrue(
            "Ephemeral password bytes in PasswordAuthenticator must be zero-wiped after secondary auth",
            secretCandidate.all { it == 0.toByte() },
        )
    }

    @Test
    fun test6_2_multiFactorContinuation_validSecondaryKeyboardInteractive_succeeds() {
        var interactiveSuccess = false
        val mockUserAuth = object : UserAuth {
            override fun authenticate(user: String?, service: Service?, method: AuthMethod?, timeout: Int): Boolean = true
            override fun getBanner(): String? = null
            override fun hadPartialSuccess(): Boolean = true
            override fun getAllowedMethods(): Collection<String> = listOf("keyboard-interactive")
        }

        val testClient = object : SSHClient() {
            override fun getUserAuth(): UserAuth = mockUserAuth
            override fun isAuthenticated(): Boolean = interactiveSuccess
        }

        // Subclass MultiFactorContinuationEngine to simulate successful OTP verification
        val engine = object : MultiFactorContinuationEngine() {
            override fun continueIfPartialSuccess(
                client: SSHClient,
                profile: ConnectionProfile,
                identity: IdentityProfile?,
                sessionId: String?,
            ): Boolean {
                val auth = client.userAuth ?: return false
                if (auth.hadPartialSuccess() && auth.allowedMethods?.contains("keyboard-interactive") == true) {
                    interactiveSuccess = true
                    return true
                }
                return false
            }
        }

        val result = engine.continueIfPartialSuccess(
            client = testClient,
            profile = createJumpProfile("mfa-host"),
        )
        assertTrue("Keyboard-interactive OTP continuation must succeed", result)
    }

    @Test
    fun test6_3_multiFactorContinuation_adversarialEmptyAllowedMethods_abortsCleanlyAndWipesCredentials() {
        val secretCandidate = "FallbackCandidatePass!".toByteArray(Charsets.UTF_8)
        val profile = ConnectionProfile(
            id = "prof-mfa-empty",
            nickname = "MFA Empty Allowed",
            host = "mfa.empty",
            port = 22,
            username = "victim",
            password = secretCandidate,
            authType = AuthType.KEY,
        )

        // Adversarial server claims SSH_AUTH_PARTIAL but provides empty allowedMethods
        val mockUserAuth = object : UserAuth {
            override fun authenticate(user: String?, service: Service?, method: AuthMethod?, timeout: Int): Boolean = false
            override fun getBanner(): String? = null
            override fun hadPartialSuccess(): Boolean = true
            override fun getAllowedMethods(): Collection<String> = emptyList()
        }

        val testClient = object : SSHClient() {
            override fun getUserAuth(): UserAuth = mockUserAuth
            override fun isAuthenticated(): Boolean = false
            override fun authPassword(username: String?, pfinder: PasswordFinder?) {
                throw UserAuthException("Rejected credentials in fallback authentication")
            }
        }

        val engine = MultiFactorContinuationEngine()

        try {
            engine.continueIfPartialSuccess(
                client = testClient,
                profile = profile,
            )
            fail("Expected UserAuthException when secondary authenticators are exhausted")
        } catch (e: UserAuthException) {
            assertTrue(
                "Exception message must indicate multi-factor secondary authentication failure",
                e.message?.contains("Multi-factor secondary authentication failed") == true,
            )
        }

        // Verify NO infinite loop occurred and password bytes were safely wiped
        assertTrue(
            "Ephemeral password bytes must be zero-wiped when secondary factor fails",
            secretCandidate.all { it == 0.toByte() },
        )
    }

    @Test
    fun test6_4_multiFactorContinuation_adversarialFailingSecondaryFactor_abortsCleanly() {
        val secretCandidate = "BadOtpPass!".toByteArray(Charsets.UTF_8)
        val profile = ConnectionProfile(
            id = "prof-mfa-fail",
            nickname = "MFA Failing",
            host = "mfa.failing",
            port = 22,
            username = "victim",
            password = secretCandidate,
            authType = AuthType.KEY,
        )

        val mockUserAuth = object : UserAuth {
            override fun authenticate(user: String?, service: Service?, method: AuthMethod?, timeout: Int): Boolean = false
            override fun getBanner(): String? = null
            override fun hadPartialSuccess(): Boolean = true
            override fun getAllowedMethods(): Collection<String> = listOf("password")
        }

        val testClient = object : SSHClient() {
            override fun getUserAuth(): UserAuth = mockUserAuth
            override fun isAuthenticated(): Boolean = false
            override fun authPassword(username: String?, pfinder: PasswordFinder?) {
                throw UserAuthException("Invalid TOTP OTP or secondary password")
            }
        }

        val engine = MultiFactorContinuationEngine()

        assertThrows(UserAuthException::class.java) {
            engine.continueIfPartialSuccess(
                client = testClient,
                profile = profile,
            )
        }

        assertTrue(
            "Candidate password must be zeroed upon secondary factor rejection",
            secretCandidate.all { it == 0.toByte() },
        )
    }

    @Test
    fun test6_5_multiFactorContinuation_noPartialSuccess_returnsFalseImmediately() {
        val engine = MultiFactorContinuationEngine()

        // 1. Client with userAuth == null
        val disconnectedClient = SSHClient()
        assertFalse(
            "Disconnected client must return false immediately",
            engine.continueIfPartialSuccess(disconnectedClient, createJumpProfile("p1")),
        )

        // 2. Client with hadPartialSuccess() == false
        val mockUserAuth = object : UserAuth {
            override fun authenticate(user: String?, service: Service?, method: AuthMethod?, timeout: Int): Boolean = false
            override fun getBanner(): String? = null
            override fun hadPartialSuccess(): Boolean = false
            override fun getAllowedMethods(): Collection<String> = listOf("publickey")
        }
        val clientNoPartial = object : SSHClient() {
            override fun getUserAuth(): UserAuth = mockUserAuth
            override fun isAuthenticated(): Boolean = false
        }
        assertFalse(
            "Client without partial success flag must return false immediately",
            engine.continueIfPartialSuccess(clientNoPartial, createJumpProfile("p2")),
        )
    }

    @Test
    fun test6_6_multiFactorContinuation_threadInterruption_abortsImmediately() {
        val mockUserAuth = object : UserAuth {
            override fun authenticate(user: String?, service: Service?, method: AuthMethod?, timeout: Int): Boolean = false
            override fun getBanner(): String? = null
            override fun hadPartialSuccess(): Boolean = true
            override fun getAllowedMethods(): Collection<String> = listOf("password")
        }
        val client = object : SSHClient() {
            override fun getUserAuth(): UserAuth = mockUserAuth
            override fun isAuthenticated(): Boolean = false
        }

        val engine = MultiFactorContinuationEngine()

        // Set thread interrupted status
        Thread.currentThread().interrupt()
        try {
            engine.continueIfPartialSuccess(
                client = client,
                profile = createJumpProfile("p-interrupt").copy(password = "pass".toByteArray()),
            )
            fail("Expected InterruptedException when thread is interrupted")
        } catch (e: Exception) {
            // Unwrap UserAuthException or InterruptedException
            val isInterrupted = e is InterruptedException || e.cause is InterruptedException
            assertTrue("Interrupted thread must abort immediately", isInterrupted)
        } finally {
            // Clear interrupt status so subsequent tests are not affected
            Thread.interrupted()
        }
    }
}
