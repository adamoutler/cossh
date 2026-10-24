package com.adamoutler.ssh.crypto.strongbox

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import com.adamoutler.ssh.crypto.SSHKeyGenerator
import org.bouncycastle.asn1.ASN1Enumerated
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.ASN1Sequence
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.ProviderException
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec

/**
 * Manages generation of hardware-backed StrongBox and TEE KeyPairs in AndroidKeyStore,
 * with graceful fallback pipelines and ASN.1 Key Attestation verification.
 */
class StrongBoxKeyManager(
    private val context: Context,
) {

    companion object {
        const val KEY_ATTESTATION_OID = "1.3.6.1.4.1.11129.2.1.17"
        private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
    }

    /**
     * Determines whether the hardware supports StrongBox Keymaster/KeyMint.
     */
    fun isStrongBoxSupported(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        try {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        } catch (_: Throwable) {
            false
        }
    } else {
        false
    }

    /**
     * Generates a hardware-backed EC KeyPair with custom attestation challenge.
     * Attempts StrongBox first (if supported), falling back to standard TEE AndroidKeyStore,
     * and finally to software EC KeyPair in environments where AndroidKeyStore is unavailable.
     */
    fun generateStrongBoxKeyPair(
        alias: String,
        curve: String = "secp256r1",
        requireUserAuth: Boolean = false,
        customChallenge: ByteArray? = null,
    ): StrongBoxKeyResult {
        val strongBoxRequested = isStrongBoxSupported()

        // 1. Attempt StrongBox if supported
        if (strongBoxRequested && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val result = generateKeyInAndroidKeyStore(
                    alias = alias,
                    curve = curve,
                    requireUserAuth = requireUserAuth,
                    customChallenge = customChallenge,
                    useStrongBox = true,
                )
                return result
            } catch (_: StrongBoxUnavailableException) {
                // StrongBox was reported as supported but unavailable at runtime; proceed to TEE fallback
            } catch (e: ProviderException) {
                if (e.message?.contains("StrongBox", ignoreCase = true) == true) {
                    // Fall back to TEE
                } else {
                    // Check for general keystore fallback
                }
            } catch (_: Throwable) {
                // Proceed to TEE fallback
            }
        }

        // 2. Attempt standard AndroidKeyStore TEE
        try {
            return generateKeyInAndroidKeyStore(
                alias = alias,
                curve = curve,
                requireUserAuth = requireUserAuth,
                customChallenge = customChallenge,
                useStrongBox = false,
            )
        } catch (_: Throwable) {
            // AndroidKeyStore unavailable (e.g. pure Robolectric / unit testing)
        }

        // 3. Fallback to software EC KeyPair
        val softwareKeyPair = SSHKeyGenerator.generateECDSAKeyPair(curve)
        val openSshPub = SSHKeyGenerator.encodePublicKey(softwareKeyPair)
        return StrongBoxKeyResult(
            alias = alias,
            keyPair = softwareKeyPair,
            isStrongBoxBacked = false,
            attestationRecord = null,
            publicKeyOpenSsh = openSshPub,
            certificateChain = null,
        )
    }

    private fun generateKeyInAndroidKeyStore(
        alias: String,
        curve: String,
        requireUserAuth: Boolean,
        customChallenge: ByteArray?,
        useStrongBox: Boolean,
    ): StrongBoxKeyResult {
        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE_PROVIDER)
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec(curve))
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)

        if (customChallenge != null && customChallenge.isNotEmpty()) {
            builder.setAttestationChallenge(customChallenge)
        }

        if (requireUserAuth) {
            builder.setUserAuthenticationRequired(true)
        }

        if (useStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true)
        }

        kpg.initialize(builder.build())
        val keyPair = kpg.generateKeyPair()

        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
        val certChain: List<Certificate>? = try {
            keyStore.getCertificateChain(alias)?.toList()
        } catch (_: Throwable) {
            null
        }

        var attestationRecord: KeyAttestationRecord? = null
        if (!certChain.isNullOrEmpty()) {
            val leafCert = certChain[0] as? X509Certificate
            if (leafCert != null) {
                attestationRecord = parseAttestationRecord(leafCert)
            }
        }

        val openSshPub = SSHKeyGenerator.encodePublicKey(keyPair)
        val isStrongBoxBacked = useStrongBox &&
            (
                attestationRecord?.attestationSecurityLevel == AttestationSecurityLevel.STRONGBOX ||
                    attestationRecord?.keymasterSecurityLevel == AttestationSecurityLevel.STRONGBOX
                )

        return StrongBoxKeyResult(
            alias = alias,
            keyPair = keyPair,
            isStrongBoxBacked = isStrongBoxBacked,
            attestationRecord = attestationRecord,
            publicKeyOpenSsh = openSshPub,
            certificateChain = certChain,
        )
    }

    /**
     * Parses the ASN.1 Key Attestation record from the X.509 certificate extension.
     */
    fun parseAttestationRecord(certificate: X509Certificate): KeyAttestationRecord? {
        val extBytes = certificate.getExtensionValue(KEY_ATTESTATION_OID) ?: return null
        return parseAttestationBytes(extBytes)
    }

    /**
     * Parses raw DER-encoded Key Attestation extension octets.
     */
    fun parseAttestationBytes(derEncodedBytes: ByteArray): KeyAttestationRecord? {
        return try {
            val octets = ASN1OctetString.getInstance(derEncodedBytes).octets
            val seq = ASN1Sequence.getInstance(octets)
            if (seq.size() < 5) return null

            val attestationVersion = (seq.getObjectAt(0) as ASN1Integer).value.toInt()
            val attestationSecLevelVal = (seq.getObjectAt(1) as ASN1Enumerated).value.toInt()
            val attestationSecLevel = AttestationSecurityLevel.fromInt(attestationSecLevelVal)

            val kmVersion = (seq.getObjectAt(2) as ASN1Integer).value.toInt()
            val kmSecLevelVal = (seq.getObjectAt(3) as ASN1Enumerated).value.toInt()
            val kmSecLevel = AttestationSecurityLevel.fromInt(kmSecLevelVal)

            val challenge = (seq.getObjectAt(4) as ASN1OctetString).octets

            val uniqueId = if (seq.size() > 5 && seq.getObjectAt(5) is ASN1OctetString) {
                (seq.getObjectAt(5) as ASN1OctetString).octets
            } else {
                null
            }

            KeyAttestationRecord(
                attestationVersion = attestationVersion,
                attestationSecurityLevel = attestationSecLevel,
                keymasterVersion = kmVersion,
                keymasterSecurityLevel = kmSecLevel,
                attestationChallenge = challenge,
                uniqueId = uniqueId,
            )
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Verifies that the attestation record contains the expected challenge bytes.
     */
    fun verifyAttestationChallenge(
        record: KeyAttestationRecord,
        expectedChallenge: ByteArray,
    ): Boolean = MessageDigest.isEqual(record.attestationChallenge, expectedChallenge)
}
