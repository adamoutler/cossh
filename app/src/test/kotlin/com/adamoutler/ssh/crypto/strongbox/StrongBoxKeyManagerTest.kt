package com.adamoutler.ssh.crypto.strongbox

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.crypto.hardware.HardwareKeyProvider
import net.schmizz.sshj.common.KeyType
import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1Enumerated
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.DERSequence
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StrongBoxKeyManagerTest {

    private lateinit var context: Context
    private lateinit var manager: StrongBoxKeyManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = StrongBoxKeyManager(context)
    }

    @Test
    fun testIsStrongBoxSupported_reflectsSystemFeature() {
        val shadowPackageManager = Shadows.shadowOf(context.packageManager)

        shadowPackageManager.setSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE, false)
        assertFalse(manager.isStrongBoxSupported())

        shadowPackageManager.setSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE, true)
        assertTrue(manager.isStrongBoxSupported())
    }

    @Test
    fun testGenerateStrongBoxKeyPair_fallbackPipelineGeneratesValidKeyPair() {
        val challenge = "verify-challenge-xyz".toByteArray(Charsets.UTF_8)
        val result = manager.generateStrongBoxKeyPair(
            alias = "test_strongbox_key",
            curve = "secp256r1",
            requireUserAuth = false,
            customChallenge = challenge,
        )

        assertNotNull(result)
        assertEquals("test_strongbox_key", result.alias)
        assertNotNull(result.keyPair)
        assertNotNull(result.publicKeyOpenSsh)
        assertTrue(result.publicKeyOpenSsh.startsWith("ecdsa-sha2-nistp256 "))
    }

    @Test
    fun testParseAttestationBytes_strongBoxLevel() {
        val challenge = "attestation-nonce-12345".toByteArray()
        val uniqueId = "device-serial-uuid-987".toByteArray()

        val extBytes = createMockAttestationExtensionBytes(
            attestationVersion = 3,
            attestationSecurityLevel = 2, // StrongBox
            keymasterVersion = 4,
            keymasterSecurityLevel = 2, // StrongBox
            challenge = challenge,
            uniqueId = uniqueId,
        )

        val record = manager.parseAttestationBytes(extBytes)
        assertNotNull(record)
        assertEquals(3, record!!.attestationVersion)
        assertEquals(AttestationSecurityLevel.STRONGBOX, record.attestationSecurityLevel)
        assertEquals(4, record.keymasterVersion)
        assertEquals(AttestationSecurityLevel.STRONGBOX, record.keymasterSecurityLevel)
        assertTrue(record.isStrongBox)
        assertTrue(record.isHardwareBacked)
        assertArrayEquals(challenge, record.attestationChallenge)
        assertArrayEquals(uniqueId, record.uniqueId)

        assertTrue(manager.verifyAttestationChallenge(record, challenge))
        assertFalse(manager.verifyAttestationChallenge(record, "wrong-challenge".toByteArray()))
    }

    @Test
    fun testParseAttestationBytes_teeLevel() {
        val challenge = "tee-challenge".toByteArray()

        val extBytes = createMockAttestationExtensionBytes(
            attestationVersion = 2,
            attestationSecurityLevel = 1, // TEE
            keymasterVersion = 3,
            keymasterSecurityLevel = 1, // TEE
            challenge = challenge,
            uniqueId = null,
        )

        val record = manager.parseAttestationBytes(extBytes)
        assertNotNull(record)
        assertEquals(AttestationSecurityLevel.TRUSTED_ENVIRONMENT, record!!.attestationSecurityLevel)
        assertFalse(record.isStrongBox)
        assertTrue(record.isHardwareBacked)
        assertNull(record.uniqueId)
    }

    @Test
    fun testParseAttestationBytes_softwareLevel() {
        val extBytes = createMockAttestationExtensionBytes(
            attestationVersion = 1,
            attestationSecurityLevel = 0, // Software
            keymasterVersion = 1,
            keymasterSecurityLevel = 0, // Software
            challenge = "sw-chal".toByteArray(),
            uniqueId = null,
        )

        val record = manager.parseAttestationBytes(extBytes)
        assertNotNull(record)
        assertEquals(AttestationSecurityLevel.SOFTWARE, record!!.attestationSecurityLevel)
        assertFalse(record.isStrongBox)
        assertFalse(record.isHardwareBacked)
    }

    @Test
    fun testParseAttestationBytes_corruptedBytesReturnsNull() {
        val corrupted = byteArrayOf(0x30, 0x02, 0x01)
        val record = manager.parseAttestationBytes(corrupted)
        assertNull(record)
    }

    @Test
    fun testHardwareKeyProvider_handlesNullEncodedSafely() {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(256)
        val kp = kpg.generateKeyPair()

        // Mock a hardware-backed private key where getEncoded() returns null
        val hardwarePrivKey = object : PrivateKey by kp.private {
            override fun getEncoded(): ByteArray? = null
            override fun getFormat(): String? = null
        }

        val provider = HardwareKeyProvider(
            pubKey = kp.public,
            privKey = hardwarePrivKey,
            keyType = KeyType.ECDSA256,
        )

        assertEquals(kp.public, provider.public)
        assertEquals(hardwarePrivKey, provider.private)
        assertEquals(KeyType.ECDSA256, provider.type)
        assertTrue(provider.isHardwareBacked)
    }

    @Test
    fun testHardwareKeyProvider_withExportableKeyReportsNotHardwareBacked() {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(256)
        val kp = kpg.generateKeyPair()

        val provider = HardwareKeyProvider(
            pubKey = kp.public,
            privKey = kp.private,
            keyType = KeyType.ECDSA256,
        )

        assertFalse(provider.isHardwareBacked)
    }

    private fun createMockAttestationExtensionBytes(
        attestationVersion: Int,
        attestationSecurityLevel: Int,
        keymasterVersion: Int,
        keymasterSecurityLevel: Int,
        challenge: ByteArray,
        uniqueId: ByteArray?,
    ): ByteArray {
        val vec = ASN1EncodableVector()
        vec.add(ASN1Integer(attestationVersion.toLong()))
        vec.add(ASN1Enumerated(attestationSecurityLevel))
        vec.add(ASN1Integer(keymasterVersion.toLong()))
        vec.add(ASN1Enumerated(keymasterSecurityLevel))
        vec.add(DEROctetString(challenge))
        if (uniqueId != null) {
            vec.add(DEROctetString(uniqueId))
        }
        val innerSeq = DERSequence(vec)
        val outerOctet = DEROctetString(innerSeq.encoded)
        return outerOctet.encoded
    }
}
