package com.adamoutler.ssh.crypto.certificate

import java.security.PublicKey

/**
 * OpenSSH Certificate Type as specified in PROTOCOL.certkeys.
 */
enum class OpenSshCertType(val code: Int) {
    USER(1),
    HOST(2),
    ;

    companion object {
        fun fromCode(code: Int): OpenSshCertType = when (code) {
            1 -> USER
            2 -> HOST
            else -> throw IllegalArgumentException("Unknown certificate type code: $code")
        }
    }
}

/**
 * Result of validating an OpenSSH Certificate.
 */
sealed interface CertValidationResult {
    data class Valid(val cert: OpenSshCertificate) : CertValidationResult
    data class Expired(val validBeforeSeconds: Long, val currentEpochSeconds: Long) : CertValidationResult
    data class NotYetValid(val validAfterSeconds: Long, val currentEpochSeconds: Long) : CertValidationResult
    data class InvalidPrincipal(val requestedPrincipal: String, val validPrincipals: List<String>) : CertValidationResult
    data class UnsupportedCriticalOption(val optionName: String, val optionValue: String) : CertValidationResult
    data class InvalidSignature(val reason: String) : CertValidationResult
    data class InvalidCertType(val expected: OpenSshCertType, val actual: OpenSshCertType) : CertValidationResult
    data class CaNotTrusted(val caFingerprint: String) : CertValidationResult
}

/**
 * Represents a parsed OpenSSH Certificate following PROTOCOL.certkeys.
 */
data class OpenSshCertificate(
    val magic: String,
    val nonce: ByteArray,
    val subjectPublicKey: PublicKey,
    val serial: Long,
    val certType: OpenSshCertType,
    val keyId: String,
    val validPrincipals: List<String>,
    val validAfterSeconds: Long,
    val validBeforeSeconds: Long,
    val criticalOptions: Map<String, String>,
    val extensions: Map<String, String>,
    val caPublicKey: PublicKey,
    val signatureAlgorithm: String,
    val rawSignatureBlob: ByteArray,
    val signedBytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OpenSshCertificate

        if (magic != other.magic) return false
        if (!nonce.contentEquals(other.nonce)) return false
        if (subjectPublicKey != other.subjectPublicKey) return false
        if (serial != other.serial) return false
        if (certType != other.certType) return false
        if (keyId != other.keyId) return false
        if (validPrincipals != other.validPrincipals) return false
        if (validAfterSeconds != other.validAfterSeconds) return false
        if (validBeforeSeconds != other.validBeforeSeconds) return false
        if (criticalOptions != other.criticalOptions) return false
        if (extensions != other.extensions) return false
        if (caPublicKey != other.caPublicKey) return false
        if (signatureAlgorithm != other.signatureAlgorithm) return false
        if (!rawSignatureBlob.contentEquals(other.rawSignatureBlob)) return false
        if (!signedBytes.contentEquals(other.signedBytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = magic.hashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + subjectPublicKey.hashCode()
        result = 31 * result + serial.hashCode()
        result = 31 * result + certType.hashCode()
        result = 31 * result + keyId.hashCode()
        result = 31 * result + validPrincipals.hashCode()
        result = 31 * result + validAfterSeconds.hashCode()
        result = 31 * result + validBeforeSeconds.hashCode()
        result = 31 * result + criticalOptions.hashCode()
        result = 31 * result + extensions.hashCode()
        result = 31 * result + caPublicKey.hashCode()
        result = 31 * result + signatureAlgorithm.hashCode()
        result = 31 * result + rawSignatureBlob.contentHashCode()
        result = 31 * result + signedBytes.contentHashCode()
        return result
    }
}
