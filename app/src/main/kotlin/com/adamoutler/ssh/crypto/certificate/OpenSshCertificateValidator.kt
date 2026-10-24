package com.adamoutler.ssh.crypto.certificate

import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.DERSequence
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.util.Base64

/**
 * Validates OpenSSH User and Host Certificates following PROTOCOL.certkeys.
 * Enforces temporal windows, principal matching, the Critical Options trap,
 * and CA cryptographic signature verification across Ed25519, RSA, and ECDSA.
 */
object OpenSshCertificateValidator {

    val RECOGNIZED_CRITICAL_OPTIONS = setOf(
        "force-command",
        "source-address",
        "verify-required",
    )

    /**
     * Complete validation pipeline:
     * 1. Temporal validity
     * 2. Principal check
     * 3. Critical Options trap
     * 4. CA cryptographic signature verification
     */
    fun validate(
        cert: OpenSshCertificate,
        requestedPrincipal: String? = null,
        currentEpochSeconds: Long = System.currentTimeMillis() / 1000L,
        trustedCaKeys: List<PublicKey>? = null,
    ): CertValidationResult {
        // 1. Temporal Validity
        val temporalResult = validateTemporal(cert, currentEpochSeconds)
        if (temporalResult !is CertValidationResult.Valid) {
            return temporalResult
        }

        // 2. Principal Check
        if (requestedPrincipal != null) {
            val principalResult = validatePrincipal(cert, requestedPrincipal)
            if (principalResult !is CertValidationResult.Valid) {
                return principalResult
            }
        }

        // 3. Critical Options Trap (Reject any unknown critical options)
        val critResult = validateCriticalOptions(cert)
        if (critResult !is CertValidationResult.Valid) {
            return critResult
        }

        // 4. Trusted CA Check (if CA trust store provided)
        if (trustedCaKeys != null) {
            val isCaTrusted = trustedCaKeys.any { trustedKey ->
                isSameKey(cert.caPublicKey, trustedKey)
            }
            if (!isCaTrusted) {
                val caFingerprint = computeFingerprint(cert.caPublicKey)
                return CertValidationResult.CaNotTrusted(caFingerprint)
            }
        }

        // 5. CA Signature Verification
        return validateSignature(cert)
    }

    /**
     * Validates temporal validity against the specified epoch seconds.
     */
    fun validateTemporal(
        cert: OpenSshCertificate,
        currentEpochSeconds: Long = System.currentTimeMillis() / 1000L,
    ): CertValidationResult {
        if (currentEpochSeconds < cert.validAfterSeconds) {
            return CertValidationResult.NotYetValid(cert.validAfterSeconds, currentEpochSeconds)
        }

        // Compare as unsigned 64-bit int (0xFFFFFFFFFFFFFFFFL is infinity)
        if (cert.validBeforeSeconds != -1L && java.lang.Long.compareUnsigned(currentEpochSeconds, cert.validBeforeSeconds) > 0) {
            return CertValidationResult.Expired(cert.validBeforeSeconds, currentEpochSeconds)
        }

        return CertValidationResult.Valid(cert)
    }

    /**
     * Validates that requestedPrincipal matches one of the certificate's validPrincipals.
     * Host certificates with an empty principals list are valid for any host.
     */
    fun validatePrincipal(
        cert: OpenSshCertificate,
        requestedPrincipal: String,
    ): CertValidationResult {
        if (cert.certType == OpenSshCertType.HOST && cert.validPrincipals.isEmpty()) {
            return CertValidationResult.Valid(cert)
        }

        val matches = cert.validPrincipals.any { pattern ->
            matchesHostOrPrincipal(pattern, requestedPrincipal)
        }

        return if (matches) {
            CertValidationResult.Valid(cert)
        } else {
            CertValidationResult.InvalidPrincipal(requestedPrincipal, cert.validPrincipals)
        }
    }

    /**
     * Enforces the OpenSSH Critical Options Trap:
     * If an option is present that the client does not recognize, authentication MUST fail.
     */
    fun validateCriticalOptions(
        cert: OpenSshCertificate,
        recognizedOptions: Set<String> = RECOGNIZED_CRITICAL_OPTIONS,
    ): CertValidationResult {
        for ((optName, optValue) in cert.criticalOptions) {
            if (optName !in recognizedOptions) {
                return CertValidationResult.UnsupportedCriticalOption(optName, optValue)
            }
        }
        return CertValidationResult.Valid(cert)
    }

    /**
     * Cryptographically verifies the CA signature over signedBytes.
     */
    fun validateSignature(cert: OpenSshCertificate): CertValidationResult {
        return try {
            val isValid = when (cert.signatureAlgorithm) {
                "ssh-ed25519" -> {
                    val sig = getSignatureInstance("Ed25519")
                    sig.initVerify(cert.caPublicKey)
                    sig.update(cert.signedBytes)
                    sig.verify(cert.rawSignatureBlob)
                }

                "rsa-sha2-256" -> {
                    val sig = Signature.getInstance("SHA256withRSA")
                    sig.initVerify(cert.caPublicKey)
                    sig.update(cert.signedBytes)
                    sig.verify(cert.rawSignatureBlob)
                }

                "rsa-sha2-512" -> {
                    val sig = Signature.getInstance("SHA512withRSA")
                    sig.initVerify(cert.caPublicKey)
                    sig.update(cert.signedBytes)
                    sig.verify(cert.rawSignatureBlob)
                }

                "ssh-rsa" -> {
                    val sig = Signature.getInstance("SHA1withRSA")
                    sig.initVerify(cert.caPublicKey)
                    sig.update(cert.signedBytes)
                    sig.verify(cert.rawSignatureBlob)
                }

                "ecdsa-sha2-nistp256", "ecdsa-sha2-nistp384", "ecdsa-sha2-nistp521" -> {
                    val derSignature = ecdsaWireToDer(cert.rawSignatureBlob)
                    val algorithm = when (cert.signatureAlgorithm) {
                        "ecdsa-sha2-nistp256" -> "SHA256withECDSA"
                        "ecdsa-sha2-nistp384" -> "SHA384withECDSA"
                        "ecdsa-sha2-nistp521" -> "SHA512withECDSA"
                        else -> "SHA256withECDSA"
                    }
                    val sig = Signature.getInstance(algorithm)
                    sig.initVerify(cert.caPublicKey)
                    sig.update(cert.signedBytes)
                    sig.verify(derSignature)
                }

                else -> return CertValidationResult.InvalidSignature("Unsupported signature algorithm: ${cert.signatureAlgorithm}")
            }

            if (isValid) {
                CertValidationResult.Valid(cert)
            } else {
                CertValidationResult.InvalidSignature("Signature bytes do not match CA public key")
            }
        } catch (e: Exception) {
            CertValidationResult.InvalidSignature("Exception during signature verification: ${e.message}")
        }
    }

    /**
     * Converts an OpenSSH wire ECDSA signature (mpint r, mpint s) into standard ASN.1 DER format.
     */
    fun ecdsaWireToDer(wireSig: ByteArray): ByteArray {
        val reader = WireBufferReader(wireSig)
        val r = reader.readMpint()
        val s = reader.readMpint()
        return DERSequence(arrayOf(ASN1Integer(r), ASN1Integer(s))).encoded
    }

    /**
     * Converts a standard ASN.1 DER ECDSA signature into OpenSSH wire format (mpint r, mpint s).
     */
    fun ecdsaDerToWire(derSig: ByteArray): ByteArray {
        val seq = ASN1Sequence.getInstance(derSig)
        val r = (seq.getObjectAt(0) as ASN1Integer).value
        val s = (seq.getObjectAt(1) as ASN1Integer).value
        val writer = WireBufferWriter()
        writer.writeMpint(r)
        writer.writeMpint(s)
        return writer.toByteArray()
    }

    /**
     * Signs certificate signedBytes with a CA private key and returns the updated certificate.
     */
    fun signCertificate(
        unsignedCert: OpenSshCertificate,
        caPrivateKey: PrivateKey,
        signatureAlgorithm: String,
    ): OpenSshCertificate {
        val rawSig = when (signatureAlgorithm) {
            "ssh-ed25519" -> {
                val sig = getSignatureInstance("Ed25519")
                sig.initSign(caPrivateKey)
                sig.update(unsignedCert.signedBytes)
                sig.sign()
            }

            "rsa-sha2-256" -> {
                val sig = Signature.getInstance("SHA256withRSA")
                sig.initSign(caPrivateKey)
                sig.update(unsignedCert.signedBytes)
                sig.sign()
            }

            "rsa-sha2-512" -> {
                val sig = Signature.getInstance("SHA512withRSA")
                sig.initSign(caPrivateKey)
                sig.update(unsignedCert.signedBytes)
                sig.sign()
            }

            "ssh-rsa" -> {
                val sig = Signature.getInstance("SHA1withRSA")
                sig.initSign(caPrivateKey)
                sig.update(unsignedCert.signedBytes)
                sig.sign()
            }

            "ecdsa-sha2-nistp256", "ecdsa-sha2-nistp384", "ecdsa-sha2-nistp521" -> {
                val algorithm = when (signatureAlgorithm) {
                    "ecdsa-sha2-nistp256" -> "SHA256withECDSA"
                    "ecdsa-sha2-nistp384" -> "SHA384withECDSA"
                    "ecdsa-sha2-nistp521" -> "SHA512withECDSA"
                    else -> "SHA256withECDSA"
                }
                val sig = Signature.getInstance(algorithm)
                sig.initSign(caPrivateKey)
                sig.update(unsignedCert.signedBytes)
                val derSig = sig.sign()
                ecdsaDerToWire(derSig)
            }

            else -> throw IllegalArgumentException("Unsupported signature algorithm: $signatureAlgorithm")
        }

        return unsignedCert.copy(
            signatureAlgorithm = signatureAlgorithm,
            rawSignatureBlob = rawSig,
        )
    }

    fun matchesHostOrPrincipal(pattern: String, candidate: String): Boolean {
        if (pattern == "*" || pattern.equals(candidate, ignoreCase = true)) return true

        // Regex conversion for wildcards * and ?
        val regex = buildString {
            append("^")
            for (char in pattern) {
                when (char) {
                    '*' -> append(".*")
                    '?' -> append(".")
                    '.' -> append("\\.")
                    else -> append(Regex.escape(char.toString()))
                }
            }
            append("$")
        }.toRegex(RegexOption.IGNORE_CASE)

        return regex.matches(candidate)
    }

    private fun isSameKey(k1: PublicKey, k2: PublicKey): Boolean {
        if (k1.encoded != null && k2.encoded != null) {
            return MessageDigest.isEqual(k1.encoded, k2.encoded)
        }
        return k1 == k2
    }

    fun computeFingerprint(key: PublicKey): String = try {
        val blob = OpenSshCertificateParser.encodePublicKeyBlob(key)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(blob)
        "SHA256:" + Base64.getEncoder().encodeToString(digest).trimEnd('=')
    } catch (_: Exception) {
        "SHA256:unknown"
    }

    private fun getSignatureInstance(algorithm: String): Signature = try {
        Signature.getInstance(algorithm, "BC")
    } catch (_: Throwable) {
        Signature.getInstance(algorithm)
    }
}
