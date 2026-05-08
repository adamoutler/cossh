package com.adamoutler.ssh.crypto

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.Security

class PemUtilsTest {

    @Before
    fun setup() {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    @Test
    fun `test parsePemToKeyPair with OpenSSH Ed25519 Key`() {
        val edPair = SSHKeyGenerator.generateEd25519KeyPair()
        assertNotNull("KeyPair should not be null", edPair)
        assertNotNull("PrivateKey should not be null", edPair.private)
        assertNotNull("PublicKey should not be null", edPair.public)
    }

    @Test
    fun `test parsePemToKeyPair with OpenSSH RSA Key`() {
        val rsaPair = SSHKeyGenerator.generateRSAKeyPair()
        assertNotNull(rsaPair)
    }
}
