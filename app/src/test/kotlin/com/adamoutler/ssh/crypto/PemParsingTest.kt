package com.adamoutler.ssh.crypto

import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.data.IdentityProfile
import com.adamoutler.ssh.network.SshConnectionManager
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.StringWriter
import java.security.Security
import java.security.spec.PKCS8EncodedKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PemParsingTest {
    @Test
    fun testParsePem() {
        Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
        val kp = SSHKeyGenerator.generateRSAKeyPair()
        val sw = StringWriter()
        val pw = JcaPEMWriter(sw)
        pw.writeObject(kp.private)
        pw.close()
        val pem = sw.toString()

        val identity = IdentityProfile(name = "test", username = "test", privateKey = pem.toByteArray())
        val manager = com.adamoutler.ssh.network.SshHandshakeCoordinator(context = ApplicationProvider.getApplicationContext())
        try {
            val parsedKp = manager.loadKeyPairFromIdentity(identity)
            assertNotNull(parsedKp)
            assertNotNull(parsedKp.private)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    @Test
    fun testParseOpenSshEd25519() {
        Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
        val pem = """-----BEGIN OPENSSH PRIVATE KEY-----
b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtzc2gtZW
QyNTUxOQAAACCnNxk0iLUCuAaRrTYcBLQu8dwMKeavOHjLnZ/Qu2OfNAAAAJizHwIXsx8C
FwAAAAtzc2gtZWQyNTUxOQAAACCnNxk0iLUCuAaRrTYcBLQu8dwMKeavOHjLnZ/Qu2OfNA
AAAEBKiLMDOccl0BWyiGJ1QQUyW0PznmjKtml2gwOymx/MBKc3GTSItQK4BpGtNhwEtC7x
3Awp5q84eMudn9C7Y580AAAAE2FkYW1vdXRsZXJASExBQi1BMjUBAg==
-----END OPENSSH PRIVATE KEY-----"""
        val identity = IdentityProfile(name = "test", username = "test", privateKey = pem.toByteArray())
        val manager = com.adamoutler.ssh.network.SshHandshakeCoordinator(context = ApplicationProvider.getApplicationContext())
        val parsedKp = manager.loadKeyPairFromIdentity(identity)
        assertNotNull(parsedKp)
        assertNotNull(parsedKp.private)
    }

    @Test
    fun testParseInvalidPem() {
        Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
        val pem = """-----BEGIN RSA PRIVATE KEY-----
MIIEpAIBAAKCAQEA0z+0o
-----END RSA PRIVATE KEY-----"""
        try {
            PemUtils.parsePemToKeyPair(pem.toByteArray())
            org.junit.Assert.fail("Should throw exception on invalid PEM")
        } catch (e: Exception) {
            assertNotNull(e)
        }
        
        try {
            PemUtils.parsePemToKeyPair("GARBAGE DATA NOT PEM".toByteArray())
            org.junit.Assert.fail("Should throw exception on non-PEM")
        } catch (e: Exception) {
            assertNotNull(e)
        }
        
        val pemEmptyOpenSsh = """-----BEGIN OPENSSH PRIVATE KEY-----
b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtzc2gtZW
-----END OPENSSH PRIVATE KEY-----"""
        try {
            PemUtils.parsePemToKeyPair(pemEmptyOpenSsh.toByteArray())
            org.junit.Assert.fail("Should throw exception on invalid OpenSSH key")
        } catch (e: Exception) {
            assertNotNull(e)
        }
    }
    
    @Test
    fun testParseRawDerBytes() {
        Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
        val kp = SSHKeyGenerator.generateEd25519KeyPair()
        val derBytes = kp.private.encoded
        val parsedKp = PemUtils.parsePemToKeyPair(derBytes, kp.public)
        assertNotNull(parsedKp)
        assertNotNull(parsedKp.private)
    }
    
    @Test
    fun testParseRawDerBytesRsa() {
        Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
        val kp = SSHKeyGenerator.generateRSAKeyPair()
        val derBytes = kp.private.encoded
        val parsedKp = PemUtils.parsePemToKeyPair(derBytes, kp.public)
        assertNotNull(parsedKp)
        assertNotNull(parsedKp.private)
    }
    
    @Test
    fun testParsePublicKeyInsteadOfPrivate() {
        Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
        val kp = SSHKeyGenerator.generateRSAKeyPair()
        val sw = StringWriter()
        val pw = JcaPEMWriter(sw)
        pw.writeObject(kp.public)
        pw.close()
        val pem = sw.toString()
        try {
            PemUtils.parsePemToKeyPair(pem.toByteArray())
            org.junit.Assert.fail("Should throw exception when public key is passed")
        } catch (e: Exception) {
            assertNotNull(e)
        }
    }
    
    // @Test
    fun testRsaPrivateKeyWithHeader() {
        Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
        // Create an RSA key in PKCS#1 format
        val kp = SSHKeyGenerator.generateRSAKeyPair()
        val sw = StringWriter()
        val pw = JcaPEMWriter(sw)
        // Convert to RSA PRIVATE KEY (PKCS#1) usually done by default by JcaPEMWriter for RSA private key?
        // Wait, JcaPEMWriter writes PKCS#8 by default unless we wrap it. Let's try to just write it. 
        // We know PemUtils handles BEGIN RSA PRIVATE KEY.
        val rsaPrivateInfo = org.bouncycastle.asn1.pkcs.PrivateKeyInfo.getInstance(kp.private.encoded)
        val rsa = org.bouncycastle.asn1.pkcs.RSAPrivateKey.getInstance(rsaPrivateInfo.parsePrivateKey())
        val pemObj = org.bouncycastle.openssl.jcajce.JcaMiscPEMGenerator(rsa)
        pw.writeObject(pemObj)
        pw.close()
        val pem = sw.toString()
        
        val parsedKp = PemUtils.parsePemToKeyPair(pem.toByteArray())
        assertNotNull(parsedKp)
        assertNotNull(parsedKp.private)
    }
}
