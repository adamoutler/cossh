package com.adamoutler.ssh.crypto

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
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
        // F4 is the standard public exponent (65537)
        val spec = RSAKeyGenParameterSpec(4096, RSAKeyGenParameterSpec.F4)
        val secureRandom = SecureRandom()
        
        keyPairGenerator.initialize(spec, secureRandom)
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Converts a PublicKey to a Base64 string for OpenSSH compatibility.
     * Note: This is a simplified representation. A true OpenSSH format export
     * typically involves specific byte framing, but for this milestone we verify
     * the base java.security keys can be serialized.
     */
    fun encodePublicKey(keyPair: KeyPair): String {
        return Base64.getEncoder().encodeToString(keyPair.public.encoded)
    }
}
