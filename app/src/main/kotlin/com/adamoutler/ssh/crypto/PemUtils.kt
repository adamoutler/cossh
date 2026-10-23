package com.adamoutler.ssh.crypto

import net.schmizz.sshj.userauth.keyprovider.OpenSSHKeyFile
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.pkcs.RSAPrivateKey
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.params.ECPublicKeyParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.RSAKeyParameters
import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters
import org.bouncycastle.crypto.util.OpenSSHPrivateKeyUtil
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory
import org.bouncycastle.openssl.PEMEncryptedKeyPair
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo
import org.bouncycastle.pkcs.jcajce.JcePKCSPBEInputDecryptorProviderBuilder
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.security.KeyFactory
import java.security.KeyPair
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

class KeyIsEncryptedException(val cipherSummary: String) :
    Exception("Key is encrypted ($cipherSummary), passphrase required")

class InvalidPassphraseException(message: String = "Incorrect passphrase for encrypted private key") :
    Exception(message)

object PemUtils {

    /**
     * Determines whether the provided PEM or raw key bytes are encrypted and require a passphrase.
     */
    fun isKeyEncrypted(pemBytes: ByteArray): Boolean = detectKeyEncryptionSummary(pemBytes) != null

    /**
     * Returns a human-readable summary of the key encryption format, or null if unencrypted / unknown.
     */
    fun detectKeyEncryptionSummary(pemBytes: ByteArray): String? {
        val snippetLen = minOf(pemBytes.size, 4096)
        val snippet = String(pemBytes, 0, snippetLen, Charsets.US_ASCII)

        if (snippet.contains("ENCRYPTED PRIVATE KEY")) {
            return "PKCS#8 Encrypted"
        }
        if (snippet.contains("Proc-Type: 4,ENCRYPTED") || snippet.contains("DEK-Info:")) {
            return "OpenSSL Encrypted PEM"
        }
        if (snippet.contains("OPENSSH PRIVATE KEY")) {
            val rawBlob = extractBase64Blob(pemBytes)
            if (rawBlob != null) {
                val cipher = getOpenSshCipher(rawBlob)
                rawBlob.fill(0)
                if (cipher != null && cipher != "none") {
                    return "OpenSSH ($cipher)"
                }
            }
        }
        return null
    }

    /**
     * Parses various PEM byte arrays (OpenSSH, PKCS#1, PKCS#8) directly into a KeyPair
     * without leaking sensitive plaintext into immutable Strings, preserving Volatile State Sanitization.
     */
    fun parsePemToKeyPair(
        pemBytes: ByteArray,
        existingPublicKey: PublicKey? = null,
        passphrase: CharArray? = null,
    ): KeyPair {
        val encSummary = detectKeyEncryptionSummary(pemBytes)
        if (encSummary != null && (passphrase == null || passphrase.isEmpty())) {
            throw KeyIsEncryptedException(encSummary)
        }

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

        // Find the end of the header line
        var headerEnd = -1
        for (i in startIndex until pemBytes.size) {
            if (pemBytes[i] == '\n'.code.toByte()) {
                headerEnd = i
                break
            }
        }
        if (headerEnd == -1) return generateKeyPairFromDer(pemBytes, existingPublicKey)

        val headerBytes = ByteArray(headerEnd - startIndex)
        System.arraycopy(pemBytes, startIndex, headerBytes, 0, headerBytes.size)
        val headerStr = String(headerBytes, Charsets.UTF_8)

        val fullHeaderLen = minOf(pemBytes.size, 512)
        val fullHeaderStr = String(pemBytes, 0, fullHeaderLen, Charsets.US_ASCII)

        // Handle OpenSSH format
        if (headerStr.contains("OPENSSH PRIVATE KEY")) {
            val rawKeyBlob = extractBase64Blob(pemBytes)
                ?: throw RuntimeException("Failed to extract OpenSSH base64 payload")
            try {
                val cipher = getOpenSshCipher(rawKeyBlob)
                if (cipher != null && cipher != "none") {
                    if (passphrase == null || passphrase.isEmpty()) {
                        throw KeyIsEncryptedException("OpenSSH ($cipher)")
                    }
                    return parseEncryptedOpenSshKey(pemBytes, passphrase, existingPublicKey)
                }

                // Unencrypted OpenSSH key
                val converter = JcaPEMKeyConverter().setProvider("BC")
                val keyParameter = OpenSSHPrivateKeyUtil.parsePrivateKeyBlob(rawKeyBlob)
                val privateKeyInfo = PrivateKeyInfoFactory.createPrivateKeyInfo(keyParameter)
                val privKey = converter.getPrivateKey(privateKeyInfo)

                var pubKey = existingPublicKey
                if (pubKey == null) {
                    try {
                        pubKey = derivePublicKey(keyParameter, converter)
                    } catch (_: Exception) {}
                }
                return KeyPair(pubKey, privKey)
            } finally {
                rawKeyBlob.fill(0)
            }
        }

        // Handle traditional PKCS#1 RSA format
        if (headerStr.contains("RSA PRIVATE KEY") && !fullHeaderStr.contains("ENCRYPTED") && !fullHeaderStr.contains("DEK-Info")) {
            val rawKeyBlob = extractBase64Blob(pemBytes)
                ?: throw RuntimeException("Failed to extract RSA base64 payload")
            try {
                val converter = JcaPEMKeyConverter().setProvider("BC")
                val rsaPrivateKey = RSAPrivateKey.getInstance(rawKeyBlob)
                val privateKeyInfo = PrivateKeyInfo(
                    AlgorithmIdentifier(PKCSObjectIdentifiers.rsaEncryption, org.bouncycastle.asn1.DERNull.INSTANCE),
                    rsaPrivateKey,
                )
                val privKey = converter.getPrivateKey(privateKeyInfo)

                var pubKey = existingPublicKey
                if (pubKey == null) {
                    try {
                        val rsaCrt = RSAPrivateCrtKeyParameters(
                            rsaPrivateKey.modulus,
                            rsaPrivateKey.publicExponent,
                            rsaPrivateKey.privateExponent,
                            rsaPrivateKey.prime1,
                            rsaPrivateKey.prime2,
                            rsaPrivateKey.exponent1,
                            rsaPrivateKey.exponent2,
                            rsaPrivateKey.coefficient,
                        )
                        val pubParams = RSAKeyParameters(false, rsaCrt.modulus, rsaCrt.publicExponent)
                        val pubInfo = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(pubParams)
                        pubKey = converter.getPublicKey(pubInfo)
                    } catch (_: Exception) {}
                }
                return KeyPair(pubKey, privKey)
            } finally {
                rawKeyBlob.fill(0)
            }
        }

        // Handle PKCS#8 or encrypted PEM formats using BouncyCastle PEMParser
        val inputStream = ByteArrayInputStream(pemBytes)
        val reader = InputStreamReader(inputStream, Charsets.UTF_8)
        val parser = PEMParser(reader)

        val converter = JcaPEMKeyConverter().setProvider("BC")
        try {
            val obj = parser.readObject()
            return when (obj) {
                is PKCS8EncryptedPrivateKeyInfo -> {
                    if (passphrase == null || passphrase.isEmpty()) {
                        throw KeyIsEncryptedException("PKCS#8 Encrypted")
                    }
                    try {
                        val pbeProvider = JcePKCSPBEInputDecryptorProviderBuilder()
                            .setProvider("BC")
                            .build(passphrase)
                        val pki = obj.decryptPrivateKeyInfo(pbeProvider)
                        val privKey = converter.getPrivateKey(pki)
                        val pubKey = existingPublicKey ?: try {
                            val keyParam = org.bouncycastle.crypto.util.PrivateKeyFactory.createKey(pki)
                            derivePublicKey(keyParam, converter)
                        } catch (_: Exception) {
                            null
                        }
                        KeyPair(pubKey, privKey)
                    } catch (e: Exception) {
                        if (e is KeyIsEncryptedException) throw e
                        throw InvalidPassphraseException("Failed to decrypt PKCS#8 key: ${e.message}")
                    }
                }

                is PEMEncryptedKeyPair -> {
                    if (passphrase == null || passphrase.isEmpty()) {
                        throw KeyIsEncryptedException("OpenSSL Encrypted PEM")
                    }
                    try {
                        val pbeProvider = JcePEMDecryptorProviderBuilder()
                            .setProvider("BC")
                            .build(passphrase)
                        val decrypted = obj.decryptKeyPair(pbeProvider)
                        val privKey = converter.getPrivateKey(decrypted.privateKeyInfo)
                        val pubKey = existingPublicKey ?: try {
                            converter.getPublicKey(decrypted.publicKeyInfo)
                        } catch (_: Exception) {
                            null
                        }
                        KeyPair(pubKey, privKey)
                    } catch (e: Exception) {
                        if (e is KeyIsEncryptedException) throw e
                        throw InvalidPassphraseException("Failed to decrypt PEM key: ${e.message}")
                    }
                }

                is PEMKeyPair -> {
                    val pubKey = existingPublicKey ?: converter.getPublicKey(obj.publicKeyInfo)
                    val privKey = converter.getPrivateKey(obj.privateKeyInfo)
                    KeyPair(pubKey, privKey)
                }

                is PrivateKeyInfo -> {
                    val privKey = converter.getPrivateKey(obj)
                    val pubKey = existingPublicKey ?: try {
                        val keyParam = org.bouncycastle.crypto.util.PrivateKeyFactory.createKey(obj)
                        derivePublicKey(keyParam, converter)
                    } catch (_: Exception) {
                        null
                    }
                    KeyPair(pubKey, privKey)
                }

                else -> throw IllegalArgumentException("Unsupported PEM object: ${obj?.javaClass?.name}")
            }
        } catch (e: Exception) {
            if (e is KeyIsEncryptedException || e is InvalidPassphraseException) throw e
            throw RuntimeException("PemUtils failure", e)
        }
    }

    private fun parseEncryptedOpenSshKey(
        pemBytes: ByteArray,
        passphrase: CharArray,
        existingPublicKey: PublicKey?,
    ): KeyPair {
        val keyFile = OpenSSHKeyFile()
        val reader = InputStreamReader(ByteArrayInputStream(pemBytes), Charsets.UTF_8)
        val pwFinder = object : net.schmizz.sshj.userauth.password.PasswordFinder {
            override fun reqPassword(resource: net.schmizz.sshj.userauth.password.Resource<*>?): CharArray = passphrase
            override fun shouldRetry(resource: net.schmizz.sshj.userauth.password.Resource<*>?): Boolean = false
        }
        try {
            keyFile.init(reader, null as java.io.Reader?, pwFinder)
            val privKey = keyFile.getPrivate()
            val pubKey = existingPublicKey ?: try {
                keyFile.getPublic()
            } catch (_: Exception) {
                null
            }
            return KeyPair(pubKey, privKey)
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("passphrase", ignoreCase = true) ||
                msg.contains("mac", ignoreCase = true) ||
                msg.contains("tag", ignoreCase = true) ||
                msg.contains("checksum", ignoreCase = true) ||
                msg.contains("decrypt", ignoreCase = true) ||
                e is java.io.IOException
            ) {
                throw InvalidPassphraseException()
            }
            throw RuntimeException("Failed to parse encrypted OpenSSH key: ${e.message}", e)
        }
    }

    private fun derivePublicKey(
        keyParameter: org.bouncycastle.crypto.params.AsymmetricKeyParameter,
        converter: JcaPEMKeyConverter,
    ): PublicKey? = when (keyParameter) {
        is Ed25519PrivateKeyParameters -> {
            val pubParams = keyParameter.generatePublicKey()
            val pubInfo = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(pubParams)
            converter.getPublicKey(pubInfo)
        }

        is RSAPrivateCrtKeyParameters -> {
            val pubParams = RSAKeyParameters(false, keyParameter.modulus, keyParameter.publicExponent)
            val pubInfo = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(pubParams)
            converter.getPublicKey(pubInfo)
        }

        is ECPrivateKeyParameters -> {
            val q = keyParameter.parameters.g.multiply(keyParameter.d)
            val pubParams = ECPublicKeyParameters(q, keyParameter.parameters)
            val pubInfo = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(pubParams)
            converter.getPublicKey(pubInfo)
        }

        else -> null
    }

    private fun extractBase64Blob(pemBytes: ByteArray): ByteArray? {
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
        if (startIndex == -1) return null

        var headerEnd = -1
        for (i in startIndex until pemBytes.size) {
            if (pemBytes[i] == '\n'.code.toByte()) {
                headerEnd = i
                break
            }
        }
        if (headerEnd == -1) return null

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

        val base64Bytes = ByteArray(footerStart - headerEnd)
        var count = 0
        for (i in headerEnd until footerStart) {
            val b = pemBytes[i]
            if (b != '\n'.code.toByte() && b != '\r'.code.toByte() && b != ' '.code.toByte()) {
                base64Bytes[count++] = b
            }
        }

        val cleanBase64Bytes = base64Bytes.copyOfRange(0, count)
        val decoded = try {
            Base64.getMimeDecoder().decode(cleanBase64Bytes)
        } catch (_: Exception) {
            null
        }
        base64Bytes.fill(0)
        cleanBase64Bytes.fill(0)
        return decoded
    }

    private fun getOpenSshCipher(rawBlob: ByteArray): String? {
        val magic = "openssh-key-v1\u0000".toByteArray(Charsets.US_ASCII)
        if (rawBlob.size < magic.size + 4) return null
        for (i in magic.indices) {
            if (rawBlob[i] != magic[i]) return null
        }
        var offset = magic.size
        val cipherLen = ((rawBlob[offset].toInt() and 0xFF) shl 24) or
            ((rawBlob[offset + 1].toInt() and 0xFF) shl 16) or
            ((rawBlob[offset + 2].toInt() and 0xFF) shl 8) or
            (rawBlob[offset + 3].toInt() and 0xFF)
        offset += 4
        if (cipherLen <= 0 || offset + cipherLen > rawBlob.size) return null
        return String(rawBlob, offset, cipherLen, Charsets.US_ASCII)
    }

    private fun generateKeyPairFromDer(derBytes: ByteArray, publicKey: PublicKey?): KeyPair {
        val converter = JcaPEMKeyConverter().setProvider("BC")
        val derivedPub = publicKey ?: try {
            val privInfo = PrivateKeyInfo.getInstance(derBytes)
            val keyParam = org.bouncycastle.crypto.util.PrivateKeyFactory.createKey(privInfo)
            derivePublicKey(keyParam, converter)
        } catch (_: Exception) {
            null
        }

        return try {
            val keyFactory = KeyFactory.getInstance("Ed25519", "BC")
            val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(derBytes))
            KeyPair(derivedPub, privateKey)
        } catch (_: Exception) {
            try {
                val keyFactory = KeyFactory.getInstance("RSA", "BC")
                val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(derBytes))
                KeyPair(derivedPub, privateKey)
            } catch (_: Exception) {
                val keyFactory = KeyFactory.getInstance("EC", "BC")
                val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(derBytes))
                KeyPair(derivedPub, privateKey)
            }
        }
    }
}
