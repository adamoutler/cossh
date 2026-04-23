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
        val keyPairGenerator = KeyPairGenerator.getInstance("Ed25519")
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
     * Encodes a public key into OpenSSH format (e.g., "ssh-ed25519 AAAA...").
     */
    fun encodePublicKey(keyPair: KeyPair): String {
        val publicKey = keyPair.public
        return when (publicKey.algorithm) {
            "Ed25519" -> {
                val keyBytes = publicKey.encoded
                // Java Ed25519 encoded format is DER. We need the raw 32 bytes at the end.
                // For simplicity in this implementation, we use a standard way to get the raw bytes
                // if it were a direct byte array, but here we'll do a basic slice.
                // A more robust implementation would use BouncyCastle's SubjectPublicKeyInfo.
                val rawBytes = keyBytes.sliceArray(keyBytes.size - 32 until keyBytes.size)
                val type = "ssh-ed25519"
                val encoded = Base64.getEncoder().encodeToString(writeSshBytes(type, rawBytes))
                "$type $encoded"
            }
            "RSA" -> {
                val rsaPubKey = publicKey as RSAPublicKey
                val type = "ssh-rsa"
                val encoded = Base64.getEncoder().encodeToString(
                    writeSshBytes(type, rsaPubKey.publicExponent.toByteArray(), rsaPubKey.modulus.toByteArray())
                )
                "$type $encoded"
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
     * Encodes a private key to PKCS8 format and encrypts it using PasswordCipher.
     */
    fun encodePrivateKey(keyPair: KeyPair): ByteArray {
        val rawBytes = keyPair.private.encoded
        val encryptedBytes = PasswordCipher.encrypt(rawBytes)
        rawBytes.fill(0) // Attempt to scrub volatile memory
        return encryptedBytes
    }
}
