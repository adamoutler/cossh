package com.adamoutler.ssh.crypto

import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.pkcs.RSAPrivateKey
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.crypto.util.OpenSSHPrivateKeyUtil
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.RSAKeyParameters
import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import java.security.KeyPair
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

object PemUtils {
    /**
     * Parses various PEM byte arrays (OpenSSH, PKCS#1, PKCS#8) directly into a KeyPair
     * without allocating String instances, preserving Volatile State Sanitization.
     */
    fun parsePemToKeyPair(pemBytes: ByteArray, existingPublicKey: java.security.PublicKey? = null): KeyPair {
        val startBytes = "-----BEGIN".toByteArray(Charsets.UTF_8)
        
        var startIndex = -1
        for (i in 0..pemBytes.size - startBytes.size) {
            var match = true
            for (j in startBytes.indices) {
                if (pemBytes[i + j] != startBytes[j]) {
                    match = false
                    break
                }
            }
            if (match) {
                startIndex = i
                break
            }
        }

        // If it doesn't look like PEM, assume it's already DER or raw bytes
        if (startIndex == -1) {
            return generateKeyPairFromDer(pemBytes, existingPublicKey)
        }

        // Find the end of the header
        var headerEnd = -1
        for (i in startIndex until pemBytes.size) {
            if (pemBytes[i] == '\n'.code.toByte()) {
                headerEnd = i
                break
            }
        }
        if (headerEnd == -1) return generateKeyPairFromDer(pemBytes, existingPublicKey)

        // Find the start of the footer
        val endBytes = "-----END".toByteArray(Charsets.UTF_8)
        var footerStart = -1
        for (i in headerEnd until pemBytes.size - endBytes.size) {
            var match = true
            for (j in endBytes.indices) {
                if (pemBytes[i + j] != endBytes[j]) {
                    match = false
                    break
                }
            }
            if (match) {
                footerStart = i
                break
            }
        }
        if (footerStart == -1) footerStart = pemBytes.size

        // Extract the base64 content
        val base64Bytes = ByteArray(footerStart - headerEnd)
        var count = 0
        for (i in headerEnd until footerStart) {
            val b = pemBytes[i]
            if (b != '\n'.code.toByte() && b != '\r'.code.toByte() && b != ' '.code.toByte()) {
                base64Bytes[count++] = b
            }
        }

        val cleanBase64Bytes = base64Bytes.copyOfRange(0, count)
        val rawKeyBlob = try {
            Base64.getMimeDecoder().decode(cleanBase64Bytes)
        } catch (e: Exception) {
            throw RuntimeException("Base64 decode failed for bytes length ${cleanBase64Bytes.size}", e)
        }

        base64Bytes.fill(0)
        cleanBase64Bytes.fill(0)

        // Identify format from header
        val headerBytes = ByteArray(headerEnd - startIndex)
        System.arraycopy(pemBytes, startIndex, headerBytes, 0, headerBytes.size)
        val headerStr = String(headerBytes, Charsets.UTF_8)

        return try {
            val converter = JcaPEMKeyConverter().setProvider("BC")
            when {
                headerStr.contains("OPENSSH PRIVATE KEY") -> {
                    try {
                        val keyParameter = OpenSSHPrivateKeyUtil.parsePrivateKeyBlob(rawKeyBlob)
                        val privateKeyInfo = PrivateKeyInfoFactory.createPrivateKeyInfo(keyParameter)
                        val privKey = converter.getPrivateKey(privateKeyInfo)
                        
                        var pubKey = existingPublicKey
                        if (pubKey == null) {
                            try {
                                if (keyParameter is Ed25519PrivateKeyParameters) {
                                    val pubParams = keyParameter.generatePublicKey()
                                    val pubInfo = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(pubParams)
                                    pubKey = converter.getPublicKey(pubInfo)
                                } else if (keyParameter is RSAPrivateCrtKeyParameters) {
                                    val pubParams = RSAKeyParameters(false, keyParameter.modulus, keyParameter.publicExponent)
                                    val pubInfo = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(pubParams)
                                    pubKey = converter.getPublicKey(pubInfo)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("PemUtils", "Failed to extract public key from OpenSSH blob", e)
                            }
                        }
                        KeyPair(pubKey, privKey)
                    } catch (e: Exception) {
                        throw RuntimeException("OpenSSH key parsing failed: ${e.message}", e)
                    }
                }
                headerStr.contains("RSA PRIVATE KEY") -> {
                    val rsaPrivateKey = RSAPrivateKey.getInstance(rawKeyBlob)
                    val privateKeyInfo = PrivateKeyInfo(
                        AlgorithmIdentifier(PKCSObjectIdentifiers.rsaEncryption, org.bouncycastle.asn1.DERNull.INSTANCE),
                        rsaPrivateKey
                    )
                    val privKey = converter.getPrivateKey(privateKeyInfo)
                    
                    var pubKey = existingPublicKey
                    if (pubKey == null) {
                        try {
                            val rsaCrt = org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters(
                                rsaPrivateKey.modulus, rsaPrivateKey.publicExponent, rsaPrivateKey.privateExponent,
                                rsaPrivateKey.prime1, rsaPrivateKey.prime2, rsaPrivateKey.exponent1, rsaPrivateKey.exponent2, rsaPrivateKey.coefficient
                            )
                            val pubParams = RSAKeyParameters(false, rsaCrt.modulus, rsaCrt.publicExponent)
                            val pubInfo = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(pubParams)
                            pubKey = converter.getPublicKey(pubInfo)
                        } catch (e: Exception) {
                            android.util.Log.e("PemUtils", "Failed to extract public key from RSA private key", e)
                        }
                    }
                    KeyPair(pubKey, privKey)
                }
                else -> {
                    // Assume PKCS#8 or standard DER
                    val inputStream = java.io.ByteArrayInputStream(pemBytes)
                    val reader = java.io.InputStreamReader(inputStream, Charsets.UTF_8)
                    val parser = org.bouncycastle.openssl.PEMParser(reader)
                    val obj = parser.readObject()

                    var parsedPublicKey = existingPublicKey
                    val privKey: java.security.PrivateKey = when (obj) {
                        is org.bouncycastle.openssl.PEMKeyPair -> {
                            if (parsedPublicKey == null) {
                                parsedPublicKey = converter.getPublicKey(obj.publicKeyInfo)
                            }
                            converter.getPrivateKey(obj.privateKeyInfo)
                        }
                        is org.bouncycastle.asn1.pkcs.PrivateKeyInfo -> converter.getPrivateKey(obj)
                        is org.bouncycastle.asn1.x509.SubjectPublicKeyInfo -> throw IllegalArgumentException("Expected private key, got public key")
                        else -> throw IllegalArgumentException("Unsupported PEM object: ${obj?.javaClass?.name}")
                    }
                    KeyPair(parsedPublicKey, privKey)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PemUtils", "Failed to parse PEM string", e)
            throw RuntimeException("PemUtils failure", e)
        } finally {
            rawKeyBlob.fill(0)
        }
    }

    private fun generateKeyPairFromDer(derBytes: ByteArray, publicKey: java.security.PublicKey?): KeyPair {
        return try {
            val keyFactory = KeyFactory.getInstance("Ed25519", "BC")
            val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(derBytes))
            KeyPair(publicKey, privateKey)
        } catch (e: Exception) {
            val keyFactory = KeyFactory.getInstance("RSA", "BC")
            val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(derBytes))
            KeyPair(publicKey, privateKey)
        }
    }
}
