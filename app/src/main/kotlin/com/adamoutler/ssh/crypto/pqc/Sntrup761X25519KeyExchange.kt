package com.adamoutler.ssh.crypto.pqc

import net.schmizz.sshj.common.DisconnectReason
import net.schmizz.sshj.common.Factory
import net.schmizz.sshj.common.Message
import net.schmizz.sshj.common.SSHPacket
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.transport.Transport
import net.schmizz.sshj.transport.TransportException
import net.schmizz.sshj.transport.digest.SHA512
import net.schmizz.sshj.transport.kex.KeyExchange
import net.schmizz.sshj.transport.kex.KeyExchangeBase
import org.bouncycastle.crypto.KeyGenerationParameters
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.pqc.crypto.ntruprime.SNTRUPrimeKEMExtractor
import org.bouncycastle.pqc.crypto.ntruprime.SNTRUPrimeKEMGenerator
import org.bouncycastle.pqc.crypto.ntruprime.SNTRUPrimeKeyGenerationParameters
import org.bouncycastle.pqc.crypto.ntruprime.SNTRUPrimeKeyPairGenerator
import org.bouncycastle.pqc.crypto.ntruprime.SNTRUPrimeParameters
import org.bouncycastle.pqc.crypto.ntruprime.SNTRUPrimePrivateKeyParameters
import org.bouncycastle.pqc.crypto.ntruprime.SNTRUPrimePublicKeyParameters
import java.math.BigInteger
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Implementation of OpenSSH hybrid Post-Quantum Key Exchange algorithm
 * `sntrup761x25519-sha512@openssh.com`.
 *
 * This key exchange combines:
 * 1. Streamlined NTRU Prime `sntrup761` lattice-based post-quantum KEM (1158-byte public key, 1039-byte ciphertext)
 * 2. Curve25519 (`X25519`) ECDH (32-byte public key, 32-byte shared secret)
 *
 * Ephemeral client public key: `sntrup761_public_key (1158 bytes) || x25519_public_key (32 bytes)` (1190 bytes total).
 * Ephemeral server public key: `sntrup761_ciphertext (1039 bytes) || x25519_public_key (32 bytes)` (1071 bytes total).
 *
 * Shared secret combiner:
 * $$K = \text{SHA-512}(ss_{\text{sntrup761}} \parallel ss_{\text{x25519}})$$
 * All ephemeral secret arrays are strictly zeroized in finally blocks to prevent volatile memory leakage.
 */
class Sntrup761X25519KeyExchange : KeyExchangeBase(SHA512()) {

    companion object {
        const val NAME = "sntrup761x25519-sha512@openssh.com"
        const val SNTRUP761_PUBLIC_KEY_SIZE = 1158
        const val SNTRUP761_CIPHERTEXT_SIZE = 1039
        const val X25519_KEY_SIZE = 32
        const val CLIENT_PUBLIC_KEY_SIZE = SNTRUP761_PUBLIC_KEY_SIZE + X25519_KEY_SIZE // 1190 bytes
        const val SERVER_PUBLIC_KEY_SIZE = SNTRUP761_CIPHERTEXT_SIZE + X25519_KEY_SIZE // 1071 bytes

        /**
         * Combines post-quantum and classical shared secrets via SHA-512.
         * The caller or finally block must zero-wipe the input buffers and result when finished.
         */
        fun combineSharedSecrets(sntrupSecret: ByteArray, x25519Secret: ByteArray): ByteArray {
            val md = MessageDigest.getInstance("SHA-512")
            md.update(sntrupSecret)
            md.update(x25519Secret)
            return md.digest()
        }

        /**
         * Generates an ephemeral client keypair for sntrup761 + x25519.
         */
        fun generateClientKeys(random: SecureRandom = SecureRandom()): ClientKeyExchangeKeys {
            val sntruGen = SNTRUPrimeKeyPairGenerator()
            sntruGen.init(SNTRUPrimeKeyGenerationParameters(random, SNTRUPrimeParameters.sntrup761))
            val sntruPair = sntruGen.generateKeyPair()
            val sntruPub = (sntruPair.public as SNTRUPrimePublicKeyParameters).encoded
            val sntruPriv = sntruPair.private as SNTRUPrimePrivateKeyParameters

            val xGen = X25519KeyPairGenerator()
            xGen.init(KeyGenerationParameters(random, 256))
            val xPair = xGen.generateKeyPair()
            val xPub = (xPair.public as X25519PublicKeyParameters).encoded
            val xPriv = xPair.private as X25519PrivateKeyParameters

            val clientPubKey = ByteArray(CLIENT_PUBLIC_KEY_SIZE)
            System.arraycopy(sntruPub, 0, clientPubKey, 0, SNTRUP761_PUBLIC_KEY_SIZE)
            System.arraycopy(xPub, 0, clientPubKey, SNTRUP761_PUBLIC_KEY_SIZE, X25519_KEY_SIZE)

            return ClientKeyExchangeKeys(
                sntruPrivateKey = sntruPriv,
                x25519PrivateKey = xPriv,
                clientPublicKeyBytes = clientPubKey,
            )
        }

        /**
         * Server-side helper to encapsulate a post-quantum shared secret and perform X25519 DH.
         * Used for integration testing and simulation.
         */
        fun encapsulateServer(
            clientPubKey: ByteArray,
            random: SecureRandom = SecureRandom(),
        ): ServerKeyExchangeResult {
            require(clientPubKey.size == CLIENT_PUBLIC_KEY_SIZE) {
                "Invalid client public key size: ${clientPubKey.size}, expected $CLIENT_PUBLIC_KEY_SIZE"
            }
            val clientSntruPubBytes = clientPubKey.copyOfRange(0, SNTRUP761_PUBLIC_KEY_SIZE)
            val clientX25519PubBytes = clientPubKey.copyOfRange(SNTRUP761_PUBLIC_KEY_SIZE, CLIENT_PUBLIC_KEY_SIZE)

            val sntruPubParams = SNTRUPrimePublicKeyParameters(SNTRUPrimeParameters.sntrup761, clientSntruPubBytes)
            val kemGenerator = SNTRUPrimeKEMGenerator(random)
            val secretWithEncapsulation = kemGenerator.generateEncapsulated(sntruPubParams)
            val sntruCiphertext = secretWithEncapsulation.encapsulation
            val sntruSecret = secretWithEncapsulation.secret

            val x25519Gen = X25519KeyPairGenerator()
            x25519Gen.init(KeyGenerationParameters(random, 256))
            val xPair = x25519Gen.generateKeyPair()
            val serverXPriv = xPair.private as X25519PrivateKeyParameters
            val serverXPub = (xPair.public as X25519PublicKeyParameters).encoded

            val serverPubKey = ByteArray(SERVER_PUBLIC_KEY_SIZE)
            System.arraycopy(sntruCiphertext, 0, serverPubKey, 0, SNTRUP761_CIPHERTEXT_SIZE)
            System.arraycopy(serverXPub, 0, serverPubKey, SNTRUP761_CIPHERTEXT_SIZE, X25519_KEY_SIZE)

            val agreement = X25519Agreement()
            agreement.init(serverXPriv)
            val x25519Secret = ByteArray(X25519_KEY_SIZE)
            agreement.calculateAgreement(X25519PublicKeyParameters(clientX25519PubBytes, 0), x25519Secret, 0)

            var combinedSecret: ByteArray? = null
            try {
                combinedSecret = combineSharedSecrets(sntruSecret, x25519Secret)
                val k = BigInteger(1, combinedSecret)
                return ServerKeyExchangeResult(
                    serverPublicKeyBytes = serverPubKey,
                    k = k,
                )
            } finally {
                sntruSecret.fill(0)
                x25519Secret.fill(0)
                combinedSecret?.fill(0)
            }
        }
    }

    private var clientKeys: ClientKeyExchangeKeys? = null
    private var clientPubKeyBytes: ByteArray? = null
    private var k: BigInteger? = null

    override fun init(
        trans: Transport,
        V_S: String,
        V_C: String,
        I_S: ByteArray,
        I_C: ByteArray,
    ) {
        super.init(trans, V_S, V_C, I_S, I_C)
        digest.init()

        val generated = generateClientKeys()
        clientKeys = generated
        clientPubKeyBytes = generated.clientPublicKeyBytes

        val packet = SSHPacket(Message.KEXDH_INIT)
        packet.putBytes(generated.clientPublicKeyBytes)
        trans.write(packet)
    }

    override fun next(cmd: Message, packet: SSHPacket): Boolean {
        if (cmd != Message.KEXDH_31) {
            throw TransportException(DisconnectReason.KEY_EXCHANGE_FAILED, "Unexpected packet: $cmd")
        }

        val keys = clientKeys ?: throw TransportException(
            DisconnectReason.KEY_EXCHANGE_FAILED,
            "Client ephemeral keys not initialized",
        )
        val clientPub = clientPubKeyBytes ?: throw TransportException(
            DisconnectReason.KEY_EXCHANGE_FAILED,
            "Client public key not set",
        )

        val hostKeyBytes = packet.readBytes()
        val serverPubKey = packet.readBytes()
        val sig = packet.readBytes()

        if (serverPubKey.size != SERVER_PUBLIC_KEY_SIZE) {
            throw TransportException(
                DisconnectReason.KEY_EXCHANGE_FAILED,
                "Invalid server public key size: ${serverPubKey.size}, expected $SERVER_PUBLIC_KEY_SIZE",
            )
        }

        try {
            hostKey = Buffer.PlainBuffer(hostKeyBytes).readPublicKey()
        } catch (e: Exception) {
            throw TransportException(DisconnectReason.KEY_EXCHANGE_FAILED, "Failed to parse host key", e)
        }

        val sntruCiphertext = serverPubKey.copyOfRange(0, SNTRUP761_CIPHERTEXT_SIZE)
        val serverX25519PubBytes = serverPubKey.copyOfRange(SNTRUP761_CIPHERTEXT_SIZE, SERVER_PUBLIC_KEY_SIZE)

        var sntrupSecret: ByteArray? = null
        var x25519Secret: ByteArray? = null
        var combinedSecret: ByteArray? = null

        try {
            val extractor = SNTRUPrimeKEMExtractor(keys.sntruPrivateKey)
            sntrupSecret = extractor.extractSecret(sntruCiphertext)

            val agreement = X25519Agreement()
            agreement.init(keys.x25519PrivateKey)
            val xSec = ByteArray(X25519_KEY_SIZE)
            agreement.calculateAgreement(X25519PublicKeyParameters(serverX25519PubBytes, 0), xSec, 0)
            x25519Secret = xSec

            combinedSecret = combineSharedSecrets(sntrupSecret, x25519Secret)
            k = BigInteger(1, combinedSecret)
        } catch (e: GeneralSecurityException) {
            throw TransportException(DisconnectReason.KEY_EXCHANGE_FAILED, "Cryptographic agreement failed", e)
        } finally {
            sntrupSecret?.fill(0)
            x25519Secret?.fill(0)
            combinedSecret?.fill(0)
        }

        val computedK = k ?: throw TransportException(
            DisconnectReason.KEY_EXCHANGE_FAILED,
            "Shared secret K calculation failed",
        )

        val buf = initializedBuffer()
        buf.putString(hostKeyBytes)
        buf.putBytes(clientPub)
        buf.putBytes(serverPubKey)
        buf.putMPInt(computedK)

        digest.update(buf.array(), buf.rpos(), buf.available())
        H = digest.digest()

        val signature = trans.hostKeyAlgorithm.newSignature()
        if (hostKey is com.hierynomus.sshj.userauth.certificate.Certificate<*>) {
            signature.initVerify((hostKey as com.hierynomus.sshj.userauth.certificate.Certificate<*>).key)
        } else {
            signature.initVerify(hostKey)
        }
        signature.update(H, 0, H.size)

        if (!signature.verify(sig)) {
            throw TransportException(DisconnectReason.KEY_EXCHANGE_FAILED, "KeyExchange signature verification failed")
        }

        return true
    }

    override fun getK(): BigInteger = k ?: throw IllegalStateException("Key exchange not complete; K is null")

    /**
     * SSHJ Factory for instantiating [Sntrup761X25519KeyExchange].
     */
    class Factory : net.schmizz.sshj.common.Factory.Named<KeyExchange> {
        override fun getName(): String = NAME
        override fun create(): KeyExchange = Sntrup761X25519KeyExchange()
    }
}

/**
 * Ephemeral client key pair container for sntrup761 + x25519 hybrid exchange.
 */
data class ClientKeyExchangeKeys(
    val sntruPrivateKey: SNTRUPrimePrivateKeyParameters,
    val x25519PrivateKey: X25519PrivateKeyParameters,
    val clientPublicKeyBytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ClientKeyExchangeKeys) return false
        return clientPublicKeyBytes.contentEquals(other.clientPublicKeyBytes)
    }

    override fun hashCode(): Int = clientPublicKeyBytes.contentHashCode()
}

/**
 * Server-side encapsulation result.
 */
data class ServerKeyExchangeResult(
    val serverPublicKeyBytes: ByteArray,
    val k: BigInteger,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ServerKeyExchangeResult) return false
        return serverPublicKeyBytes.contentEquals(other.serverPublicKeyBytes) && k == other.k
    }

    override fun hashCode(): Int = 31 * serverPublicKeyBytes.contentHashCode() + k.hashCode()
}
