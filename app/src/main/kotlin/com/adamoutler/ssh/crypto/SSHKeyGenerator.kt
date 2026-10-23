package com.adamoutler.ssh.crypto

import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAKeyGenParameterSpec
import java.util.Base64

object SSHKeyGenerator {

    /**
     * Generates an ED25519 KeyPair using native Android crypto providers.
     */
    fun generateEd25519KeyPair(): KeyPair {
        val keyPairGenerator = try {
            KeyPairGenerator.getInstance("Ed25519", "BC")
        } catch (_: Exception) {
            KeyPairGenerator.getInstance("Ed25519")
        }
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Generates an RSA-4096 KeyPair.
     * 4096-bit is the absolute minimum standard for modern RSA.
     */
    fun generateRSAKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        val spec = RSAKeyGenParameterSpec(4096, RSAKeyGenParameterSpec.F4)
        val secureRandom = SecureRandom()

        keyPairGenerator.initialize(spec, secureRandom)
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Generates an ECDSA KeyPair for the given NIST curve.
     * Supported standard curves: "secp256r1" (nistp256), "secp384r1" (nistp384), "secp521r1" (nistp521).
     */
    fun generateECDSAKeyPair(curveName: String = "secp256r1"): KeyPair {
        val keyPairGenerator = try {
            KeyPairGenerator.getInstance("EC", "BC")
        } catch (_: Exception) {
            KeyPairGenerator.getInstance("EC")
        }
        val ecSpec = java.security.spec.ECGenParameterSpec(curveName)
        keyPairGenerator.initialize(ecSpec, SecureRandom())
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Encodes a public key into OpenSSH format (e.g., "ssh-ed25519 AAAA...", "ecdsa-sha2-nistp256 AAAA...").
     */
    fun encodePublicKey(keyPair: KeyPair): String {
        val publicKey = keyPair.public
        return when (publicKey.algorithm) {
            "Ed25519", "EdDSA", "1.3.101.112" -> {
                val keyBytes = publicKey.encoded
                // Java Ed25519 encoded format is DER. We need the raw 32 bytes at the end.
                val rawBytes = keyBytes.sliceArray(keyBytes.size - 32 until keyBytes.size)
                val type = "ssh-ed25519"
                val encoded = Base64.getEncoder().encodeToString(writeSshBytes(type, rawBytes))
                "$type $encoded"
            }

            "RSA" -> {
                val rsaPubKey = publicKey as RSAPublicKey
                val type = "ssh-rsa"
                val encoded = Base64.getEncoder().encodeToString(
                    writeSshBytes(type, rsaPubKey.publicExponent.toByteArray(), rsaPubKey.modulus.toByteArray()),
                )
                "$type $encoded"
            }

            "EC", "ECDSA", "1.2.840.10045.2.1" -> {
                val ecPublicKey = publicKey as? java.security.interfaces.ECPublicKey
                    ?: throw IllegalArgumentException("Expected ECPublicKey")
                val keyType = net.schmizz.sshj.common.KeyType.fromKey(ecPublicKey)
                val buffer = net.schmizz.sshj.common.Buffer.PlainBuffer()
                buffer.putPublicKey(ecPublicKey)
                val encoded = Base64.getEncoder().encodeToString(buffer.getCompactData())
                "$keyType $encoded"
            }

            else -> throw IllegalArgumentException("Unsupported algorithm: ${publicKey.algorithm}")
        }
    }

    private fun writeSshBytes(vararg parts: Any): ByteArray {
        val buffers = parts.map { part ->
            when (part) {
                is String -> {
                    val bytes = part.toByteArray(Charsets.UTF_8)
                    ByteBuffer.allocate(4 + bytes.size).putInt(bytes.size).put(bytes).array()
                }

                is ByteArray -> {
                    // For big integers, we might need to handle the sign bit for OpenSSH
                    val bytes = if (part.isNotEmpty() && part[0].toInt() and 0x80 != 0) {
                        byteArrayOf(0) + part
                    } else {
                        part
                    }
                    ByteBuffer.allocate(4 + bytes.size).putInt(bytes.size).put(bytes).array()
                }

                else -> throw IllegalArgumentException("Unsupported part type")
            }
        }
        val totalSize = buffers.sumOf { it.size }
        val finalBuffer = ByteBuffer.allocate(totalSize)
        buffers.forEach { finalBuffer.put(it) }
        return finalBuffer.array()
    }

    /**
     * Encodes a private key to PKCS8 format.
     * Note: The caller is responsible for securing these bytes (e.g., using IdentityStorageManager).
     */
    fun encodePrivateKey(keyPair: KeyPair): ByteArray = keyPair.private.encoded
}
