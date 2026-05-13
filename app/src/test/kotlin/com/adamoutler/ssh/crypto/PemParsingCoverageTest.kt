package com.adamoutler.ssh.crypto

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.interfaces.RSAPrivateKey

class PemParsingCoverageTest {

    @Test
    fun testParseInvalidPem() {
        val invalidPem = "-----BEGIN RSA PRIVATE KEY-----\nINVALID\n-----END RSA PRIVATE KEY-----"
        var exceptionThrown = false
        try {
            PemUtils.parsePemToKeyPair(invalidPem.toByteArray())
        } catch (e: Exception) {
            exceptionThrown = true
        }
        assertTrue(exceptionThrown)
    }

    @Test
    fun testParseEmptyPem() {
        val emptyPem = ""
        var exceptionThrown = false
        try {
            PemUtils.parsePemToKeyPair(emptyPem.toByteArray())
        } catch (e: Exception) {
            exceptionThrown = true
        }
        assertTrue(exceptionThrown)
    }

    @Test
    fun testParseEd25519Invalid() {
        val invalidEd25519 = "-----BEGIN OPENSSH PRIVATE KEY-----\nINVALID\n-----END OPENSSH PRIVATE KEY-----"
        var exceptionThrown = false
        try {
            PemUtils.parsePemToKeyPair(invalidEd25519.toByteArray())
        } catch (e: Exception) {
            exceptionThrown = true
        }
        assertTrue(exceptionThrown)
    }
}
