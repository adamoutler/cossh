package com.adamoutler.ssh.security

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.crypto.KnownHostsManager
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.crypto.dnssec.SshfpRecord
import com.adamoutler.ssh.crypto.dnssec.SshfpValidationResult
import com.adamoutler.ssh.crypto.dnssec.SshfpVerificationResult
import com.adamoutler.ssh.crypto.dnssec.SshfpVerifier
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.network.PasswordAuthenticator
import com.adamoutler.ssh.network.SecureSshConfig
import com.adamoutler.ssh.network.TofuHostKeyVerifier
import com.adamoutler.ssh.security.automation.AutomationIntentGuard
import com.adamoutler.ssh.ui.navigation.IntentSanitizer
import net.schmizz.sshj.AndroidConfig
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.security.KeyPairGenerator
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RuntimeCryptoWiringAndSuiteHardeningTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var storageManager: SecurityStorageManager
    private lateinit var guard: AutomationIntentGuard
    private lateinit var secretKey: ByteArray
    private val testProfileId = "target-node-42"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences("test_sec_storage_wiring", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        storageManager = SecurityStorageManager(context, prefs)
        secretKey = storageManager.generateOrGetAutomationSecretKey()

        val profile = ConnectionProfile(
            id = testProfileId,
            nickname = "Target Production Node",
            host = "10.0.0.42",
            port = 22,
        )
        storageManager.saveProfile(profile)

        guard = AutomationIntentGuard(
            securityStorageManager = storageManager,
        )
    }

    // -------------------------------------------------------------------------
    // 1. PasswordAuthenticator UTF-8 Multi-Byte Decoding & Zero-Wiping
    // -------------------------------------------------------------------------

    @Test
    fun testPasswordAuthenticator_utf8DecodingPreservesMultiByteAndZeroWipes() {
        val testPassword = "pässwörd🔒€"
        val rawBytes = testPassword.toByteArray(Charsets.UTF_8)

        val decodedChars = PasswordAuthenticator.decodeUtf8BytesToChars(rawBytes)

        assertEquals("Decoded char array string must match original UTF-8 text", testPassword, String(decodedChars))
        assertEquals(testPassword.length, decodedChars.size)

        // Verify zero-wipe sanitization
        decodedChars.fill('\u0000')
        assertTrue("Wiped char buffer must consist entirely of null terminators", decodedChars.all { it == '\u0000' })
    }

    // -------------------------------------------------------------------------
    // 2. SecureSshConfig Hardening: Purge of -cbc Ciphers
    // -------------------------------------------------------------------------

    @Test
    fun testSecureSshConfig_purgesAllCbcCiphersWhileRetainingAeadAndCtr() {
        val defaultConfig = AndroidConfig()
        val secureConfig = SecureSshConfig()

        val defaultCipherNames = defaultConfig.cipherFactories.map { it.name.lowercase() }
        val secureCipherNames = secureConfig.cipherFactories.map { it.name.lowercase() }

        // Confirm default config had CBC ciphers
        assertTrue(
            "Default config must contain cbc ciphers prior to hardening",
            defaultCipherNames.any { it.contains("cbc") },
        )

        // Hardened config must contain zero cbc ciphers
        val remainingCbc = secureCipherNames.filter { it.contains("cbc") }
        assertTrue("SecureSshConfig must contain zero -cbc ciphers but found: $remainingCbc", remainingCbc.isEmpty())

        // Modern CTR/AEAD must be preserved
        assertTrue(
            "Hardened suite must retain CTR ciphers: $secureCipherNames",
            secureCipherNames.any { it.contains("-ctr") },
        )
        assertTrue(
            "Hardened suite must retain AES ciphers: $secureCipherNames",
            secureCipherNames.any { it.contains("aes") },
        )
    }

    // -------------------------------------------------------------------------
    // 3. SshfpVerifier: Strict Rejection of Obsolete SHA-1 Records
    // -------------------------------------------------------------------------

    @Test
    fun testSshfpVerifier_rejectsSha1RecordsAndAcceptsSha256() {
        val dummyHostKey = "ssh-ed25519-adversarial-test-key".toByteArray(Charsets.UTF_8)
        val sha1 = MessageDigest.getInstance("SHA-1").digest(dummyHostKey)
        val sha256 = MessageDigest.getInstance("SHA-256").digest(dummyHostKey)

        // 3a. DNS response containing only SHA-1 record (must be rejected/ignored -> FingerprintMismatchAlert)
        val sha1OnlyDnsResponse = createDnsResponseBytes(
            adBit = true,
            records = listOf(
                SshfpRecord(
                    algorithm = SshfpVerifier.ALGORITHM_ED25519,
                    fpType = SshfpVerifier.FPTYPE_SHA1,
                    fingerprint = sha1,
                ),
            ),
        )

        val sha1Result = SshfpVerifier.verifyHostKeyWire(
            hostname = "bastion.example.com",
            algorithm = SshfpVerifier.ALGORITHM_ED25519,
            hostKeyWireBytes = dummyHostKey,
            dnsResponseBytes = sha1OnlyDnsResponse,
        )

        assertTrue(
            "SHA-1 record must not validate host key due to collision risk: $sha1Result",
            sha1Result is SshfpValidationResult.FingerprintMismatchAlert,
        )

        // 3b. DNS response containing SHA-256 record (must be accepted as ValidDnssecMatch)
        val sha256DnsResponse = createDnsResponseBytes(
            adBit = true,
            records = listOf(
                SshfpRecord(
                    algorithm = SshfpVerifier.ALGORITHM_ED25519,
                    fpType = SshfpVerifier.FPTYPE_SHA256,
                    fingerprint = sha256,
                ),
            ),
        )

        val sha256Result = SshfpVerifier.verifyHostKeyWire(
            hostname = "bastion.example.com",
            algorithm = SshfpVerifier.ALGORITHM_ED25519,
            hostKeyWireBytes = dummyHostKey,
            dnsResponseBytes = sha256DnsResponse,
        )

        assertTrue(
            "SHA-256 record must validate host key: $sha256Result",
            sha256Result is SshfpValidationResult.ValidDnssecMatch,
        )
        val match = sha256Result as SshfpValidationResult.ValidDnssecMatch
        assertEquals(SshfpVerifier.FPTYPE_SHA256, match.record.fpType)
        assertArrayEquals(sha256, match.record.fingerprint)
    }

    // -------------------------------------------------------------------------
    // 4. TofuHostKeyVerifier: Wiring with SshfpVerifier (Auto-Trust & Rejection)
    // -------------------------------------------------------------------------

    @Test
    fun testTofuHostKeyVerifier_autoAcceptsOnVerifiedAndRejectsOnMismatch() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val pubKey = keyPair.public

        // 4a. Auto-trust on Verified
        val knownHostsFile1 = tempFolder.newFile("known_hosts_auto_trust")
        val verifiedVerifier = object : SshfpVerifier() {
            override fun verifyHostKey(hostname: String, algorithm: String, keyEncoded: ByteArray?): SshfpVerificationResult = SshfpVerificationResult.Verified(
                SshfpRecord(ALGORITHM_RSA, FPTYPE_SHA256, ByteArray(32) { 0x42.toByte() }),
            )
        }

        val tofuVerifier = TofuHostKeyVerifier(
            knownHostsFile = knownHostsFile1,
            isAutonomousReconnect = false,
            context = context,
            sshfpVerifier = verifiedVerifier,
        )

        val accepted = tofuVerifier.verify("auto-trust.example.com", 22, pubKey)
        assertTrue("TofuHostKeyVerifier must auto-accept when DNSSEC SSHFP verifies key", accepted)

        val km1 = KnownHostsManager(knownHostsFile1)
        val entries = km1.getEntries()
        assertTrue(
            "Host key must be saved in known_hosts file upon verified DNSSEC auto-trust",
            entries.any { km1.matchesHost(it, "auto-trust.example.com", 22) },
        )

        // 4b. Immediate rejection on FingerprintMismatchAlert
        val knownHostsFile2 = tempFolder.newFile("known_hosts_mismatch")
        val mismatchVerifier = object : SshfpVerifier() {
            override fun verifyHostKey(hostname: String, algorithm: String, keyEncoded: ByteArray?): SshfpVerificationResult = SshfpVerificationResult.FingerprintMismatchAlert(
                expectedFingerprints = listOf(SshfpRecord(ALGORITHM_RSA, FPTYPE_SHA256, ByteArray(32))),
                actualFingerprint = ByteArray(32) { 0x99.toByte() },
                actualFingerprintHex = "99".repeat(32),
            )
        }

        val tofuMismatchVerifier = TofuHostKeyVerifier(
            knownHostsFile = knownHostsFile2,
            isAutonomousReconnect = false,
            context = context,
            sshfpVerifier = mismatchVerifier,
        )

        val rejected = tofuMismatchVerifier.verify("mitm-target.example.com", 22, pubKey)
        assertFalse("TofuHostKeyVerifier must immediately reject on DNSSEC SSHFP mismatch alert", rejected)
    }

    // -------------------------------------------------------------------------
    // 5. IntentSanitizer: Wiring with AutomationIntentGuard (HMAC Authentication)
    // -------------------------------------------------------------------------

    @Test
    fun testIntentSanitizer_authenticatesValidAutomationIntentAndRejectsForged() {
        // 5a. Valid HMAC-signed automation intent
        val validIntent = AutomationIntentGuard.createSignedIntent(
            context = context,
            profileId = testProfileId,
            secretKey = secretKey,
            action = AutomationIntentGuard.ACTION_EXECUTE_AUTOMATION,
        )

        val validTarget = IntentSanitizer.validateLaunchIntent(
            intent = validIntent,
            profileExists = { it == testProfileId },
            automationIntentGuard = guard,
        )

        assertNotNull("Valid signed intent must be successfully validated", validTarget)
        assertEquals(testProfileId, validTarget?.profileId)
        assertEquals("terminal?profileId=$testProfileId", validTarget?.route)

        // 5b. Unsigned / Forged automation intent
        val forgedIntent = Intent(AutomationIntentGuard.ACTION_EXECUTE_AUTOMATION).apply {
            putExtra(AutomationIntentGuard.EXTRA_PROFILE_ID, testProfileId)
            putExtra(AutomationIntentGuard.EXTRA_NONCE, "forged-nonce-12345")
            putExtra(AutomationIntentGuard.EXTRA_TIMESTAMP, System.currentTimeMillis())
            putExtra(AutomationIntentGuard.EXTRA_SIGNATURE, "deadbeef".repeat(8)) // invalid signature
        }

        val forgedTarget = IntentSanitizer.validateLaunchIntent(
            intent = forgedIntent,
            profileExists = { it == testProfileId },
            automationIntentGuard = guard,
        )

        assertNull("Forged signature must be strictly rejected", forgedTarget)

        // 5c. Valid signature but missing / nonexistent profile in profileExists predicate
        val nonexistentTarget = IntentSanitizer.validateLaunchIntent(
            intent = validIntent,
            profileExists = { false }, // Profile does not exist
            automationIntentGuard = guard,
        )

        assertNull("Intent for non-existent profile must be rejected", nonexistentTarget)

        // 5d. Automation intent provided without guard instance
        val noGuardTarget = IntentSanitizer.validateLaunchIntent(
            intent = validIntent,
            profileExists = { true },
            automationIntentGuard = null,
        )

        assertNull("Automation intent must be rejected when guard is null", noGuardTarget)
    }

    private fun createDnsResponseBytes(
        adBit: Boolean,
        records: List<SshfpRecord>,
        rcode: Int = 0,
    ): ByteArray {
        val out = ByteArrayOutputStream()

        // 12-byte header
        out.write(0x00)
        out.write(0x00)

        var flags = 0x8180 or (rcode and 0x0F)
        if (adBit) {
            flags = flags or 0x0020
        }
        out.write((flags ushr 8) and 0xFF)
        out.write(flags and 0xFF)

        // QDCOUNT: 1
        out.write(0x00)
        out.write(0x01)
        // ANCOUNT
        out.write((records.size ushr 8) and 0xFF)
        out.write(records.size and 0xFF)
        // NSCOUNT: 0
        out.write(0x00)
        out.write(0x00)
        // ARCOUNT: 0
        out.write(0x00)
        out.write(0x00)

        // Question: 3 "ssh" 0, QTYPE 44, QCLASS 1
        out.write(3)
        out.write("ssh".toByteArray(Charsets.US_ASCII))
        out.write(0)
        out.write(0x00)
        out.write(SshfpVerifier.RR_TYPE_SSHFP)
        out.write(0x00)
        out.write(SshfpVerifier.CLASS_IN)

        // Answers
        for (rec in records) {
            out.write(0xC0)
            out.write(0x0C)
            out.write(0x00)
            out.write(SshfpVerifier.RR_TYPE_SSHFP)
            out.write(0x00)
            out.write(SshfpVerifier.CLASS_IN)
            out.write(0x00)
            out.write(0x00)
            out.write(0x01)
            out.write(0x2C)

            val rdLen = 2 + rec.fingerprint.size
            out.write((rdLen ushr 8) and 0xFF)
            out.write(rdLen and 0xFF)

            out.write(rec.algorithm)
            out.write(rec.fpType)
            out.write(rec.fingerprint)
        }

        return out.toByteArray()
    }
}
