package com.adamoutler.ssh.crypto.fido2

import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.DERSequence
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * OpenSSH FIDO2 / U2F wire protocol parser and serializer conforming to `PROTOCOL.u2f`.
 *
 * Implements binary wire encoding/decoding for:
 * - `sk-ssh-ed25519@openssh.com`
 * - `sk-ecdsa-sha2-nistp256@openssh.com`
 *
 * Provides cryptographic WebAuthn authenticator signed data blob construction:
 * $$\text{SHA-256}(\text{application}) \parallel \text{flags} \parallel \text{counter} \parallel \text{SHA-256}(\text{message})$$
 * and ECDSA ASN.1 DER to $(r, s)$ wire mpint conversion.
 */
object OpenSshFido2WireParser {

    const val KEY_TYPE_SK_ED25519 = "sk-ssh-ed25519@openssh.com"
    const val KEY_TYPE_SK_ECDSA = "sk-ecdsa-sha2-nistp256@openssh.com"
    const val CURVE_NISTP256 = "nistp256"

    const val FLAG_USER_PRESENCE: Byte = 0x01
    const val FLAG_USER_VERIFIED: Byte = 0x04

    /**
     * Serializes a FIDO2 public key into OpenSSH wire format blob.
     */
    fun encodePublicKeyBlob(blob: Fido2PublicKeyBlob): ByteArray {
        val out = Fido2WireWriter()
        when (blob) {
            is Fido2PublicKeyBlob.Ed25519 -> {
                out.writeString(KEY_TYPE_SK_ED25519)
                out.writeStringBytes(blob.publicBytes)
                out.writeString(blob.application)
            }

            is Fido2PublicKeyBlob.EcdsaNistP256 -> {
                out.writeString(KEY_TYPE_SK_ECDSA)
                out.writeString(blob.curveName)
                out.writeStringBytes(blob.publicPoint)
                out.writeString(blob.application)
            }
        }
        return out.toByteArray()
    }

    /**
     * Parses an OpenSSH FIDO2 public key wire format blob.
     */
    fun parsePublicKeyBlob(bytes: ByteArray): Fido2PublicKeyBlob {
        val reader = Fido2WireReader(bytes)
        val keyType = reader.readString()
        return when (keyType) {
            KEY_TYPE_SK_ED25519 -> {
                val publicBytes = reader.readStringBytes()
                val application = reader.readString()
                Fido2PublicKeyBlob.Ed25519(publicBytes = publicBytes, application = application)
            }

            KEY_TYPE_SK_ECDSA -> {
                val curveName = reader.readString()
                val publicPoint = reader.readStringBytes()
                val application = reader.readString()
                Fido2PublicKeyBlob.EcdsaNistP256(
                    curveName = curveName,
                    publicPoint = publicPoint,
                    application = application,
                )
            }

            else -> throw IllegalArgumentException("Unsupported FIDO2 key type: $keyType")
        }
    }

    /**
     * Serializes a FIDO2 wire signature according to OpenSSH PROTOCOL.u2f.
     */
    fun serializeWireSignature(signature: Fido2WireSignature): ByteArray {
        val out = Fido2WireWriter()
        when (signature) {
            is Fido2WireSignature.Ed25519 -> {
                out.writeString(KEY_TYPE_SK_ED25519)
                out.writeStringBytes(signature.signature)
                out.writeByte(signature.flags)
                out.writeUint32(signature.counter)
            }

            is Fido2WireSignature.EcdsaNistP256 -> {
                out.writeString(KEY_TYPE_SK_ECDSA)
                out.writeMpint(signature.r)
                out.writeMpint(signature.s)
                out.writeByte(signature.flags)
                out.writeUint32(signature.counter)
            }
        }
        return out.toByteArray()
    }

    /**
     * Parses a FIDO2 wire signature from OpenSSH wire bytes.
     */
    fun parseWireSignature(bytes: ByteArray): Fido2WireSignature {
        val reader = Fido2WireReader(bytes)
        val keyType = reader.readString()
        return when (keyType) {
            KEY_TYPE_SK_ED25519 -> {
                val sigBytes = reader.readStringBytes()
                val flags = reader.readByte()
                val counter = reader.readUint32()
                Fido2WireSignature.Ed25519(
                    signature = sigBytes,
                    flags = flags,
                    counter = counter,
                )
            }

            KEY_TYPE_SK_ECDSA -> {
                val r = reader.readMpint()
                val s = reader.readMpint()
                val flags = reader.readByte()
                val counter = reader.readUint32()
                Fido2WireSignature.EcdsaNistP256(
                    r = r,
                    s = s,
                    flags = flags,
                    counter = counter,
                )
            }

            else -> throw IllegalArgumentException("Unsupported FIDO2 signature type: $keyType")
        }
    }

    /**
     * Constructs the 69-byte WebAuthn signed data blob:
     * $$\text{SHA-256}(\text{application}) \parallel \text{flags} \parallel \text{counter} \parallel \text{SHA-256}(\text{message})$$
     */
    fun buildSignedDataBlob(
        application: String,
        flags: Byte,
        counter: Long,
        message: ByteArray,
    ): ByteArray {
        val appHash = MessageDigest.getInstance("SHA-256").digest(application.toByteArray(Charsets.UTF_8))
        val msgHash = MessageDigest.getInstance("SHA-256").digest(message)

        val out = ByteBuffer.allocate(69)
        out.put(appHash)
        out.put(flags)
        out.putInt((counter and 0xFFFFFFFFL).toInt())
        out.put(msgHash)
        return out.array()
    }

    /**
     * Extracts $(r, s)$ coordinates from an ASN.1 DER-encoded ECDSA signature.
     */
    fun extractEcdsaRs(derSignature: ByteArray): Pair<BigInteger, BigInteger> {
        val seq = ASN1Sequence.getInstance(derSignature)
        require(seq.size() >= 2) { "Malformed ASN.1 DER ECDSA sequence" }
        val r = (seq.getObjectAt(0) as ASN1Integer).value
        val s = (seq.getObjectAt(1) as ASN1Integer).value
        return Pair(r, s)
    }

    /**
     * Encodes $(r, s)$ coordinates into an ASN.1 DER sequence.
     */
    fun encodeEcdsaRsToDer(r: BigInteger, s: BigInteger): ByteArray {
        val vec = ASN1EncodableVector()
        vec.add(ASN1Integer(r))
        vec.add(ASN1Integer(s))
        return DERSequence(vec).encoded
    }

    /**
     * Validates presence (0x01) and verification (0x04) flags.
     */
    fun validateFlags(
        flags: Byte,
        requireUserPresence: Boolean = true,
        requireUserVerification: Boolean = false,
    ): Boolean {
        val f = flags.toInt() and 0xFF
        if (requireUserPresence && (f and FLAG_USER_PRESENCE.toInt()) == 0) return false
        if (requireUserVerification && (f and FLAG_USER_VERIFIED.toInt()) == 0) return false
        return true
    }
}

/**
 * OpenSSH FIDO2 Public Key Blob representation.
 */
sealed class Fido2PublicKeyBlob {
    abstract val keyType: String
    abstract val application: String

    data class Ed25519(
        val publicBytes: ByteArray,
        override val application: String = "ssh:",
    ) : Fido2PublicKeyBlob() {
        override val keyType: String = OpenSshFido2WireParser.KEY_TYPE_SK_ED25519

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Ed25519) return false
            return publicBytes.contentEquals(other.publicBytes) && application == other.application
        }

        override fun hashCode(): Int = 31 * publicBytes.contentHashCode() + application.hashCode()
    }

    data class EcdsaNistP256(
        val curveName: String = OpenSshFido2WireParser.CURVE_NISTP256,
        val publicPoint: ByteArray,
        override val application: String = "ssh:",
    ) : Fido2PublicKeyBlob() {
        override val keyType: String = OpenSshFido2WireParser.KEY_TYPE_SK_ECDSA

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EcdsaNistP256) return false
            return curveName == other.curveName && publicPoint.contentEquals(other.publicPoint) && application == other.application
        }

        override fun hashCode(): Int = 31 * (31 * curveName.hashCode() + publicPoint.contentHashCode()) + application.hashCode()
    }
}

/**
 * OpenSSH FIDO2 Wire Signature representation.
 */
sealed class Fido2WireSignature {
    abstract val keyType: String
    abstract val flags: Byte
    abstract val counter: Long

    data class Ed25519(
        val signature: ByteArray,
        override val flags: Byte,
        override val counter: Long,
    ) : Fido2WireSignature() {
        override val keyType: String = OpenSshFido2WireParser.KEY_TYPE_SK_ED25519

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Ed25519) return false
            return signature.contentEquals(other.signature) && flags == other.flags && counter == other.counter
        }

        override fun hashCode(): Int = 31 * (31 * signature.contentHashCode() + flags.hashCode()) + counter.hashCode()
    }

    data class EcdsaNistP256(
        val r: BigInteger,
        val s: BigInteger,
        override val flags: Byte,
        override val counter: Long,
    ) : Fido2WireSignature() {
        override val keyType: String = OpenSshFido2WireParser.KEY_TYPE_SK_ECDSA
    }
}

/**
 * Helper reader for OpenSSH binary wire types.
 */
internal class Fido2WireReader(
    private val bytes: ByteArray,
    private var offset: Int = 0,
) {
    fun hasRemaining(): Boolean = offset < bytes.size
    fun remaining(): Int = bytes.size - offset

    fun readByte(): Byte {
        require(remaining() >= 1) { "Buffer underflow reading byte" }
        return bytes[offset++]
    }

    fun readBytes(length: Int): ByteArray {
        require(remaining() >= length) { "Buffer underflow reading $length bytes" }
        val result = bytes.copyOfRange(offset, offset + length)
        offset += length
        return result
    }

    fun readUint32(): Long {
        require(remaining() >= 4) { "Buffer underflow reading uint32" }
        val b0 = bytes[offset++].toLong() and 0xFF
        val b1 = bytes[offset++].toLong() and 0xFF
        val b2 = bytes[offset++].toLong() and 0xFF
        val b3 = bytes[offset++].toLong() and 0xFF
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    fun readStringBytes(): ByteArray {
        val len = readUint32().toInt()
        require(len >= 0 && len <= remaining()) { "Invalid string length: $len" }
        return readBytes(len)
    }

    fun readString(): String = String(readStringBytes(), Charsets.UTF_8)

    fun readMpint(): BigInteger {
        val mpBytes = readStringBytes()
        if (mpBytes.isEmpty()) return BigInteger.ZERO
        return BigInteger(mpBytes)
    }
}

/**
 * Helper writer for OpenSSH binary wire types.
 */
internal class Fido2WireWriter {
    private val buffer = ByteArrayOutputStream()

    fun writeByte(b: Byte): Fido2WireWriter {
        buffer.write(b.toInt() and 0xFF)
        return this
    }

    fun writeRawBytes(bytes: ByteArray): Fido2WireWriter {
        buffer.write(bytes)
        return this
    }

    fun writeUint32(v: Long): Fido2WireWriter {
        buffer.write(((v ushr 24) and 0xFF).toInt())
        buffer.write(((v ushr 16) and 0xFF).toInt())
        buffer.write(((v ushr 8) and 0xFF).toInt())
        buffer.write((v and 0xFF).toInt())
        return this
    }

    fun writeStringBytes(bytes: ByteArray): Fido2WireWriter {
        writeUint32(bytes.size.toLong())
        writeRawBytes(bytes)
        return this
    }

    fun writeString(str: String): Fido2WireWriter = writeStringBytes(str.toByteArray(Charsets.UTF_8))

    fun writeMpint(value: BigInteger): Fido2WireWriter {
        if (value == BigInteger.ZERO) {
            writeUint32(0)
            return this
        }
        val bytes = value.toByteArray()
        writeStringBytes(bytes)
        return this
    }

    fun toByteArray(): ByteArray = buffer.toByteArray()
}
