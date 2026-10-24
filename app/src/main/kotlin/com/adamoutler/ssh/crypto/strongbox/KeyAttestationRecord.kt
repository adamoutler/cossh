package com.adamoutler.ssh.crypto.strongbox

import java.security.KeyPair
import java.security.cert.Certificate

/**
 * Android Key Attestation Security Level (ASN.1 ENUMERATED).
 */
enum class AttestationSecurityLevel(val level: Int) {
    SOFTWARE(0),
    TRUSTED_ENVIRONMENT(1),
    STRONGBOX(2),
    UNKNOWN(-1),
    ;

    companion object {
        fun fromInt(value: Int): AttestationSecurityLevel = entries.find { it.level == value } ?: UNKNOWN
    }
}

/**
 * Parsed Android Key Attestation record from X.509 extension OID 1.3.6.1.4.1.11129.2.1.17.
 */
data class KeyAttestationRecord(
    val attestationVersion: Int,
    val attestationSecurityLevel: AttestationSecurityLevel,
    val keymasterVersion: Int,
    val keymasterSecurityLevel: AttestationSecurityLevel,
    val attestationChallenge: ByteArray,
    val uniqueId: ByteArray? = null,
    val softwareEnforced: Map<String, String> = emptyMap(),
    val teeEnforced: Map<String, String> = emptyMap(),
) {
    val isHardwareBacked: Boolean
        get() = attestationSecurityLevel == AttestationSecurityLevel.STRONGBOX ||
            attestationSecurityLevel == AttestationSecurityLevel.TRUSTED_ENVIRONMENT

    val isStrongBox: Boolean
        get() = attestationSecurityLevel == AttestationSecurityLevel.STRONGBOX

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KeyAttestationRecord

        if (attestationVersion != other.attestationVersion) return false
        if (attestationSecurityLevel != other.attestationSecurityLevel) return false
        if (keymasterVersion != other.keymasterVersion) return false
        if (keymasterSecurityLevel != other.keymasterSecurityLevel) return false
        if (!attestationChallenge.contentEquals(other.attestationChallenge)) return false
        if (uniqueId != null) {
            if (other.uniqueId == null) return false
            if (!uniqueId.contentEquals(other.uniqueId)) return false
        } else if (other.uniqueId != null) {
            return false
        }
        if (softwareEnforced != other.softwareEnforced) return false
        if (teeEnforced != other.teeEnforced) return false

        return true
    }

    override fun hashCode(): Int {
        var result = attestationVersion
        result = 31 * result + attestationSecurityLevel.hashCode()
        result = 31 * result + keymasterVersion
        result = 31 * result + keymasterSecurityLevel.hashCode()
        result = 31 * result + attestationChallenge.contentHashCode()
        result = 31 * result + (uniqueId?.contentHashCode() ?: 0)
        result = 31 * result + softwareEnforced.hashCode()
        result = 31 * result + teeEnforced.hashCode()
        return result
    }
}

/**
 * Result of StrongBox / Keystore hardware-backed key generation.
 */
data class StrongBoxKeyResult(
    val alias: String,
    val keyPair: KeyPair,
    val isStrongBoxBacked: Boolean,
    val attestationRecord: KeyAttestationRecord?,
    val publicKeyOpenSsh: String,
    val certificateChain: List<Certificate>? = null,
)
