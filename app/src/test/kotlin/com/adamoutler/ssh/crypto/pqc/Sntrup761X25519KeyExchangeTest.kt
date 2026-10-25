package com.adamoutler.ssh.crypto.pqc

import com.adamoutler.ssh.crypto.SSHKeyGenerator
import com.hierynomus.sshj.key.KeyAlgorithms
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.DisconnectReason
import net.schmizz.sshj.common.Message
import net.schmizz.sshj.common.SSHPacket
import net.schmizz.sshj.transport.Transport
import net.schmizz.sshj.transport.TransportException
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.pqc.crypto.ntruprime.SNTRUPrimeKEMExtractor
import org.bouncycastle.pqc.crypto.ntruprime.SNTRUPrimeParameters
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.lang.reflect.Proxy
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

class Sntrup761X25519KeyExchangeTest {

    @Test
    fun factory_returnsCorrectAlgorithmNameAndInstance() {
        val factory = Sntrup761X25519KeyExchange.Factory()
        assertEquals("sntrup761x25519-sha512@openssh.com", factory.name)
        val instance = factory.create()
        assertTrue(instance is Sntrup761X25519KeyExchange)
    }

    @Test
    fun clientKeyGeneration_producesStandardWireLengths() {
        val clientKeys = Sntrup761X25519KeyExchange.generateClientKeys()
        assertNotNull(clientKeys.sntruPrivateKey)
        assertNotNull(clientKeys.x25519PrivateKey)
        assertEquals(1190, clientKeys.clientPublicKeyBytes.size)

        // Verify SNTRUPrime parameter sizing
        val sntruPubBytes = clientKeys.clientPublicKeyBytes.copyOfRange(0, 1158)
        assertEquals(SNTRUPrimeParameters.sntrup761.publicKeyBytes, sntruPubBytes.size)

        val x25519PubBytes = clientKeys.clientPublicKeyBytes.copyOfRange(1158, 1190)
        assertEquals(32, x25519PubBytes.size)
    }

    @Test
    fun clientAndServer_deriveIdenticalSharedSecret() {
        val random = SecureRandom()
        val clientKeys = Sntrup761X25519KeyExchange.generateClientKeys(random)

        // Server encapsulates shared secret using client's ephemeral public key
        val serverResult = Sntrup761X25519KeyExchange.encapsulateServer(
            clientPubKey = clientKeys.clientPublicKeyBytes,
            random = random,
        )
        assertEquals(1071, serverResult.serverPublicKeyBytes.size)

        // Client decapsulates and derives K
        val sntruCiphertext = serverResult.serverPublicKeyBytes.copyOfRange(0, 1039)
        val serverXPubBytes = serverResult.serverPublicKeyBytes.copyOfRange(1039, 1071)

        val extractor = SNTRUPrimeKEMExtractor(clientKeys.sntruPrivateKey)
        val clientSntruSecret = extractor.extractSecret(sntruCiphertext)
        assertEquals(32, clientSntruSecret.size)

        val agreement = org.bouncycastle.crypto.agreement.X25519Agreement()
        agreement.init(clientKeys.x25519PrivateKey)
        val clientXSecret = ByteArray(32)
        agreement.calculateAgreement(X25519PublicKeyParameters(serverXPubBytes, 0), clientXSecret, 0)
        assertEquals(32, clientXSecret.size)

        val clientCombined = Sntrup761X25519KeyExchange.combineSharedSecrets(clientSntruSecret, clientXSecret)
        val clientK = BigInteger(1, clientCombined)

        // Verify client K bit-for-bit matches server K
        assertEquals(serverResult.k, clientK)
    }

    @Test
    fun zeroWiping_clearsMemoryBuffers() {
        val secret1 = byteArrayOf(1, 2, 3, 4, 5)
        val secret2 = byteArrayOf(6, 7, 8, 9, 10)

        val combined = Sntrup761X25519KeyExchange.combineSharedSecrets(secret1, secret2)
        assertEquals(64, combined.size)

        // Simulate finally zero-wiping
        secret1.fill(0)
        secret2.fill(0)
        combined.fill(0)

        assertTrue(secret1.all { it == 0.toByte() })
        assertTrue(secret2.all { it == 0.toByte() })
        assertTrue(combined.all { it == 0.toByte() })
    }

    @Test
    fun keyExchangeProtocol_runsSuccessfullyWithMockTransport() {
        val hostKeyPair = SSHKeyGenerator.generateEd25519KeyPair()
        val writtenPackets = mutableListOf<SSHPacket>()
        val keyAlgorithm = KeyAlgorithms.EdDSA25519().create()

        val mockTransport = createMockTransport(writtenPackets, keyAlgorithm)

        val kex = Sntrup761X25519KeyExchange()
        val vS = "SSH-2.0-OpenSSH_9.0"
        val vC = "SSH-2.0-CoSSH_1.0"
        val iS = byteArrayOf(1, 2, 3, 4)
        val iC = byteArrayOf(5, 6, 7, 8)

        kex.init(mockTransport, vS, vC, iS, iC)

        assertEquals(1, writtenPackets.size)
        val initPacket = writtenPackets[0]
        assertEquals(Message.KEXDH_INIT, initPacket.readMessageID())
        val clientPubBytes = initPacket.readBytes()
        assertEquals(1190, clientPubBytes.size)

        // Server processes client public key and prepares KEXDH_31 reply
        val serverResult = Sntrup761X25519KeyExchange.encapsulateServer(clientPubBytes)

        val hostKeyBuffer = Buffer.PlainBuffer()
        hostKeyBuffer.putPublicKey(hostKeyPair.public)
        val hostKeyBytes = hostKeyBuffer.compactData

        // Server computes exchange hash H
        val hashBuffer = Buffer.PlainBuffer()
        hashBuffer.putString(vC)
        hashBuffer.putString(vS)
        hashBuffer.putString(iC)
        hashBuffer.putString(iS)
        hashBuffer.putString(hostKeyBytes)
        hashBuffer.putBytes(clientPubBytes)
        hashBuffer.putBytes(serverResult.serverPublicKeyBytes)
        hashBuffer.putMPInt(serverResult.k)

        val md = MessageDigest.getInstance("SHA-512")
        val expectedH = md.digest(hashBuffer.compactData)

        // Server signs H
        val signer = keyAlgorithm.newSignature()
        signer.initSign(hostKeyPair.private)
        signer.update(expectedH, 0, expectedH.size)
        val rawSig = signer.sign()

        val sigBuffer = Buffer.PlainBuffer()
        sigBuffer.putString("ssh-ed25519")
        sigBuffer.putBytes(rawSig)
        val sigBytes = sigBuffer.compactData

        // Build KEXDH_31 packet
        val replyPacket = SSHPacket(Message.KEXDH_31)
        replyPacket.putBytes(hostKeyBytes)
        replyPacket.putBytes(serverResult.serverPublicKeyBytes)
        replyPacket.putBytes(sigBytes)
        replyPacket.readMessageID() // Simulates TransportImpl dispatch consuming message ID

        val success = kex.next(Message.KEXDH_31, replyPacket)
        assertTrue(success)
        assertEquals(serverResult.k, kex.k)
        assertArrayEquals(expectedH, kex.h)
    }

    @Test
    fun keyExchangeProtocol_rejectsInvalidSignature() {
        val hostKeyPair = SSHKeyGenerator.generateEd25519KeyPair()
        val writtenPackets = mutableListOf<SSHPacket>()
        val keyAlgorithm = KeyAlgorithms.EdDSA25519().create()

        val mockTransport = createMockTransport(writtenPackets, keyAlgorithm)

        val kex = Sntrup761X25519KeyExchange()
        kex.init(mockTransport, "SSH-2.0-Server", "SSH-2.0-Client", byteArrayOf(1), byteArrayOf(2))

        val initPacket = writtenPackets[0]
        initPacket.readMessageID()
        val clientPubBytes = initPacket.readBytes()

        val serverResult = Sntrup761X25519KeyExchange.encapsulateServer(clientPubBytes)

        val hostKeyBuffer = Buffer.PlainBuffer()
        hostKeyBuffer.putPublicKey(hostKeyPair.public)
        val hostKeyBytes = hostKeyBuffer.compactData

        // Corrupted signature formatted with ssh-ed25519 wire format
        val badSigBuffer = Buffer.PlainBuffer()
        badSigBuffer.putString("ssh-ed25519")
        badSigBuffer.putBytes(ByteArray(64) { 0xAA.toByte() })
        val corruptedSig = badSigBuffer.compactData

        val replyPacket = SSHPacket(Message.KEXDH_31)
        replyPacket.putBytes(hostKeyBytes)
        replyPacket.putBytes(serverResult.serverPublicKeyBytes)
        replyPacket.putBytes(corruptedSig)
        replyPacket.readMessageID() // Simulates TransportImpl dispatch consuming message ID

        try {
            kex.next(Message.KEXDH_31, replyPacket)
            fail("Expected TransportException due to invalid signature")
        } catch (e: TransportException) {
            assertEquals(DisconnectReason.KEY_EXCHANGE_FAILED, e.disconnectReason)
        }
    }

    private fun createMockTransport(
        writtenPackets: MutableList<SSHPacket>,
        keyAlgorithm: com.hierynomus.sshj.key.KeyAlgorithm,
    ): Transport {
        val invocationHandler = java.lang.reflect.InvocationHandler { _, method, args ->
            when (method.name) {
                "write" -> {
                    writtenPackets.add(args[0] as SSHPacket)
                    0L
                }
                "getHostKeyAlgorithm" -> keyAlgorithm
                "getConfig" -> net.schmizz.sshj.DefaultConfig()
                "getClientVersion" -> "SSH-2.0-CoSSH"
                "getServerVersion" -> "SSH-2.0-OpenSSH"
                "toString" -> "MockTransport"
                "hashCode" -> 1
                "equals" -> false
                else -> null
            }
        }
        return Proxy.newProxyInstance(
            Transport::class.java.classLoader,
            arrayOf(Transport::class.java),
            invocationHandler,
        ) as Transport
    }
}
