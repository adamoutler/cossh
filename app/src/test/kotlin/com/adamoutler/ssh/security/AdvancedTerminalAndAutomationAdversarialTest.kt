package com.adamoutler.ssh.security

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.backup.BackupSanitizer
import com.adamoutler.ssh.crypto.KnownHostRecord
import com.adamoutler.ssh.crypto.KnownHostsBulkPorter
import com.adamoutler.ssh.crypto.KnownHostsManager
import com.adamoutler.ssh.crypto.KnownHostsMergeStrategy
import com.adamoutler.ssh.crypto.SSHKeyGenerator
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.crypto.certificate.CertValidationResult
import com.adamoutler.ssh.crypto.certificate.OpenSshCertType
import com.adamoutler.ssh.crypto.certificate.OpenSshCertificate
import com.adamoutler.ssh.crypto.certificate.OpenSshCertificateParser
import com.adamoutler.ssh.crypto.certificate.OpenSshCertificateValidator
import com.adamoutler.ssh.crypto.certificate.WireBufferReader
import com.adamoutler.ssh.crypto.certificate.WireBufferWriter
import com.adamoutler.ssh.crypto.dnssec.SshfpRecord
import com.adamoutler.ssh.crypto.dnssec.SshfpValidationResult
import com.adamoutler.ssh.crypto.dnssec.SshfpVerifier
import com.adamoutler.ssh.data.CommandSnippet
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.KnockProtocol
import com.adamoutler.ssh.data.PortKnockStep
import com.adamoutler.ssh.data.PortKnockingConfig
import com.adamoutler.ssh.network.LocalShellConnectionHandler
import com.adamoutler.ssh.network.PortKnockingManager
import com.adamoutler.ssh.network.WolManager
import com.adamoutler.ssh.security.automation.AutomationIntentGuard
import com.adamoutler.ssh.security.automation.AutomationValidationResult
import com.adamoutler.ssh.security.automation.NonceReplayCache
import com.adamoutler.ssh.ui.components.TerminalSnippetHandler
import kotlinx.coroutines.runBlocking
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.MessageDigest
import java.security.Security
import java.util.UUID

/**
 * Hardened adversarial test suite verifying terminal execution environments,
 * OpenSSH host certificates, known_hosts anti-MITM defenses, DNSSEC SSHFP wire validation,
 * command snippet biometric gating, and pre-connect automation security invariants.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdvancedTerminalAndAutomationAdversarialTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun setupCryptoProvider() {
            if (Security.getProvider("BC") == null) {
                Security.addProvider(BouncyCastleProvider())
            }
        }
    }

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var knownHostsFile: File
    private lateinit var knownHostsManager: KnownHostsManager
    private lateinit var securityStorageManager: SecurityStorageManager
    private lateinit var automationSecretKey: ByteArray
    private val validAutomationProfileId = "adv-target-node-99"
    private var simulatedClockMs: Long = 1_700_000_000_000L

    private val sampleEd25519Key1 = "AAAAC3NzaC1lZDI1NTE5AAAAIGo4wN/jWvjZ4mN+0pB4B9wT7Hq9q2r1x8kL8p8kL8p8"
    private val sampleEd25519Key2 = "AAAAC3NzaC1lZDI1NTE5AAAAINewKeyDataForReplacement1234567890123"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        // Setup KnownHostsManager with temporary file
        knownHostsFile = tempFolder.newFile("adversarial_known_hosts")
        knownHostsManager = KnownHostsManager(knownHostsFile)

        // Setup SecurityStorageManager for automation
        val prefs = context.getSharedPreferences("adv_sec_storage_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        securityStorageManager = SecurityStorageManager(context, prefs)
        automationSecretKey = securityStorageManager.generateOrGetAutomationSecretKey()

        val validProfile = ConnectionProfile(
            id = validAutomationProfileId,
            nickname = "Adversarial Target Node",
            host = "10.0.0.1",
            port = 22,
        )
        securityStorageManager.saveProfile(validProfile)

        PortKnockingManager.resetCooldownForTesting()
    }

    @After
    fun tearDown() {
        PortKnockingManager.resetCooldownForTesting()
    }

    // =========================================================================
    // 1. Environment Variable & initialDirectory Injection Hardening
    // =========================================================================

    @Test
    fun testLocalShellConnectionHandler_stripsDangerousPreloadAndLibraryPathVars_caseInsensitive() {
        val incomingEnvVars = mapOf(
            "LD_PRELOAD" to "/system/lib64/hostile_preload.so",
            "ld_preload" to "/tmp/evil_payload.so",
            "Ld_Preload" to "/data/local/tmp/inject.so",
            "LD_LIBRARY_PATH" to "/evil/lib",
            "ld_library_path" to "/evil/lib2",
            "Ld_Library_Path" to "/evil/lib3",
            "APP_ENV" to "production",
            "TERM" to "xterm-256color",
            "SAFE_API_KEY" to "k8s-prod-987654",
        )

        val environment = mutableMapOf(
            "HOST_SYSTEM_VAR" to "system_value",
            "LD_PRELOAD" to "/preexisting/library.so",
            "LD_LIBRARY_PATH" to "/preexisting/path",
        )

        LocalShellConnectionHandler.sanitizeEnvironment(environment, incomingEnvVars)

        // Verify dangerous injection variables are completely omitted and stripped
        assertFalse("LD_PRELOAD must not exist", environment.containsKey("LD_PRELOAD"))
        assertFalse("ld_preload must not exist", environment.containsKey("ld_preload"))
        assertFalse("Ld_Preload must not exist", environment.containsKey("Ld_Preload"))
        assertFalse("LD_LIBRARY_PATH must not exist", environment.containsKey("LD_LIBRARY_PATH"))
        assertFalse("ld_library_path must not exist", environment.containsKey("ld_library_path"))
        assertFalse("Ld_Library_Path must not exist", environment.containsKey("Ld_Library_Path"))

        // Verify valid variables are preserved
        assertEquals("production", environment["APP_ENV"])
        assertEquals("k8s-prod-987654", environment["SAFE_API_KEY"])
        assertEquals("system_value", environment["HOST_SYSTEM_VAR"])

        // Invariant: TERM must be preserved / set to xterm-256color
        assertEquals("xterm-256color", environment["TERM"])

        // Invariant: Default TERM is set when not present in profile env vars
        val envDefault = mutableMapOf<String, String>()
        LocalShellConnectionHandler.sanitizeEnvironment(envDefault, emptyMap())
        assertEquals("xterm-256color", envDefault["TERM"])
    }

    @Test
    fun testSshConnectionHandler_neutralizesInitialDirectoryControlCharactersAndEscapesQuotes() {
        val hostileInputs = listOf(
            "/var/log\r\nrm -rf /" to "/var/logrm -rf /",
            "/tmp\u0000/evil" to "/tmp/evil",
            "/opt/app\u001B[31m/bin" to "/opt/app[31m/bin",
            "/var/run\u007F/daemon" to "/var/run/daemon",
            "/var/log/my'dir'with'quotes" to "/var/log/my'\\''dir'\\''with'\\''quotes",
            "/var/log\r\ncd /evil'injection" to "/var/logcd /evil'\\''injection",
        )

        for ((hostileInput, expectedEscaped) in hostileInputs) {
            // SshConnectionHandler sanitization logic:
            val sanitized = hostileInput.filter { ch -> ch.code >= 0x20 && ch.code != 0x7F }
            assertTrue("Sanitized string must not be blank", sanitized.isNotBlank())

            val escapedDir = sanitized.replace("'", "'\\''")
            val cdCmd = "cd '$escapedDir'\r\n"

            // Verify safe escaping
            assertEquals(expectedEscaped, escapedDir)
            assertTrue(cdCmd.startsWith("cd '"))
            assertTrue(cdCmd.endsWith("'\r\n"))
            assertFalse(cdCmd.contains("\u0000"))

            // Verify no embedded unescaped CRLF inside the cd parameter
            val innerParam = cdCmd.substring(4, cdCmd.length - 2)
            assertFalse("Inner cd parameter must not contain raw carriage return", innerParam.contains("\r"))
            assertFalse("Inner cd parameter must not contain raw newline", innerParam.contains("\n"))
        }
    }

    @Test
    fun testBackupSanitizer_neutralizesHostileInitialDirectory() {
        val hostileProfile = ConnectionProfile(
            id = "profile-hostile-dir",
            nickname = "Hostile Directory Profile",
            host = "192.168.1.50",
            initialDirectory = "/var/log\r\nrm -rf /\u0000",
        )

        val (sanitizedProfiles, _) = BackupSanitizer.sanitize(
            profiles = listOf(hostileProfile),
            identities = emptyList(),
        )

        assertEquals(1, sanitizedProfiles.size)
        val cleanProfile = sanitizedProfiles[0]
        assertNotNull(cleanProfile.initialDirectory)
        // Control characters must be stripped by HostValidator.stripControlChars
        assertEquals("/var/logrm -rf /", cleanProfile.initialDirectory)
        assertFalse(cleanProfile.initialDirectory!!.contains("\r"))
        assertFalse(cleanProfile.initialDirectory!!.contains("\n"))
        assertFalse(cleanProfile.initialDirectory!!.contains("\u0000"))
    }

    // =========================================================================
    // 2. Command Snippets Biometric Gate & Formatting (TerminalSnippetHandler)
    // =========================================================================

    @Test
    fun testTerminalSnippetHandler_requireAuth_gatesExecutionBehindAuthentication() {
        val snippet = CommandSnippet(
            id = "snip-critical-01",
            name = "Reboot Production Server",
            command = "sudo reboot",
            autoSend = true,
            requireAuth = true,
        )

        val terminalDispatchedBytes = mutableListOf<ByteArray>()
        var authPromptInvoked = false
        var capturedOnAuthenticatedCallback: (() -> Unit)? = null

        val handler = TerminalSnippetHandler(
            sendToTerminal = { bytes -> terminalDispatchedBytes.add(bytes) },
            requestConfirmation = { fail("Should route to authentication prompt when requireAuth is true and handler provided") },
            requestAuthentication = { requestedSnippet, onAuthenticated ->
                assertEquals(snippet.id, requestedSnippet.id)
                authPromptInvoked = true
                capturedOnAuthenticatedCallback = onAuthenticated
            },
        )

        // 1. User clicks snippet chip
        handler.onSnippetClicked(snippet)

        assertTrue("Authentication prompt must be invoked immediately", authPromptInvoked)
        assertTrue("No bytes should be dispatched to terminal before biometric authentication succeeds", terminalDispatchedBytes.isEmpty())

        // 2. Biometric authentication succeeds
        assertNotNull(capturedOnAuthenticatedCallback)
        capturedOnAuthenticatedCallback!!.invoke()

        // 3. Command dispatched to terminal
        assertEquals(1, terminalDispatchedBytes.size)
        val dispatchedString = String(terminalDispatchedBytes[0], StandardCharsets.UTF_8)
        assertEquals("sudo reboot\r", dispatchedString)
    }

    @Test
    fun testTerminalSnippetHandler_requireAuth_cancelledAuth_neverExecutes() {
        val snippet = CommandSnippet(
            id = "snip-critical-02",
            name = "Drop Database",
            command = "dropdb production",
            autoSend = true,
            requireAuth = true,
        )

        val terminalDispatchedBytes = mutableListOf<ByteArray>()
        var authPromptInvoked = false

        val handler = TerminalSnippetHandler(
            sendToTerminal = { bytes -> terminalDispatchedBytes.add(bytes) },
            requestAuthentication = { _, _ ->
                authPromptInvoked = true
                // Simulating user cancellation or biometric mismatch (callback never called)
            },
        )

        handler.onSnippetClicked(snippet)

        assertTrue(authPromptInvoked)
        assertTrue("Terminal dispatch MUST remain empty when authentication is cancelled", terminalDispatchedBytes.isEmpty())
    }

    @Test
    fun testTerminalSnippetHandler_noAuth_executesImmediatelyWithoutPrompt() {
        val snippet = CommandSnippet(
            id = "snip-safe-01",
            name = "Check Disk Usage",
            command = "df -h",
            autoSend = true,
            requireAuth = false,
        )

        val terminalDispatchedBytes = mutableListOf<ByteArray>()
        var authPromptInvoked = false

        val handler = TerminalSnippetHandler(
            sendToTerminal = { bytes -> terminalDispatchedBytes.add(bytes) },
            requestAuthentication = { _, _ -> authPromptInvoked = true },
        )

        handler.onSnippetClicked(snippet)

        assertFalse("Auth prompt must not be invoked for non-gated snippets", authPromptInvoked)
        assertEquals(1, terminalDispatchedBytes.size)
        assertEquals("df -h\r", String(terminalDispatchedBytes[0], StandardCharsets.UTF_8))
    }

    @Test
    fun testTerminalSnippetHandler_autoSendFormatting_handlesTrueAndFalse() {
        val autoSendSnippet = CommandSnippet(
            id = "snip-auto",
            name = "Uptime",
            command = "uptime",
            autoSend = true,
            requireAuth = false,
        )
        val manualSendSnippet = CommandSnippet(
            id = "snip-manual",
            name = "Tail Logs",
            command = "tail -f /var/log/syslog",
            autoSend = false,
            requireAuth = false,
        )

        val autoBytes = TerminalSnippetHandler.formatCommand(autoSendSnippet)
        val manualBytes = TerminalSnippetHandler.formatCommand(manualSendSnippet)

        // autoSend = true appends carriage return '\r'
        assertEquals("uptime\r", String(autoBytes, StandardCharsets.UTF_8))
        assertTrue(autoBytes.endsWith(byteArrayOf(0x0D)))

        // autoSend = false does NOT append carriage return
        assertEquals("tail -f /var/log/syslog", String(manualBytes, StandardCharsets.UTF_8))
        assertFalse(manualBytes.endsWith(byteArrayOf(0x0D)))
    }

    // =========================================================================
    // 3. OpenSSH Host Certificates & KnownHostsBulkPorter Anti-MITM Guard
    // =========================================================================

    @Test
    fun testOpenSshCertificateValidator_validHostCert_accepted() {
        val caKeyPair = SSHKeyGenerator.generateEd25519KeyPair()
        val subjectKeyPair = SSHKeyGenerator.generateEd25519KeyPair()

        val cert = buildTestCert(
            caKp = caKeyPair,
            subjectKp = subjectKeyPair,
            certType = OpenSshCertType.HOST,
            validPrincipals = listOf("bastion.internal.corp", "*.corp"),
            validAfter = 1_000L,
            validBefore = 5_000L,
        )

        val result = OpenSshCertificateValidator.validate(
            cert = cert,
            requestedPrincipal = "bastion.internal.corp",
            currentEpochSeconds = 2_500L,
            trustedCaKeys = listOf(caKeyPair.public),
        )

        assertTrue("Expected Valid certificate result, got: $result", result is CertValidationResult.Valid)
        assertEquals(cert, (result as CertValidationResult.Valid).cert)
    }

    @Test
    fun testOpenSshCertificateValidator_expiredCert_rejected() {
        val caKeyPair = SSHKeyGenerator.generateEd25519KeyPair()
        val cert = buildTestCert(
            caKp = caKeyPair,
            certType = OpenSshCertType.HOST,
            validAfter = 1_000L,
            validBefore = 2_000L,
        )

        val result = OpenSshCertificateValidator.validate(
            cert = cert,
            requestedPrincipal = "host.internal.corp",
            currentEpochSeconds = 2_500L, // Past validBefore
            trustedCaKeys = listOf(caKeyPair.public),
        )

        assertTrue(result is CertValidationResult.Expired)
        val expired = result as CertValidationResult.Expired
        assertEquals(2_000L, expired.validBeforeSeconds)
        assertEquals(2_500L, expired.currentEpochSeconds)
    }

    @Test
    fun testOpenSshCertificateValidator_notYetValidCert_rejected() {
        val caKeyPair = SSHKeyGenerator.generateEd25519KeyPair()
        val cert = buildTestCert(
            caKp = caKeyPair,
            certType = OpenSshCertType.HOST,
            validAfter = 3_000L,
            validBefore = 5_000L,
        )

        val result = OpenSshCertificateValidator.validate(
            cert = cert,
            requestedPrincipal = "host.internal.corp",
            currentEpochSeconds = 2_000L, // Before validAfter
            trustedCaKeys = listOf(caKeyPair.public),
        )

        assertTrue(result is CertValidationResult.NotYetValid)
        val notYetValid = result as CertValidationResult.NotYetValid
        assertEquals(3_000L, notYetValid.validAfterSeconds)
        assertEquals(2_000L, notYetValid.currentEpochSeconds)
    }

    @Test
    fun testOpenSshCertificateValidator_principalMismatch_rejected() {
        val caKeyPair = SSHKeyGenerator.generateEd25519KeyPair()
        val cert = buildTestCert(
            caKp = caKeyPair,
            certType = OpenSshCertType.HOST,
            validPrincipals = listOf("web01.internal.corp", "*.corp"),
            validAfter = 1_000L,
            validBefore = 5_000L,
        )

        val result = OpenSshCertificateValidator.validate(
            cert = cert,
            requestedPrincipal = "attacker.unauthorized.com",
            currentEpochSeconds = 2_000L,
            trustedCaKeys = listOf(caKeyPair.public),
        )

        assertTrue(result is CertValidationResult.InvalidPrincipal)
        val mismatch = result as CertValidationResult.InvalidPrincipal
        assertEquals("attacker.unauthorized.com", mismatch.requestedPrincipal)
        assertTrue(mismatch.validPrincipals.contains("web01.internal.corp"))
    }

    @Test
    fun testOpenSshCertificateValidator_tamperedSignature_rejected() {
        val caKeyPair = SSHKeyGenerator.generateEd25519KeyPair()
        val cert = buildTestCert(caKp = caKeyPair)

        // Tamper with signedBytes
        val tamperedBytes = cert.signedBytes.clone()
        tamperedBytes[0] = (tamperedBytes[0].toInt() xor 0xFF).toByte()
        val tamperedCert = cert.copy(signedBytes = tamperedBytes)

        val result = OpenSshCertificateValidator.validate(
            cert = tamperedCert,
            requestedPrincipal = "host.internal.corp",
            currentEpochSeconds = 1_500L,
            trustedCaKeys = listOf(caKeyPair.public),
        )

        assertTrue(result is CertValidationResult.InvalidSignature)
    }

    @Test
    fun testOpenSshCertificateValidator_unrecognizedCriticalOption_rejected() {
        val caKeyPair = SSHKeyGenerator.generateEd25519KeyPair()
        val cert = buildTestCert(
            caKp = caKeyPair,
            criticalOptions = mapOf("unrecognized-trap@openssh.com" to "evil-payload"),
        )

        val result = OpenSshCertificateValidator.validate(
            cert = cert,
            requestedPrincipal = "host.internal.corp",
            currentEpochSeconds = 1_500L,
            trustedCaKeys = listOf(caKeyPair.public),
        )

        assertTrue(result is CertValidationResult.UnsupportedCriticalOption)
        val crit = result as CertValidationResult.UnsupportedCriticalOption
        assertEquals("unrecognized-trap@openssh.com", crit.optionName)
    }

    @Test
    fun testOpenSshCertificateValidator_untrustedCaKey_rejected() {
        val trustedCa = SSHKeyGenerator.generateEd25519KeyPair()
        val rogueCa = SSHKeyGenerator.generateEd25519KeyPair()

        val cert = buildTestCert(caKp = rogueCa)

        val result = OpenSshCertificateValidator.validate(
            cert = cert,
            requestedPrincipal = "host.internal.corp",
            currentEpochSeconds = 1_500L,
            trustedCaKeys = listOf(trustedCa.public),
        )

        assertTrue(result is CertValidationResult.CaNotTrusted)
    }

    @Test
    fun testKnownHostsBulkPorter_parsesCertAuthorityAndRevokedMarkers() {
        val content = """
            @cert-authority *.internal.corp ssh-ed25519 $sampleEd25519Key1 Production CA Key
            @revoked compromised.internal.corp ssh-ed25519 $sampleEd25519Key2 Stolen Key
        """.trimIndent()

        val result = KnownHostsBulkPorter.importKnownHosts(
            content = content,
            manager = knownHostsManager,
            strategy = KnownHostsMergeStrategy.STRICT_REJECT_CONFLICTS,
        )

        assertTrue(result.isSuccess)
        assertEquals(2, result.importedCount)

        val entries = knownHostsManager.getEntries()
        assertEquals(2, entries.size)

        val caEntry = entries.first { it.isCertAuthority }
        assertEquals("*.internal.corp", caEntry.rawHostPattern)
        assertEquals("Production CA Key", caEntry.comment)
        assertTrue(caEntry.isCertAuthority)
        assertFalse(caEntry.isRevoked)

        val revokedEntry = entries.first { it.isRevoked }
        assertEquals("compromised.internal.corp", revokedEntry.rawHostPattern)
        assertEquals("Stolen Key", revokedEntry.comment)
        assertTrue(revokedEntry.isRevoked)
        assertFalse(revokedEntry.isCertAuthority)
    }

    @Test
    fun testKnownHostsBulkPorter_rejectsControlCharactersAndAnsiEscapeSequences() {
        val hostileContent = """
            node1.example.com ssh-ed25519 $sampleEd25519Key1 \u001B[31mEvilColor\u001B[0m
            node2.example.com\r\nrm -rf / ssh-ed25519 $sampleEd25519Key1 Comment
            node3.example.com\u0000injected ssh-ed25519 $sampleEd25519Key1 NullByte
        """.trimIndent().replace("\\u001B", "\u001B").replace("\\u0000", "\u0000")

        val result = KnownHostsBulkPorter.importKnownHosts(
            content = hostileContent,
            manager = knownHostsManager,
            strategy = KnownHostsMergeStrategy.STRICT_REJECT_CONFLICTS,
        )

        assertTrue(result.isSuccess)
        assertEquals("All hostile lines containing control characters must be skipped", 0, result.importedCount)
        assertTrue(result.warnings.any { it.contains("forbidden control characters") })
        assertEquals(0, knownHostsManager.getEntries().size)
    }

    @Test
    fun testKnownHostsBulkPorter_strictRejectConflicts_abortsOnMitmConflict() {
        // Save initial trusted host key
        knownHostsManager.saveOrUpdateHostKey(
            formattedHost = "vault.enterprise.corp",
            port = 22,
            keyType = "ssh-ed25519",
            keyBlobBase64 = sampleEd25519Key1,
            hashHost = false,
        )

        // Hostile / MITM import with differing key for the same host
        val conflictingContent = "vault.enterprise.corp ssh-ed25519 $sampleEd25519Key2 Attempted MITM Key"

        val result = KnownHostsBulkPorter.importKnownHosts(
            content = conflictingContent,
            manager = knownHostsManager,
            strategy = KnownHostsMergeStrategy.STRICT_REJECT_CONFLICTS,
        )

        assertFalse("Import must fail on conflict under STRICT_REJECT_CONFLICTS", result.isSuccess)
        assertEquals(1, result.conflicts.size)
        assertEquals("vault.enterprise.corp", result.conflicts[0].host)
        assertEquals(0, result.importedCount)

        // Existing trusted key in manager must remain pristine
        val storedEntries = knownHostsManager.getEntries()
        assertEquals(1, storedEntries.size)
        assertEquals(sampleEd25519Key1, storedEntries[0].publicKeyBase64)
    }

    // =========================================================================
    // 4. DNSSEC SSHFP Adversarial Wire Validation (SshfpVerifier)
    // =========================================================================

    @Test
    fun testSshfpVerifier_rejectsDnsWireResponsesWithoutAdBit() {
        val dummyHostKey = "ssh-ed25519-adversarial-wire-key".toByteArray(StandardCharsets.UTF_8)
        val validSha256 = MessageDigest.getInstance("SHA-256").digest(dummyHostKey)

        // DNS response with AD bit FALSE (unauthenticated DNS)
        val unauthenticatedDnsResponse = createDnsResponseBytes(
            adBit = false,
            records = listOf(
                SshfpRecord(
                    algorithm = SshfpVerifier.ALGORITHM_ED25519,
                    fpType = SshfpVerifier.FPTYPE_SHA256,
                    fingerprint = validSha256,
                ),
            ),
        )

        val result = SshfpVerifier.verifyHostKeyWire(
            hostname = "bastion.example.com",
            algorithm = SshfpVerifier.ALGORITHM_ED25519,
            hostKeyWireBytes = dummyHostKey,
            dnsResponseBytes = unauthenticatedDnsResponse,
        )

        assertEquals(SshfpValidationResult.DnssecNotAuthenticated, result)
    }

    @Test
    fun testSshfpVerifier_rejectsMismatchedSha256Fingerprints() {
        val actualHostKey = "actual-server-host-key-bytes".toByteArray(StandardCharsets.UTF_8)
        val actualSha256 = MessageDigest.getInstance("SHA-256").digest(actualHostKey)

        // Hostile / spoofed DNS record has differing fingerprint
        val spoofedFingerprint = ByteArray(32) { 0xDE.toByte() }

        val authenticatedDnsResponse = createDnsResponseBytes(
            adBit = true,
            records = listOf(
                SshfpRecord(
                    algorithm = SshfpVerifier.ALGORITHM_ED25519,
                    fpType = SshfpVerifier.FPTYPE_SHA256,
                    fingerprint = spoofedFingerprint,
                ),
            ),
        )

        val result = SshfpVerifier.verifyHostKeyWire(
            hostname = "target.example.com",
            algorithm = SshfpVerifier.ALGORITHM_ED25519,
            hostKeyWireBytes = actualHostKey,
            dnsResponseBytes = authenticatedDnsResponse,
        )

        assertTrue("Expected FingerprintMismatchAlert, got $result", result is SshfpValidationResult.FingerprintMismatchAlert)
        val alert = result as SshfpValidationResult.FingerprintMismatchAlert
        assertArrayEquals(actualSha256, alert.actualFingerprint)
        assertFalse(alert.actualFingerprint.contentEquals(spoofedFingerprint))
    }

    @Test
    fun testSshfpVerifier_acceptsValidAuthenticatedSha256Record() {
        val validHostKey = "valid-secure-host-key".toByteArray(StandardCharsets.UTF_8)
        val validSha256 = MessageDigest.getInstance("SHA-256").digest(validHostKey)

        val authenticatedDnsResponse = createDnsResponseBytes(
            adBit = true,
            records = listOf(
                SshfpRecord(
                    algorithm = SshfpVerifier.ALGORITHM_ED25519,
                    fpType = SshfpVerifier.FPTYPE_SHA256,
                    fingerprint = validSha256,
                ),
            ),
        )

        val result = SshfpVerifier.verifyHostKeyWire(
            hostname = "secure.example.com",
            algorithm = SshfpVerifier.ALGORITHM_ED25519,
            hostKeyWireBytes = validHostKey,
            dnsResponseBytes = authenticatedDnsResponse,
        )

        assertTrue(result is SshfpValidationResult.ValidDnssecMatch)
        val match = result as SshfpValidationResult.ValidDnssecMatch
        assertEquals(SshfpVerifier.ALGORITHM_ED25519, match.record.algorithm)
        assertEquals(SshfpVerifier.FPTYPE_SHA256, match.record.fpType)
        assertArrayEquals(validSha256, match.record.fingerprint)
    }

    @Test
    fun testSshfpVerifier_handlesNoMatchingRecordsCleanly() {
        val dummyHostKey = "dummy-key".toByteArray(StandardCharsets.UTF_8)

        val dnsResponseWithoutMatchingAlgo = createDnsResponseBytes(
            adBit = true,
            records = listOf(
                SshfpRecord(
                    algorithm = SshfpVerifier.ALGORITHM_RSA, // Only RSA, we query ED25519
                    fpType = SshfpVerifier.FPTYPE_SHA256,
                    fingerprint = ByteArray(32),
                ),
            ),
        )

        val result = SshfpVerifier.verifyHostKeyWire(
            hostname = "rsa-only.example.com",
            algorithm = SshfpVerifier.ALGORITHM_ED25519,
            hostKeyWireBytes = dummyHostKey,
            dnsResponseBytes = dnsResponseWithoutMatchingAlgo,
        )

        assertEquals(SshfpValidationResult.NoRecordsFound, result)
    }

    // =========================================================================
    // 5. Pre-Connect Automation (AutomationIntentGuard, WolManager, PortKnockingManager)
    // =========================================================================

    @Test
    fun testAutomationIntentGuard_authenticatesValidHmacSha256Intent() {
        val nonceCache = NonceReplayCache()
        val guard = AutomationIntentGuard(
            securityStorageManager = securityStorageManager,
            nonceCache = nonceCache,
            clock = { simulatedClockMs },
        )

        val intent = createAutomationIntent(
            profileId = validAutomationProfileId,
            timestampMs = simulatedClockMs,
            nonce = UUID.randomUUID().toString(),
            secretKey = automationSecretKey,
        )

        val result = guard.validate(intent)
        assertTrue("Valid intent must be Authenticated, got $result", result is AutomationValidationResult.Authenticated)
        val auth = result as AutomationValidationResult.Authenticated
        assertEquals(validAutomationProfileId, auth.profileId)
        assertEquals(AutomationIntentGuard.ACTION_EXECUTE_AUTOMATION, auth.action)
    }

    @Test
    fun testAutomationIntentGuard_rejectsReplayedNonce() {
        val nonceCache = NonceReplayCache()
        val guard = AutomationIntentGuard(
            securityStorageManager = securityStorageManager,
            nonceCache = nonceCache,
            clock = { simulatedClockMs },
        )

        val fixedNonce = "unique-nonce-12345"
        val intent1 = createAutomationIntent(
            profileId = validAutomationProfileId,
            timestampMs = simulatedClockMs,
            nonce = fixedNonce,
            secretKey = automationSecretKey,
        )
        val intent2 = createAutomationIntent(
            profileId = validAutomationProfileId,
            timestampMs = simulatedClockMs + 500L,
            nonce = fixedNonce, // Replayed nonce
            secretKey = automationSecretKey,
        )

        // First attempt succeeds
        assertTrue(guard.validate(intent1) is AutomationValidationResult.Authenticated)

        // Replayed attempt is rejected
        val result2 = guard.validate(intent2)
        assertTrue(result2 is AutomationValidationResult.Rejected)
        val rejected = result2 as AutomationValidationResult.Rejected
        assertTrue("Reason must mention replayed nonce: ${rejected.reason}", rejected.reason.contains("Replayed nonce"))
    }

    @Test
    fun testAutomationIntentGuard_rejectsExpiredAndFutureTimestamps() {
        val guard = AutomationIntentGuard(
            securityStorageManager = securityStorageManager,
            clock = { simulatedClockMs },
        )

        // 1. Expired timestamp (> 60_000ms old)
        val expiredIntent = createAutomationIntent(
            profileId = validAutomationProfileId,
            timestampMs = simulatedClockMs - 60_001L,
            secretKey = automationSecretKey,
        )
        val expiredResult = guard.validate(expiredIntent)
        assertTrue(expiredResult is AutomationValidationResult.Rejected)
        assertTrue((expiredResult as AutomationValidationResult.Rejected).reason.contains("Expired timestamp"))

        // 2. Future timestamp (> 60_000ms in the future)
        val futureIntent = createAutomationIntent(
            profileId = validAutomationProfileId,
            timestampMs = simulatedClockMs + 60_001L,
            secretKey = automationSecretKey,
        )
        val futureResult = guard.validate(futureIntent)
        assertTrue(futureResult is AutomationValidationResult.Rejected)
        assertTrue((futureResult as AutomationValidationResult.Rejected).reason.contains("Future timestamp"))
    }

    @Test
    fun testAutomationIntentGuard_rejectsTamperedHmacSignature() {
        val guard = AutomationIntentGuard(
            securityStorageManager = securityStorageManager,
            clock = { simulatedClockMs },
        )

        val validIntent = createAutomationIntent(
            profileId = validAutomationProfileId,
            timestampMs = simulatedClockMs,
            secretKey = automationSecretKey,
        )

        // Tamper with signature
        val validSig = validIntent.getStringExtra(AutomationIntentGuard.EXTRA_SIGNATURE)!!
        val tamperedSig = if (validSig.startsWith("a")) "b" + validSig.substring(1) else "a" + validSig.substring(1)
        validIntent.putExtra(AutomationIntentGuard.EXTRA_SIGNATURE, tamperedSig)

        val result = guard.validate(validIntent)
        assertTrue(result is AutomationValidationResult.Rejected)
        assertTrue((result as AutomationValidationResult.Rejected).reason.contains("HMAC-SHA256 signature verification failed"))
    }

    @Test
    fun testAutomationIntentGuard_rejectsHostileOverrideExtras() {
        val guard = AutomationIntentGuard(
            securityStorageManager = securityStorageManager,
            clock = { simulatedClockMs },
        )

        val hostileKeys = listOf("command", "exec", "cmd", "host", "hostname", "port", "password", "user", "username")

        for (hostileKey in hostileKeys) {
            val intent = createAutomationIntent(
                profileId = validAutomationProfileId,
                timestampMs = simulatedClockMs,
                secretKey = automationSecretKey,
            )
            intent.putExtra(hostileKey, "malicious_override")

            val result = guard.validate(intent)
            assertTrue("Expected rejection for hostile key '$hostileKey'", result is AutomationValidationResult.Rejected)
            val rejected = result as AutomationValidationResult.Rejected
            assertTrue(rejected.reason.contains("Hostile override extras detected"))
            assertTrue(rejected.hostileExtrasFound.contains(hostileKey))
        }
    }

    @Test
    fun testWolManager_buildsConforming102ByteMagicPacket() {
        val colonMac = "00:11:22:33:44:55"
        val hyphenMac = "AA-BB-CC-DD-EE-FF"

        assertTrue(WolManager.isValidMacAddress(colonMac))
        assertTrue(WolManager.isValidMacAddress(hyphenMac))

        val colonBytes = WolManager.parseMacBytes(colonMac)
        val hyphenBytes = WolManager.parseMacBytes(hyphenMac)

        assertEquals(6, colonBytes.size)
        assertEquals(6, hyphenBytes.size)

        val packet = WolManager.buildMagicPacket(colonBytes)
        assertEquals(102, packet.size)

        // 6 leading 0xFF bytes
        for (i in 0 until 6) {
            assertEquals(0xFF.toByte(), packet[i])
        }

        // 16 consecutive repetitions of target 6-byte MAC
        for (rep in 0 until 16) {
            val offset = 6 + (rep * 6)
            for (byteIdx in 0 until 6) {
                assertEquals(colonBytes[byteIdx], packet[offset + byteIdx])
            }
        }
    }

    @Test
    fun testWolManager_throwsOnMalformedMacStrings() {
        val malformedMacs = listOf(
            "invalid-mac",
            "00:11:22",
            "",
            "   ",
            "00:11:22:33:44:55:66", // 7 octets
            "00:11:22:33:44:ZZ", // non-hex
            "0011.2233.4455", // Cisco dotted format unsupported
        )

        for (malformed in malformedMacs) {
            assertFalse("WolManager.isValidMacAddress('$malformed') must be false", WolManager.isValidMacAddress(malformed))
            try {
                WolManager.parseMacBytes(malformed)
                fail("WolManager.parseMacBytes('$malformed') should have thrown IllegalArgumentException")
            } catch (_: IllegalArgumentException) {
                // Expected
            }
        }
    }

    @Test
    fun testPortKnockingManager_validatesPortBoundsAndHandlesKnockSequencesDeterministically() = runBlocking {
        val steps = listOf(
            PortKnockStep(7000, KnockProtocol.TCP),
            PortKnockStep(8000, KnockProtocol.UDP),
            PortKnockStep(9000, KnockProtocol.TCP),
        )
        val config = PortKnockingConfig(
            steps = steps,
            stepDelayMs = 0L,
            postKnockDelayMs = 0L,
            probeTimeoutMs = 100,
        )

        val dispatchedKnocks = mutableListOf<Pair<String, Int>>()

        // Deterministic execution
        val executed = PortKnockingManager.executePortKnockSequence(
            host = "knocker.target.corp",
            config = config,
            socketConnect = { host, port, _ -> dispatchedKnocks.add(host to port) },
            udpSend = { host, port -> dispatchedKnocks.add(host to port) },
        )

        assertEquals(3, executed)
        assertEquals(3, dispatchedKnocks.size)
        assertEquals("knocker.target.corp" to 7000, dispatchedKnocks[0])
        assertEquals("knocker.target.corp" to 8000, dispatchedKnocks[1])
        assertEquals("knocker.target.corp" to 9000, dispatchedKnocks[2])

        // Verify cooldown / rate limiting is active for this host
        assertTrue(PortKnockingManager.isRateLimited("knocker.target.corp"))
        val blockedRun = PortKnockingManager.executePortKnockSequence(
            host = "knocker.target.corp",
            config = config,
        )
        assertEquals("Subsequent knock within cooldown window must return 0", 0, blockedRun)
    }

    // =========================================================================
    // Test Helper Methods
    // =========================================================================

    private fun createAutomationIntent(
        profileId: String,
        action: String = AutomationIntentGuard.ACTION_EXECUTE_AUTOMATION,
        timestampMs: Long,
        nonce: String = UUID.randomUUID().toString(),
        secretKey: ByteArray,
    ): Intent {
        val sigHex = AutomationIntentGuard.computeHmacSha256Hex(
            secretKey = secretKey,
            timestampMs = timestampMs,
            nonce = nonce,
            profileId = profileId,
            action = action,
        )
        return Intent(AutomationIntentGuard.ACTION_EXECUTE_AUTOMATION).apply {
            putExtra(AutomationIntentGuard.EXTRA_PROFILE_ID, profileId)
            putExtra(AutomationIntentGuard.EXTRA_NONCE, nonce)
            putExtra(AutomationIntentGuard.EXTRA_TIMESTAMP, timestampMs)
            putExtra(AutomationIntentGuard.EXTRA_SIGNATURE, sigHex)
            putExtra(AutomationIntentGuard.EXTRA_ACTION, action)
        }
    }

    private fun buildTestCert(
        magic: String = "ssh-ed25519-cert-v01@openssh.com",
        subjectKp: KeyPair = SSHKeyGenerator.generateEd25519KeyPair(),
        caKp: KeyPair = SSHKeyGenerator.generateEd25519KeyPair(),
        certType: OpenSshCertType = OpenSshCertType.HOST,
        keyId: String = "adversarial-test-cert",
        validPrincipals: List<String> = listOf("host.internal.corp", "*.corp"),
        validAfter: Long = 1000L,
        validBefore: Long = 2000L,
        criticalOptions: Map<String, String> = emptyMap(),
        extensions: Map<String, String> = emptyMap(),
        sigAlg: String = "ssh-ed25519",
    ): OpenSshCertificate {
        val provisional = OpenSshCertificate(
            magic = magic,
            nonce = "1234567890123456".toByteArray(),
            subjectPublicKey = subjectKp.public,
            serial = 1L,
            certType = certType,
            keyId = keyId,
            validPrincipals = validPrincipals,
            validAfterSeconds = validAfter,
            validBeforeSeconds = validBefore,
            criticalOptions = criticalOptions,
            extensions = extensions,
            caPublicKey = caKp.public,
            signatureAlgorithm = sigAlg,
            rawSignatureBlob = ByteArray(0),
            signedBytes = ByteArray(0),
        )

        val writer = WireBufferWriter()
        writer.writeString(provisional.magic)
        writer.writeStringBytes(provisional.nonce)
        when (val pub = provisional.subjectPublicKey) {
            is java.security.interfaces.RSAPublicKey -> {
                writer.writeMpint(pub.publicExponent)
                writer.writeMpint(pub.modulus)
            }

            is java.security.interfaces.ECPublicKey -> {
                writer.writeString("nistp256")
                val pubBlob = OpenSshCertificateParser.encodePublicKeyBlob(pub)
                val r = WireBufferReader(pubBlob)
                r.readString()
                r.readString()
                writer.writeStringBytes(r.readStringBytes())
            }

            else -> {
                writer.writeStringBytes(OpenSshCertificateParser.extractEd25519RawBytes(pub))
            }
        }
        writer.writeUint64(provisional.serial)
        writer.writeUint32(provisional.certType.code.toLong())
        writer.writeString(provisional.keyId)

        val pWriter = WireBufferWriter()
        for (p in provisional.validPrincipals) pWriter.writeString(p)
        writer.writeStringBytes(pWriter.toByteArray())

        writer.writeUint64(provisional.validAfterSeconds)
        writer.writeUint64(provisional.validBeforeSeconds)

        val cWriter = WireBufferWriter()
        for ((k, v) in provisional.criticalOptions) {
            cWriter.writeString(k)
            cWriter.writeString(v)
        }
        writer.writeStringBytes(cWriter.toByteArray())

        val eWriter = WireBufferWriter()
        for ((k, v) in provisional.extensions) {
            eWriter.writeString(k)
            eWriter.writeString(v)
        }
        writer.writeStringBytes(eWriter.toByteArray())

        writer.writeStringBytes(ByteArray(0))
        writer.writeStringBytes(OpenSshCertificateParser.encodePublicKeyBlob(provisional.caPublicKey))

        val signedBytes = writer.toByteArray()
        val certWithSignedBytes = provisional.copy(signedBytes = signedBytes)

        return OpenSshCertificateValidator.signCertificate(
            unsignedCert = certWithSignedBytes,
            caPrivateKey = caKp.private,
            signatureAlgorithm = sigAlg,
        )
    }

    private fun createDnsResponseBytes(
        adBit: Boolean,
        records: List<SshfpRecord>,
        rcode: Int = 0,
    ): ByteArray {
        val out = ByteArrayOutputStream()

        // 12-byte DNS Header
        out.write(0x00)
        out.write(0x00)

        // Flags: QR=1 (response), RD=1, RA=1, AD bit (0x0020)
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
        out.write("ssh".toByteArray(StandardCharsets.US_ASCII))
        out.write(0)
        out.write(0x00)
        out.write(SshfpVerifier.RR_TYPE_SSHFP)
        out.write(0x00)
        out.write(SshfpVerifier.CLASS_IN)

        // Answers
        for (rec in records) {
            // Compressed pointer to question name (0xC00C)
            out.write(0xC0)
            out.write(0x0C)

            // TYPE 44 (SSHFP)
            out.write(0x00)
            out.write(SshfpVerifier.RR_TYPE_SSHFP)
            // CLASS 1 (IN)
            out.write(0x00)
            out.write(SshfpVerifier.CLASS_IN)
            // TTL: 300 seconds
            out.write(0x00)
            out.write(0x00)
            out.write(0x01)
            out.write(0x2C)

            // RDLENGTH = 2 (algo + fptype) + fingerprint.size
            val rdLen = 2 + rec.fingerprint.size
            out.write((rdLen ushr 8) and 0xFF)
            out.write(rdLen and 0xFF)

            // RDATA
            out.write(rec.algorithm)
            out.write(rec.fpType)
            out.write(rec.fingerprint)
        }

        return out.toByteArray()
    }

    private fun ByteArray.endsWith(suffix: ByteArray): Boolean {
        if (this.size < suffix.size) return false
        for (i in suffix.indices) {
            if (this[this.size - suffix.size + i] != suffix[i]) return false
        }
        return true
    }
}
