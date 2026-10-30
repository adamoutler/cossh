package com.adamoutler.ssh.security

import android.app.Application
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.billing.BillingManager
import com.adamoutler.ssh.crypto.KnownHostsManager
import com.adamoutler.ssh.ui.screens.KnownHostsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
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
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreReleasePrivacyAndOsLeakageTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var clipboardManager: ClipboardManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.clearPrimaryClip()
        BillingManager.forceCloudSyncEnabledForTest = false
        context.getSharedPreferences("BillingPrefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        BillingManager.forceCloudSyncEnabledForTest = false
        context.getSharedPreferences("BillingPrefs", Context.MODE_PRIVATE).edit().clear().commit()
        SecureClipboardManager.clearClipboard(context)
    }

    // --- Clipboard Scrubbing & Sensitive Extras Tests ---

    @Test
    fun testSecureClipboardScrubbing_lastCopiedTextScrubbedOnAutoClear() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        SecureClipboardManager.copyToClipboard(
            context = context,
            text = "sensitive_passphrase_to_scrub",
            label = "Secret",
            scope = testScope,
            autoClearDelayMs = 30_000L,
        )

        assertEquals("sensitive_passphrase_to_scrub", SecureClipboardManager.getLastCopiedTextForTest())
        assertEquals("sensitive_passphrase_to_scrub", clipboardManager.primaryClip?.getItemAt(0)?.text?.toString())

        // Fast-forward past 30 seconds
        testScope.advanceTimeBy(30_001L)
        testScope.runCurrent()

        // Internal reference must be scrubbed to null to prevent in-memory secret retention
        assertNull("lastCopiedText must be scrubbed to null after auto-clear timeout", SecureClipboardManager.getLastCopiedTextForTest())
        val clipAfter = clipboardManager.primaryClip
        assertTrue(
            "Clipboard primary clip must be cleared after auto-clear timeout",
            clipAfter == null || clipAfter.itemCount == 0 || clipAfter.getItemAt(0).text.isNullOrEmpty(),
        )
    }

    @Test
    fun testSecureClipboardManualClear_scrubsLastCopiedTextAndCancelsTimer() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        SecureClipboardManager.copyToClipboard(
            context = context,
            text = "secret_for_manual_clear",
            scope = testScope,
            autoClearDelayMs = 30_000L,
        )
        assertEquals("secret_for_manual_clear", SecureClipboardManager.getLastCopiedTextForTest())

        SecureClipboardManager.clearClipboard(context)

        assertNull("lastCopiedText must be null after manual clear", SecureClipboardManager.getLastCopiedTextForTest())
        assertNull("autoClearJob must be null after manual clear", SecureClipboardManager.autoClearJob)
    }

    @Test
    fun testKnownHostsViewModel_routesFingerprintCopyThroughSecureClipboardManager() = runTest {
        val knownHostsFile = tempFolder.newFile("ssh_known_hosts")
        val knownHostsManager = KnownHostsManager(knownHostsFile)
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = KnownHostsViewModel(app, knownHostsManager)

        val fingerprint = "SHA256:7mP0V8xYzQ2kLbO1vWjR9sA6pC4uE8nH2yZ3iK5oQ1g"
        viewModel.copyFingerprintToClipboard(fingerprint)

        val clip = clipboardManager.primaryClip
        assertNotNull("Primary clip must exist", clip)
        assertEquals(fingerprint, clip!!.getItemAt(0).text.toString())

        // Validate sensitive flag on API 33+
        val extras = clip.description.extras
        assertNotNull("Extras bundle must not be null on API 34", extras)
        assertTrue("EXTRA_IS_SENSITIVE must be true", extras!!.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE))

        // Validate SecureClipboardManager tracked this copy
        assertEquals(fingerprint, SecureClipboardManager.getLastCopiedTextForTest())
    }

    // --- Backup & Extraction Rules Audit Tests ---

    @Test
    fun testBackupRules_excludesBillingPrefsSecurityAuditAndIdentities() {
        val backupRulesFile = findConfigFile("backup_rules.xml")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(backupRulesFile)
        val root = doc.documentElement
        assertEquals("full-backup-content", root.tagName)

        val excludes = root.getElementsByTagName("exclude")
        val excludeList = mutableListOf<Pair<String, String>>()
        for (i in 0 until excludes.length) {
            val element = excludes.item(i) as Element
            excludeList.add(element.getAttribute("domain") to element.getAttribute("path"))
        }

        assertTrue("Must exclude BillingPrefs.xml sharedpref", excludeList.contains("sharedpref" to "BillingPrefs.xml"))
        assertTrue("Must exclude security_audit_anchor_prefs.xml sharedpref", excludeList.contains("sharedpref" to "security_audit_anchor_prefs.xml"))
        assertTrue("Must exclude security_audit file directory", excludeList.contains("file" to "security_audit"))
        assertTrue("Must exclude secret_ssh_profiles.xml", excludeList.contains("sharedpref" to "secret_ssh_profiles.xml"))
        assertTrue("Must exclude secret_ssh_identities.xml", excludeList.contains("sharedpref" to "secret_ssh_identities.xml"))
        assertTrue("Must exclude ssh_known_hosts file", excludeList.contains("file" to "ssh_known_hosts"))
        assertTrue("Must exclude crypto_prefs.xml", excludeList.contains("sharedpref" to "crypto_prefs.xml"))
        assertTrue("Must exclude secure_crashes file", excludeList.contains("file" to "secure_crashes"))
    }

    @Test
    fun testDataExtractionRules_excludesSensitiveDataInBothCloudAndDeviceTransfer() {
        val rulesFile = findConfigFile("data_extraction_rules.xml")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(rulesFile)
        val root = doc.documentElement
        assertEquals("data-extraction-rules", root.tagName)

        // Verify cloud-backup exclusions
        val cloudBackupNodes = root.getElementsByTagName("cloud-backup")
        assertEquals("Exactly one cloud-backup node required", 1, cloudBackupNodes.length)
        val cloudElement = cloudBackupNodes.item(0) as Element
        val cloudExcludes = extractExcludes(cloudElement)

        assertTrue("cloud-backup must exclude BillingPrefs.xml", cloudExcludes.contains("sharedpref" to "BillingPrefs.xml"))
        assertTrue("cloud-backup must exclude security_audit_anchor_prefs.xml", cloudExcludes.contains("sharedpref" to "security_audit_anchor_prefs.xml"))
        assertTrue("cloud-backup must exclude security_audit file", cloudExcludes.contains("file" to "security_audit"))
        assertTrue("cloud-backup must exclude secret_ssh_profiles.xml", cloudExcludes.contains("sharedpref" to "secret_ssh_profiles.xml"))
        assertTrue("cloud-backup must exclude secret_ssh_identities.xml", cloudExcludes.contains("sharedpref" to "secret_ssh_identities.xml"))
        assertTrue("cloud-backup must exclude ssh_known_hosts", cloudExcludes.contains("file" to "ssh_known_hosts"))
        assertTrue("cloud-backup must exclude crypto_prefs.xml", cloudExcludes.contains("sharedpref" to "crypto_prefs.xml"))
        assertTrue("cloud-backup must exclude secure_crashes", cloudExcludes.contains("file" to "secure_crashes"))

        // Verify device-transfer exclusions
        val deviceTransferNodes = root.getElementsByTagName("device-transfer")
        assertEquals("Exactly one device-transfer node required", 1, deviceTransferNodes.length)
        val deviceElement = deviceTransferNodes.item(0) as Element
        val deviceExcludes = extractExcludes(deviceElement)

        assertTrue("device-transfer must exclude BillingPrefs.xml", deviceExcludes.contains("sharedpref" to "BillingPrefs.xml"))
        assertTrue("device-transfer must exclude security_audit_anchor_prefs.xml", deviceExcludes.contains("sharedpref" to "security_audit_anchor_prefs.xml"))
        assertTrue("device-transfer must exclude security_audit file", deviceExcludes.contains("file" to "security_audit"))
        assertTrue("device-transfer must exclude secret_ssh_profiles.xml", deviceExcludes.contains("sharedpref" to "secret_ssh_profiles.xml"))
        assertTrue("device-transfer must exclude secret_ssh_identities.xml", deviceExcludes.contains("sharedpref" to "secret_ssh_identities.xml"))
        assertTrue("device-transfer must exclude ssh_known_hosts", deviceExcludes.contains("file" to "ssh_known_hosts"))
        assertTrue("device-transfer must exclude crypto_prefs.xml", deviceExcludes.contains("sharedpref" to "crypto_prefs.xml"))
        assertTrue("device-transfer must exclude secure_crashes", deviceExcludes.contains("file" to "secure_crashes"))
    }

    // --- Billing Entitlement Integrity & Tamper Guard Tests ---

    @Test
    fun testBillingEntitlementTamperGuard_persistEntitlementGeneratesValidHmacAndPasses() {
        val billingManager = BillingManager(context)
        billingManager.persistEntitlement()

        val prefs = context.getSharedPreferences("BillingPrefs", Context.MODE_PRIVATE)
        assertTrue("cloud_sync_entitled must be true in prefs", prefs.getBoolean(BillingManager.PREF_KEY_ENTITLED, false))

        val hmac = prefs.getString(BillingManager.PREF_KEY_ENTITLED_HMAC, null)
        assertNotNull("cloud_sync_entitled_hmac must be persisted", hmac)
        val expectedHmac = BillingManager.computeEntitlementHmac(context.packageName)
        assertEquals("Persisted HMAC must match computed HMAC", expectedHmac, hmac)

        assertTrue("Entitlement integrity must be verified", billingManager.verifyEntitlementIntegrity())
    }

    @Test
    fun testBillingEntitlementTamperGuard_unverifiedFlagWithoutHmacFailsIntegrity() {
        val prefs = context.getSharedPreferences("BillingPrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(BillingManager.PREF_KEY_ENTITLED, true)
            .remove(BillingManager.PREF_KEY_ENTITLED_HMAC)
            .commit()

        val billingManager = BillingManager(context)
        assertFalse("Tampered entitlement flag without HMAC tag must fail integrity verification", billingManager.verifyEntitlementIntegrity())
    }

    @Test
    fun testBillingEntitlementTamperGuard_tamperedHmacTagFailsIntegrity() {
        val prefs = context.getSharedPreferences("BillingPrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(BillingManager.PREF_KEY_ENTITLED, true)
            .putString(BillingManager.PREF_KEY_ENTITLED_HMAC, "forged_entitlement_signature_bad_tag_deadbeef")
            .commit()

        val billingManager = BillingManager(context)
        assertFalse("Tampered entitlement HMAC must fail verification", billingManager.verifyEntitlementIntegrity())
    }

    @Test
    fun testBillingEntitlementTamperGuard_unentitledStateReturnsFalse() {
        val billingManager = BillingManager(context)
        assertFalse("Unentitled state must return false", billingManager.verifyEntitlementIntegrity())
    }

    @Test
    fun testBillingEntitlementTamperGuard_forceCloudSyncEnabledForTestBypasses() {
        BillingManager.forceCloudSyncEnabledForTest = true
        val billingManager = BillingManager(context)
        assertTrue("Test override flag must permit verification", billingManager.verifyEntitlementIntegrity())
    }

    // --- Helper Methods ---

    private fun findConfigFile(fileName: String): File {
        val candidates = listOf(
            File("app/src/main/res/xml/$fileName"),
            File("src/main/res/xml/$fileName"),
            File("../app/src/main/res/xml/$fileName"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Resource file $fileName not found in candidates: $candidates")
    }

    private fun extractExcludes(parent: Element): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        val children = parent.getElementsByTagName("exclude")
        for (i in 0 until children.length) {
            val element = children.item(i) as Element
            list.add(element.getAttribute("domain") to element.getAttribute("path"))
        }
        return list
    }
}
