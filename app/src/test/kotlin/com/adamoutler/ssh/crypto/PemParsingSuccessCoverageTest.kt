package com.adamoutler.ssh.crypto

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.StringWriter
import java.security.KeyPairGenerator
import java.security.Security

class PemParsingSuccessCoverageTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setup() {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    @Test
    fun testParseValidRsa() {
        val kpg = KeyPairGenerator.getInstance("RSA", "BC")
        kpg.initialize(1024)
        val kp = kpg.generateKeyPair()

        val sw = StringWriter()
        val pw = JcaPEMWriter(sw)
        pw.writeObject(kp)
        pw.close()
        
        val pemString = sw.toString()
        // It's probably a RSA PRIVATE KEY or just PRIVATE KEY. Let's see if it parses.
        val parsed = PemUtils.parsePemToKeyPair(pemString.toByteArray())
        assertNotNull(parsed.private)
    }

    @Test
    fun testParseValidEd25519() {
        val kpg = KeyPairGenerator.getInstance("Ed25519", "BC")
        val kp = kpg.generateKeyPair()

        val sw = StringWriter()
        val pw = JcaPEMWriter(sw)
        pw.writeObject(kp)
        pw.close()
        
        val pemString = sw.toString()
        val parsed = PemUtils.parsePemToKeyPair(pemString.toByteArray())
        assertNotNull(parsed.private)
    }

    @Test
    fun testSubjectPublicKeyInfo_Throws() {
        val kpg = KeyPairGenerator.getInstance("Ed25519", "BC")
        val kp = kpg.generateKeyPair()

        val sw = StringWriter()
        val pw = JcaPEMWriter(sw)
        pw.writeObject(kp.public)
        pw.close()

        val pemString = sw.toString()
        var thrown = false
        try {
            PemUtils.parsePemToKeyPair(pemString.toByteArray())
        } catch (e: Exception) {
            thrown = true
        }
        assertTrue(thrown)
    }
}
