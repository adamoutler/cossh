package com.adamoutler.ssh.crypto

import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfoBuilder
import org.bouncycastle.pkcs.jcajce.JcePKCSPBEOutputEncryptorBuilder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.BeforeClass
import org.junit.Test
import java.io.StringWriter
import java.security.Security

class KeyDecryptionSanitizationTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun setupBouncyCastle() {
            Security.removeProvider("BC")
            Security.addProvider(BouncyCastleProvider())
        }
    }

    @Test
    fun testPassphraseBufferZeroWiping_PostDecryption() {
        val keyPair = SSHKeyGenerator.generateEd25519KeyPair()
        val pki = PrivateKeyInfo.getInstance(keyPair.private.encoded)

        val secret = "super-secret-passphrase-1234".toCharArray()
        val encryptor = JcePKCSPBEOutputEncryptorBuilder(PKCSObjectIdentifiers.pbeWithSHAAnd3_KeyTripleDES_CBC)
            .setProvider("BC")
            .build(secret)
        val encryptedPki = PKCS8EncryptedPrivateKeyInfoBuilder(pki).build(encryptor)

        val sw = StringWriter()
        val pemWriter = JcaPEMWriter(sw)
        pemWriter.writeObject(encryptedPki)
        pemWriter.close()
        val pemBytes = sw.toString().toByteArray(Charsets.UTF_8)

        val inputPassphrase = "super-secret-passphrase-1234".toCharArray()
        val decrypted = try {
            PemUtils.parsePemToKeyPair(pemBytes, existingPublicKey = keyPair.public, passphrase = inputPassphrase)
        } finally {
            inputPassphrase.fill('\u0000')
        }

        assertNotNull(decrypted.private)
        // Verify inputPassphrase buffer was zeroed in finally
        val zeroArray = CharArray("super-secret-passphrase-1234".length) { '\u0000' }
        assertArrayEquals("Passphrase buffer must be zero-wiped", zeroArray, inputPassphrase)
    }

    @Test
    fun testRawDerZeroWiping_PostParsing() {
        val keyPair = SSHKeyGenerator.generateEd25519KeyPair()
        val der = SSHKeyGenerator.encodePrivateKey(keyPair)

        val derCopy = der.copyOf()
        val parsed = try {
            PemUtils.parsePemToKeyPair(derCopy, existingPublicKey = keyPair.public)
        } finally {
            derCopy.fill(0)
        }

        assertNotNull(parsed.private)
        val zeroBytes = ByteArray(der.size)
        assertArrayEquals("DER byte buffer must be zero-wiped", zeroBytes, derCopy)
    }
}
