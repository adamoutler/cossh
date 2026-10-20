package com.adamoutler.ssh.crypto

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SSHKeyGeneratorTest {

    @Test
    fun testGenerateEd25519KeyPair() {
        val keyPair = SSHKeyGenerator.generateEd25519KeyPair()
        assertNotNull(keyPair)
        assertNotNull(keyPair.private)
        assertNotNull(keyPair.public)

        // Ensure algorithm matches
        val alg = keyPair.public.algorithm
        assertTrue(alg == "Ed25519" || alg == "EdDSA" || alg == "1.3.101.112")
    }

    @Test
    fun testGenerateRSAKeyPair() {
        val keyPair = SSHKeyGenerator.generateRSAKeyPair()
        assertNotNull(keyPair)
        assertNotNull(keyPair.private)
        assertNotNull(keyPair.public)

        // Ensure algorithm matches
        assertEquals("RSA", keyPair.public.algorithm)

        // Verify key serialization to Base64
        val encodedPub = SSHKeyGenerator.encodePublicKey(keyPair)
        assertTrue(encodedPub.isNotEmpty())
    }

    private fun assertEquals(expected: String, actual: String) {
        if (expected != actual) {
            throw AssertionError("Expected $expected but got $actual")
        }
    }
}
