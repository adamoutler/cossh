package com.adamoutler.ssh.crypto

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SSHKeyGeneratorEncodingTest {

    @Test
    fun testGenerateAndEncodeEd25519() {
        val keyPair = SSHKeyGenerator.generateEd25519KeyPair()
        val publicKeyString = SSHKeyGenerator.encodePublicKey(keyPair)
        
        assertNotNull(publicKeyString)
        assertTrue("Should start with ssh-ed25519", publicKeyString.startsWith("ssh-ed25519"))
        
        val privateKeyBytes = SSHKeyGenerator.encodePrivateKey(keyPair)
        assertNotNull(privateKeyBytes)
        assertTrue("PKCS8 private key should not be empty", privateKeyBytes.isNotEmpty())
    }

    @Test
    fun testGenerateAndEncodeRSA() {
        val keyPair = SSHKeyGenerator.generateRSAKeyPair()
        val publicKeyString = SSHKeyGenerator.encodePublicKey(keyPair)
        
        assertNotNull(publicKeyString)
        assertTrue("Should start with ssh-rsa", publicKeyString.startsWith("ssh-rsa"))
        
        val privateKeyBytes = SSHKeyGenerator.encodePrivateKey(keyPair)
        assertNotNull(privateKeyBytes)
        assertTrue("PKCS8 private key should not be empty", privateKeyBytes.isNotEmpty())
    }
}
