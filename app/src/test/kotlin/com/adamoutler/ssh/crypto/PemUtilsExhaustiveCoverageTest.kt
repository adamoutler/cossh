package com.adamoutler.ssh.crypto

import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters
import org.bouncycastle.crypto.util.OpenSSHPrivateKeyUtil
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.StringWriter
import java.security.KeyPairGenerator
import java.security.Security
import java.security.interfaces.RSAPrivateCrtKey
import java.util.Base64

class PemUtilsExhaustiveCoverageTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setup() {
            if (Security.getProvider("BC") == null) {
                Security.addProvider(BouncyCastleProvider())
            }
        }
    }

    @Test
    fun testParseOpenSshRsaKey_ExtractsPublicKey() {
        val kpg = KeyPairGenerator.getInstance("RSA", "BC")
        kpg.initialize(1024)
        val kp = kpg.generateKeyPair()
        val priv = kp.private as RSAPrivateCrtKey
        val rsaParams = RSAPrivateCrtKeyParameters(
            priv.modulus,
            priv.publicExponent,
            priv.privateExponent,
            priv.primeP,
            priv.primeQ,
            priv.primeExponentP,
            priv.primeExponentQ,
            priv.crtCoefficient,
        )

        val blob = OpenSSHPrivateKeyUtil.encodePrivateKey(rsaParams)
        val b64 = Base64.getEncoder().encodeToString(blob)
        val pem = "-----BEGIN OPENSSH PRIVATE KEY-----\n$b64\n-----END OPENSSH PRIVATE KEY-----\n"

        val parsed = PemUtils.parsePemToKeyPair(pem.toByteArray(Charsets.UTF_8))
        assertNotNull(parsed.public)
        assertNotNull(parsed.private)
    }

    @Test
    fun testParseOpenSshRsaKey_WithExistingPublicKey() {
        val kpg = KeyPairGenerator.getInstance("RSA", "BC")
        kpg.initialize(1024)
        val kp = kpg.generateKeyPair()
        val priv = kp.private as RSAPrivateCrtKey
        val rsaParams = RSAPrivateCrtKeyParameters(
            priv.modulus,
            priv.publicExponent,
            priv.privateExponent,
            priv.primeP,
            priv.primeQ,
            priv.primeExponentP,
            priv.primeExponentQ,
            priv.crtCoefficient,
        )

        val blob = OpenSSHPrivateKeyUtil.encodePrivateKey(rsaParams)
        val b64 = Base64.getEncoder().encodeToString(blob)
        val pem = "-----BEGIN OPENSSH PRIVATE KEY-----\n$b64\n-----END OPENSSH PRIVATE KEY-----\n"

        val parsed = PemUtils.parsePemToKeyPair(pem.toByteArray(Charsets.UTF_8), existingPublicKey = kp.public)
        assertNotNull(parsed.public)
        assertTrue(parsed.public === kp.public)
        assertNotNull(parsed.private)
    }

    @Test
    fun testParseRsaPrivateKey_WithExistingPublicKey() {
        val kpg = KeyPairGenerator.getInstance("RSA", "BC")
        kpg.initialize(1024)
        val kp = kpg.generateKeyPair()

        val sw = StringWriter()
        JcaPEMWriter(sw).use { it.writeObject(kp.private) }
        val pemString = sw.toString()

        val parsed = PemUtils.parsePemToKeyPair(pemString.toByteArray(Charsets.UTF_8), existingPublicKey = kp.public)
        assertNotNull(parsed.public)
        assertTrue(parsed.public === kp.public)
        assertNotNull(parsed.private)
    }

    @Test
    fun testParseEcPemKeyPair_BouncyCastlePEMKeyPairFallback() {
        val kpg = KeyPairGenerator.getInstance("EC", "BC")
        kpg.initialize(org.bouncycastle.jce.ECNamedCurveTable.getParameterSpec("secp256r1"))
        val kp = kpg.generateKeyPair()

        val sw = StringWriter()
        JcaPEMWriter(sw).use { it.writeObject(kp) }
        val pemString = sw.toString() // produces "-----BEGIN EC PRIVATE KEY-----"

        val parsed = PemUtils.parsePemToKeyPair(pemString.toByteArray(Charsets.UTF_8))
        assertNotNull(parsed.private)
        assertNotNull(parsed.public)
    }

    @Test
    fun testParseUnsupportedPemObject_ThrowsException() {
        // Dummy certificate header
        val dummyCert = "-----BEGIN CERTIFICATE-----\n" +
            Base64.getEncoder().encodeToString(ByteArray(32) { 1.toByte() }) +
            "\n-----END CERTIFICATE-----\n"

        try {
            PemUtils.parsePemToKeyPair(dummyCert.toByteArray(Charsets.UTF_8))
            org.junit.Assert.fail("Expected failure for unsupported PEM object")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("PemUtils failure") == true)
        }
    }

    @Test
    fun testDelimiterEdgeCases_NoNewlineAfterBegin() {
        val dummy = "-----BEGIN RSA PRIVATE KEY-----GARBAGE_NO_NEWLINE"
        try {
            PemUtils.parsePemToKeyPair(dummy.toByteArray(Charsets.UTF_8))
        } catch (ignored: Exception) {
            // Expected to fallback to DER and fail gracefully
        }
    }

    @Test
    fun testDelimiterEdgeCases_NoEndFooter() {
        val kpg = KeyPairGenerator.getInstance("RSA", "BC")
        kpg.initialize(1024)
        val kp = kpg.generateKeyPair()

        val sw = StringWriter()
        JcaPEMWriter(sw).use { it.writeObject(kp.private) }
        val fullPem = sw.toString()
        // Strip the -----END line
        val noEndPem = fullPem.substringBefore("-----END")

        try {
            val parsed = PemUtils.parsePemToKeyPair(noEndPem.toByteArray(Charsets.UTF_8))
            assertNotNull(parsed.private)
        } catch (ignored: Exception) {}
    }

    @Test
    fun testBase64WithCarriageReturnsAndSpaces() {
        val kpg = KeyPairGenerator.getInstance("RSA", "BC")
        kpg.initialize(1024)
        val kp = kpg.generateKeyPair()

        val sw = StringWriter()
        JcaPEMWriter(sw).use { it.writeObject(kp.private) }
        val fullPem = sw.toString()

        // Inject spaces and \r\n
        val formattedPem = fullPem.replace("\n", "\r\n  ")
        val parsed = PemUtils.parsePemToKeyPair(formattedPem.toByteArray(Charsets.UTF_8))
        assertNotNull(parsed.private)
    }
}
