package com.adamoutler.ssh.crypto

import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfoBuilder
import org.bouncycastle.pkcs.jcajce.JcePKCSPBEOutputEncryptorBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Test
import java.io.StringWriter
import java.security.Security

class PassphraseProtectedKeyCryptoTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun setupBouncyCastle() {
            Security.removeProvider("BC")
            Security.addProvider(BouncyCastleProvider())
        }
    }

    @Test
    fun testEcdsaKeyGenerationAndEncoding_NistP256() {
        val keyPair = SSHKeyGenerator.generateECDSAKeyPair("secp256r1")
        assertNotNull(keyPair.private)
        assertNotNull(keyPair.public)

        val encodedPub = SSHKeyGenerator.encodePublicKey(keyPair)
        assertTrue("Encoded public key should start with ecdsa-sha2-nistp256", encodedPub.startsWith("ecdsa-sha2-nistp256 "))

        val der = SSHKeyGenerator.encodePrivateKey(keyPair)
        val roundtripPair = PemUtils.parsePemToKeyPair(der, keyPair.public)
        assertNotNull(roundtripPair.private)
        assertEquals(keyPair.public, roundtripPair.public)
    }

    @Test
    fun testEcdsaKeyGenerationAndEncoding_NistP384() {
        val keyPair = SSHKeyGenerator.generateECDSAKeyPair("secp384r1")
        assertNotNull(keyPair.private)
        assertNotNull(keyPair.public)

        val encodedPub = SSHKeyGenerator.encodePublicKey(keyPair)
        assertTrue("Encoded public key should start with ecdsa-sha2-nistp384", encodedPub.startsWith("ecdsa-sha2-nistp384 "))
    }

    @Test
    fun testEcdsaKeyGenerationAndEncoding_NistP521() {
        val keyPair = SSHKeyGenerator.generateECDSAKeyPair("secp521r1")
        assertNotNull(keyPair.private)
        assertNotNull(keyPair.public)

        val encodedPub = SSHKeyGenerator.encodePublicKey(keyPair)
        assertTrue("Encoded public key should start with ecdsa-sha2-nistp521", encodedPub.startsWith("ecdsa-sha2-nistp521 "))
    }

    @Test
    fun testEd25519KeyGeneration_AndRoundtripParsing() {
        val keyPair = SSHKeyGenerator.generateEd25519KeyPair()
        assertNotNull(keyPair.private)
        assertNotNull(keyPair.public)

        val pubEncoded = SSHKeyGenerator.encodePublicKey(keyPair)
        assertTrue(pubEncoded.startsWith("ssh-ed25519 "))

        val der = SSHKeyGenerator.encodePrivateKey(keyPair)
        val isEnc = PemUtils.isKeyEncrypted(der)
        assertEquals(false, isEnc)

        val parsed = PemUtils.parsePemToKeyPair(der, keyPair.public)
        assertNotNull(parsed.private)
        assertEquals(keyPair.public, parsed.public)
    }

    @Test
    fun testEncryptedPkcs8_FullDecryptionRoundtripAndPassphraseValidation() {
        val keyPair = SSHKeyGenerator.generateEd25519KeyPair()
        val pki = PrivateKeyInfo.getInstance(keyPair.private.encoded)

        val passphrase = "correct-battery-staple-horse".toCharArray()
        val encryptor = JcePKCSPBEOutputEncryptorBuilder(PKCSObjectIdentifiers.pbeWithSHAAnd3_KeyTripleDES_CBC)
            .setProvider("BC")
            .build(passphrase)
        val encryptedPki = PKCS8EncryptedPrivateKeyInfoBuilder(pki).build(encryptor)

        val sw = StringWriter()
        val pemWriter = JcaPEMWriter(sw)
        pemWriter.writeObject(encryptedPki)
        pemWriter.close()
        val pemBytes = sw.toString().toByteArray(Charsets.UTF_8)

        // 1. Detection
        val isEnc = PemUtils.isKeyEncrypted(pemBytes)
        assertTrue("Key should be detected as encrypted", isEnc)

        val summary = PemUtils.detectKeyEncryptionSummary(pemBytes)
        assertEquals("PKCS#8 Encrypted", summary)

        // 2. Reject missing passphrase
        try {
            PemUtils.parsePemToKeyPair(pemBytes, passphrase = null)
            fail("Expected KeyIsEncryptedException")
        } catch (e: KeyIsEncryptedException) {
            assertEquals("PKCS#8 Encrypted", e.cipherSummary)
        }

        // 3. Reject invalid passphrase
        try {
            PemUtils.parsePemToKeyPair(pemBytes, passphrase = "wrong-password".toCharArray())
            fail("Expected InvalidPassphraseException")
        } catch (_: InvalidPassphraseException) {
            // Expected
        }

        // 4. Successful decryption
        val decrypted = PemUtils.parsePemToKeyPair(pemBytes, existingPublicKey = keyPair.public, passphrase = passphrase)
        assertNotNull(decrypted.private)
        assertEquals(keyPair.public, decrypted.public)
    }

    @Test
    fun testEncryptedLegacyPem_DetectionAndRejectionWithoutPassphrase() {
        val legacyPemEncrypted = """
            -----BEGIN RSA PRIVATE KEY-----
            Proc-Type: 4,ENCRYPTED
            DEK-Info: DES-EDE3-CBC,123456789ABCDEF0

            MIIBOgIBAAJBAK1w==
            -----END RSA PRIVATE KEY-----
        """.trimIndent()

        val pemBytes = legacyPemEncrypted.toByteArray(Charsets.UTF_8)
        val isEnc = PemUtils.isKeyEncrypted(pemBytes)
        assertTrue(isEnc)

        val summary = PemUtils.detectKeyEncryptionSummary(pemBytes)
        assertEquals("OpenSSL Encrypted PEM", summary)

        try {
            PemUtils.parsePemToKeyPair(pemBytes)
            fail("Expected KeyIsEncryptedException")
        } catch (e: KeyIsEncryptedException) {
            assertEquals("OpenSSL Encrypted PEM", e.cipherSummary)
        }
    }
}
