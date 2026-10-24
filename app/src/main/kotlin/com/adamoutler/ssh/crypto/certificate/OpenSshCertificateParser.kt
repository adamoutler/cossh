package com.adamoutler.ssh.crypto.certificate

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory
import org.bouncycastle.jce.ECNamedCurveTable
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.PublicKey
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Parses and serializes OpenSSH Certificates according to PROTOCOL.certkeys.
 */
object OpenSshCertificateParser {

    /**
     * Parses an OpenSSH certificate from:
     * - An authorized_keys or known_hosts line (e.g. "@cert-authority *.internal ssh-ed25519-cert-v01@openssh.com AAAA... key_id")
     * - A raw space-separated line ("ssh-ed25519-cert-v01@openssh.com AAAA... key_id")
     * - A raw base64 string
     */
    fun parse(encodedOrAuthorizedLine: String): OpenSshCertificate {
        val trimmed = encodedOrAuthorizedLine.trim()
        val tokens = trimmed.split("\\s+".toRegex())

        val base64Token = when {
            tokens.size >= 3 && tokens[0].startsWith("@") -> tokens[2]
            tokens.size >= 2 && tokens[0].contains("-cert-v01@openssh.com") -> tokens[1]
            tokens.size == 1 -> tokens[0]
            else -> tokens.firstOrNull { isBase64(it) } ?: throw IllegalArgumentException("Could not find Base64 certificate payload")
        }

        val wireBytes = Base64.getDecoder().decode(base64Token)
        return parseWireBytes(wireBytes)
    }

    /**
     * Parses raw wire format bytes of an OpenSSH certificate.
     */
    fun parseWireBytes(wireBytes: ByteArray): OpenSshCertificate {
        val reader = WireBufferReader(wireBytes)

        val magic = reader.readString()
        val nonce = reader.readStringBytes()

        val subjectPublicKey = when {
            magic.startsWith("ssh-ed25519-cert") -> {
                val rawPk = reader.readStringBytes()
                createEd25519PublicKey(rawPk)
            }

            magic.startsWith("ssh-rsa-cert") || magic.startsWith("rsa-sha2-") -> {
                val e = reader.readMpint()
                val n = reader.readMpint()
                KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(n, e))
            }

            magic.startsWith("ecdsa-sha2-") -> {
                val curveName = reader.readString()
                val qBytes = reader.readStringBytes()
                createECPublicKey(curveName, qBytes)
            }

            else -> throw IllegalArgumentException("Unsupported certificate magic/key-type: $magic")
        }

        val serial = reader.readUint64()
        val certTypeCode = reader.readUint32().toInt()
        val certType = OpenSshCertType.fromCode(certTypeCode)
        val keyId = reader.readString()

        // Valid Principals
        val principalsBlob = reader.readStringBytes()
        val pReader = WireBufferReader(principalsBlob)
        val validPrincipals = mutableListOf<String>()
        while (pReader.hasRemaining()) {
            validPrincipals.add(pReader.readString())
        }

        val validAfter = reader.readUint64()
        val validBefore = reader.readUint64()

        // Critical Options
        val critBlob = reader.readStringBytes()
        val critReader = WireBufferReader(critBlob)
        val criticalOptions = mutableMapOf<String, String>()
        while (critReader.hasRemaining()) {
            val name = critReader.readString()
            val data = critReader.readString()
            criticalOptions[name] = data
        }

        // Extensions
        val extBlob = reader.readStringBytes()
        val extReader = WireBufferReader(extBlob)
        val extensions = mutableMapOf<String, String>()
        while (extReader.hasRemaining()) {
            val name = extReader.readString()
            val data = extReader.readString()
            extensions[name] = data
        }

        // Reserved (must be ignored/skipped)
        reader.readStringBytes()

        // Signature Key (CA Public Key)
        val caPubKeyBlob = reader.readStringBytes()
        val caPublicKey = parsePublicKeyBlob(caPubKeyBlob)

        // signedBytes includes everything from the start up to the signature blob
        val signedBytes = wireBytes.copyOfRange(0, reader.offset)

        // Signature Blob
        val sigBlob = reader.readStringBytes()
        val sigReader = WireBufferReader(sigBlob)
        val signatureAlgorithm = sigReader.readString()
        val rawSignatureBlob = sigReader.readStringBytes()

        return OpenSshCertificate(
            magic = magic,
            nonce = nonce,
            subjectPublicKey = subjectPublicKey,
            serial = serial,
            certType = certType,
            keyId = keyId,
            validPrincipals = validPrincipals,
            validAfterSeconds = validAfter,
            validBeforeSeconds = validBefore,
            criticalOptions = criticalOptions,
            extensions = extensions,
            caPublicKey = caPublicKey,
            signatureAlgorithm = signatureAlgorithm,
            rawSignatureBlob = rawSignatureBlob,
            signedBytes = signedBytes,
        )
    }

    /**
     * Serializes an OpenSshCertificate to wire binary format.
     */
    fun serializeWireBytes(cert: OpenSshCertificate): ByteArray {
        val writer = WireBufferWriter()
        writer.writeString(cert.magic)
        writer.writeStringBytes(cert.nonce)

        when (val pub = cert.subjectPublicKey) {
            is RSAPublicKey -> {
                writer.writeMpint(pub.publicExponent)
                writer.writeMpint(pub.modulus)
            }

            is ECPublicKey -> {
                val curveName = getCurveName(cert.magic)
                writer.writeString(curveName)
                writer.writeStringBytes(encodeECPoint(pub))
            }

            else -> {
                // Ed25519
                val rawPk = extractEd25519RawBytes(pub)
                writer.writeStringBytes(rawPk)
            }
        }

        writer.writeUint64(cert.serial)
        writer.writeUint32(cert.certType.code.toLong())
        writer.writeString(cert.keyId)

        // Principals
        val pWriter = WireBufferWriter()
        for (principal in cert.validPrincipals) {
            pWriter.writeString(principal)
        }
        writer.writeStringBytes(pWriter.toByteArray())

        writer.writeUint64(cert.validAfterSeconds)
        writer.writeUint64(cert.validBeforeSeconds)

        // Critical Options
        val critWriter = WireBufferWriter()
        for ((name, data) in cert.criticalOptions) {
            critWriter.writeString(name)
            critWriter.writeString(data)
        }
        writer.writeStringBytes(critWriter.toByteArray())

        // Extensions
        val extWriter = WireBufferWriter()
        for ((name, data) in cert.extensions) {
            extWriter.writeString(name)
            extWriter.writeString(data)
        }
        writer.writeStringBytes(extWriter.toByteArray())

        // Reserved
        writer.writeStringBytes(ByteArray(0))

        // CA Public Key
        writer.writeStringBytes(encodePublicKeyBlob(cert.caPublicKey))

        // Signature Blob
        val sigWriter = WireBufferWriter()
        sigWriter.writeString(cert.signatureAlgorithm)
        sigWriter.writeStringBytes(cert.rawSignatureBlob)
        writer.writeStringBytes(sigWriter.toByteArray())

        return writer.toByteArray()
    }

    /**
     * Encodes an OpenSshCertificate into an authorized_keys / known_hosts line.
     */
    fun encodeAuthorizedLine(cert: OpenSshCertificate, comment: String? = null): String {
        val bytes = serializeWireBytes(cert)
        val b64 = Base64.getEncoder().encodeToString(bytes)
        return if (comment != null) {
            "${cert.magic} $b64 $comment"
        } else {
            "${cert.magic} $b64"
        }
    }

    fun parsePublicKeyBlob(blob: ByteArray): PublicKey {
        val reader = WireBufferReader(blob)
        val type = reader.readString()
        return when (type) {
            "ssh-ed25519" -> {
                val raw = reader.readStringBytes()
                createEd25519PublicKey(raw)
            }

            "ssh-rsa" -> {
                val e = reader.readMpint()
                val n = reader.readMpint()
                KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(n, e))
            }

            "ecdsa-sha2-nistp256", "ecdsa-sha2-nistp384", "ecdsa-sha2-nistp521" -> {
                val curveName = reader.readString()
                val qBytes = reader.readStringBytes()
                createECPublicKey(curveName, qBytes)
            }

            else -> throw IllegalArgumentException("Unsupported public key type in blob: $type")
        }
    }

    fun encodePublicKeyBlob(publicKey: PublicKey): ByteArray {
        val writer = WireBufferWriter()
        when (publicKey) {
            is RSAPublicKey -> {
                writer.writeString("ssh-rsa")
                writer.writeMpint(publicKey.publicExponent)
                writer.writeMpint(publicKey.modulus)
            }

            is ECPublicKey -> {
                val curveName = when (publicKey.params.curve.field.fieldSize) {
                    256 -> "nistp256"
                    384 -> "nistp384"
                    521 -> "nistp521"
                    else -> "nistp256"
                }
                val keyType = "ecdsa-sha2-$curveName"
                writer.writeString(keyType)
                writer.writeString(curveName)
                writer.writeStringBytes(encodeECPoint(publicKey))
            }

            else -> {
                writer.writeString("ssh-ed25519")
                writer.writeStringBytes(extractEd25519RawBytes(publicKey))
            }
        }
        return writer.toByteArray()
    }

    fun createEd25519PublicKey(rawPk: ByteArray): PublicKey {
        val params = Ed25519PublicKeyParameters(rawPk, 0)
        val spki = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(params)
        val keySpec = X509EncodedKeySpec(spki.encoded)
        val kf = try {
            KeyFactory.getInstance("Ed25519", "BC")
        } catch (_: Throwable) {
            KeyFactory.getInstance("Ed25519")
        }
        return kf.generatePublic(keySpec)
    }

    fun extractEd25519RawBytes(pubKey: PublicKey): ByteArray {
        val encoded = pubKey.encoded
        return if (encoded.size >= 32) {
            encoded.copyOfRange(encoded.size - 32, encoded.size)
        } else {
            encoded
        }
    }

    fun createECPublicKey(curveName: String, qBytes: ByteArray): PublicKey {
        val normalizedCurve = when (curveName) {
            "nistp256" -> "secp256r1"
            "nistp384" -> "secp384r1"
            "nistp521" -> "secp521r1"
            else -> curveName
        }
        val ecSpec = ECNamedCurveTable.getParameterSpec(normalizedCurve)
            ?: throw IllegalArgumentException("Unknown EC curve: $curveName")
        val point = ecSpec.curve.decodePoint(qBytes)
        val x = point.affineXCoord.toBigInteger()
        val y = point.affineYCoord.toBigInteger()
        val ecPoint = java.security.spec.ECPoint(x, y)

        val params = java.security.AlgorithmParameters.getInstance("EC")
        params.init(java.security.spec.ECGenParameterSpec(normalizedCurve))
        val javaEcSpec = params.getParameterSpec(java.security.spec.ECParameterSpec::class.java)
        val pubSpec = java.security.spec.ECPublicKeySpec(ecPoint, javaEcSpec)

        return KeyFactory.getInstance("EC").generatePublic(pubSpec)
    }

    private fun encodeECPoint(ecPublicKey: ECPublicKey): ByteArray {
        val fieldSize = (ecPublicKey.params.curve.field.fieldSize + 7) / 8
        val xBytes = getPadded(ecPublicKey.w.affineX.toByteArray(), fieldSize)
        val yBytes = getPadded(ecPublicKey.w.affineY.toByteArray(), fieldSize)
        val point = ByteArray(1 + fieldSize * 2)
        point[0] = 0x04
        System.arraycopy(xBytes, 0, point, 1, fieldSize)
        System.arraycopy(yBytes, 0, point, 1 + fieldSize, fieldSize)
        return point
    }

    private fun getPadded(bytes: ByteArray, size: Int): ByteArray {
        if (bytes.size == size) return bytes
        val result = ByteArray(size)
        if (bytes.size > size) {
            System.arraycopy(bytes, bytes.size - size, result, 0, size)
        } else {
            System.arraycopy(bytes, 0, result, size - bytes.size, bytes.size)
        }
        return result
    }

    private fun getCurveName(magic: String): String = when {
        magic.contains("nistp256") -> "nistp256"
        magic.contains("nistp384") -> "nistp384"
        magic.contains("nistp521") -> "nistp521"
        else -> "nistp256"
    }

    private fun isBase64(str: String): Boolean {
        if (str.length % 4 != 0 && str.length < 16) return false
        return str.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '+' || it == '/' || it == '=' }
    }
}

/**
 * Helper class for reading OpenSSH wire types.
 */
class WireBufferReader(
    val bytes: ByteArray,
    var offset: Int = 0,
) {
    fun hasRemaining(): Boolean = offset < bytes.size
    fun remaining(): Int = bytes.size - offset

    fun readBytes(length: Int): ByteArray {
        checkRemaining(length)
        val result = bytes.copyOfRange(offset, offset + length)
        offset += length
        return result
    }

    fun readUint32(): Long {
        checkRemaining(4)
        val b0 = bytes[offset++].toLong() and 0xFF
        val b1 = bytes[offset++].toLong() and 0xFF
        val b2 = bytes[offset++].toLong() and 0xFF
        val b3 = bytes[offset++].toLong() and 0xFF
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    fun readUint64(): Long {
        checkRemaining(8)
        var result = 0L
        for (i in 0 until 8) {
            result = (result shl 8) or (bytes[offset++].toLong() and 0xFF)
        }
        return result
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

    private fun checkRemaining(needed: Int) {
        require(remaining() >= needed) {
            "Buffer underflow: needed $needed bytes, but only ${remaining()} remaining"
        }
    }
}

/**
 * Helper class for writing OpenSSH wire types.
 */
class WireBufferWriter {
    private val buffer = ByteArrayOutputStream()

    fun writeRawBytes(bytes: ByteArray): WireBufferWriter {
        buffer.write(bytes)
        return this
    }

    fun writeUint32(v: Long): WireBufferWriter {
        buffer.write(((v ushr 24) and 0xFF).toInt())
        buffer.write(((v ushr 16) and 0xFF).toInt())
        buffer.write(((v ushr 8) and 0xFF).toInt())
        buffer.write((v and 0xFF).toInt())
        return this
    }

    fun writeUint64(v: Long): WireBufferWriter {
        for (i in 7 downTo 0) {
            buffer.write(((v ushr (i * 8)) and 0xFF).toInt())
        }
        return this
    }

    fun writeStringBytes(bytes: ByteArray): WireBufferWriter {
        writeUint32(bytes.size.toLong())
        writeRawBytes(bytes)
        return this
    }

    fun writeString(str: String): WireBufferWriter = writeStringBytes(str.toByteArray(Charsets.UTF_8))

    fun writeMpint(value: BigInteger): WireBufferWriter {
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
