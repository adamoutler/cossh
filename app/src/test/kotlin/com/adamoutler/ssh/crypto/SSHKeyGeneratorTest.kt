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
        assertTrue(keyPair.public.algorithm == "Ed25519" || keyPair.public.algorithm == "EdDSA")
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
