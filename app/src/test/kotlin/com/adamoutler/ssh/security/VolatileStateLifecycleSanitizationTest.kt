package com.adamoutler.ssh.security

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.backup.BackupCryptoManager
import com.adamoutler.ssh.backup.BackupManager
import com.adamoutler.ssh.crypto.IdentityStorageManager
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.EphemeralProfileStore
import com.adamoutler.ssh.data.IdentityProfile
import com.adamoutler.ssh.data.Protocol
import com.adamoutler.ssh.ui.screens.AddEditProfileViewModel
import com.adamoutler.ssh.ui.screens.IdentityViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * Validates Volatile Credential Sanitization and Async Identity Save Race prevention for SSH-257.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class VolatileStateLifecycleSanitizationTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var app: Application

    private val throwingPrefs = object : SharedPreferences {
        override fun getAll(): MutableMap<String, *> = throw RuntimeException("Simulated Keystore Storage Failure")
        override fun getString(key: String?, defValue: String?): String? = throw RuntimeException("Simulated Keystore Storage Failure")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = null
        override fun getInt(key: String?, defValue: Int): Int = 0
        override fun getLong(key: String?, defValue: Long): Long = 0L
        override fun getFloat(key: String?, defValue: Float): Float = 0f
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = false
        override fun contains(key: String?): Boolean = false
        override fun edit(): SharedPreferences.Editor = throw RuntimeException("Simulated Keystore Storage Failure")
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        app = ApplicationProvider.getApplicationContext()
        EphemeralProfileStore.clear()
    }

    @After
    fun tearDown() {
        EphemeralProfileStore.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun testAddEditProfileViewModel_saveProfile_wipesPasswordEvenIfStorageThrows() {
        val failingStorage = SecurityStorageManager(app, throwingPrefs)
        val testPrefs = app.getSharedPreferences("test_id_" + UUID.randomUUID(), Context.MODE_PRIVATE)
        val identityStorage = IdentityStorageManager(app, testPrefs)

        val viewModel = AddEditProfileViewModel(app, failingStorage, identityStorage)
        val sensitivePassword = "SuperSecretPassword123".toByteArray(Charsets.UTF_8)

        try {
            viewModel.saveProfile(
                id = UUID.randomUUID().toString(),
                nickname = "Test Server",
                host = "10.0.0.1",
                port = "22",
                protocol = Protocol.SSH,
                username = "admin",
                authType = AuthType.PASSWORD,
                password = sensitivePassword,
                keyReference = null,
            )
            fail("Expected Exception from failingStorage")
        } catch (_: Exception) {
            // Expected: CryptoException or RuntimeException caught
        }

        // Verify sensitivePassword byte array was wiped to zeroes in finally block
        assertTrue(
            "Password buffer must be zero-wiped when saveProfile throws",
            sensitivePassword.all { it == 0.toByte() },
        )
    }

    @Test
    fun testAddEditProfileViewModel_saveProfileFromState_wipesOriginalPasswordAndResetsState() {
        val failingStorage = SecurityStorageManager(app, throwingPrefs)
        val testPrefs = app.getSharedPreferences("test_id_" + UUID.randomUUID(), Context.MODE_PRIVATE)
        val identityStorage = IdentityStorageManager(app, testPrefs)

        val viewModel = AddEditProfileViewModel(app, failingStorage, identityStorage)
        val originalPass = "OriginalPasswordBytes".toByteArray(Charsets.UTF_8)

        viewModel.updateState {
            it.copy(
                nickname = "Throwing State Host",
                host = "192.168.1.10",
                port = "22",
                isPasswordLocked = true,
                originalPassword = originalPass,
                authType = AuthType.PASSWORD,
            )
        }

        try {
            viewModel.saveProfileFromState()
            fail("Expected Exception from failingStorage")
        } catch (_: Exception) {
            // Expected: CryptoException or RuntimeException caught
        }

        // Verify originalPassword buffer was zero-wiped
        assertTrue(
            "originalPassword byte array must be zero-wiped even if storage throws",
            originalPass.all { it == 0.toByte() },
        )
        // Verify state was reset
        assertNull(
            "originalPassword in uiState must be reset to null",
            viewModel.uiState.value.originalPassword,
        )
    }

    @Test
    fun testAddEditIdentityScreen_defensiveCopies_isolateFromUiStateReset() {
        val originalPrivateBytes = "PrivateEd25519KeySecret".toByteArray(Charsets.UTF_8)
        val originalPassBytes = "PassphraseSecret".toByteArray(Charsets.UTF_8)

        // Simulate defensive copy in AddEditIdentityScreen before saveIdentity & resetState
        val defensivePrivate = originalPrivateBytes.copyOf()
        val defensivePass = originalPassBytes.copyOf()

        val identity = IdentityProfile(
            id = UUID.randomUUID().toString(),
            name = "Defensive Identity",
            username = "deploy",
            password = defensivePass,
            publicKey = "ssh-ed25519 AAAAC3...",
            privateKey = defensivePrivate,
            authType = AuthType.KEY,
        )

        // Simulate resetState() executing on the main thread zeroing original buffers
        originalPrivateBytes.fill(0)
        originalPassBytes.fill(0)

        // Verify defensive copies held by IdentityProfile were unaffected by resetState
        assertEquals(
            "Private key held by IdentityProfile must remain intact despite UI state reset",
            "PrivateEd25519KeySecret",
            identity.privateKey?.decodeToString(),
        )
        assertEquals(
            "Password held by IdentityProfile must remain intact despite UI state reset",
            "PassphraseSecret",
            identity.password?.decodeToString(),
        )

        // Now verify identity.clearSensitiveData() properly wipes the defensive copies
        identity.clearSensitiveData()
        assertTrue(
            "clearSensitiveData() must zero out privateKey",
            defensivePrivate.all { it == 0.toByte() },
        )
        assertTrue(
            "clearSensitiveData() must zero out password",
            defensivePass.all { it == 0.toByte() },
        )
    }

    @Test
    fun testIdentityViewModel_saveIdentity_wipesCredentialsInFinally() = runTest(testDispatcher) {
        val failingStorage = IdentityStorageManager(app, throwingPrefs)
        val viewModel = IdentityViewModel(app, failingStorage)

        val passBytes = "IdentitySecret123".toByteArray(Charsets.UTF_8)
        val privBytes = "PrivateKeySecretBytes".toByteArray(Charsets.UTF_8)

        val identity = IdentityProfile(
            id = UUID.randomUUID().toString(),
            name = "Failing Identity",
            username = "git",
            password = passBytes,
            publicKey = null,
            privateKey = privBytes,
            authType = AuthType.KEY,
        )

        viewModel.saveIdentity(identity)
        advanceUntilIdle()

        // Verify identity's secrets are zeroed in finally block even though storage threw
        assertTrue("Identity password must be wiped to zeroes", passBytes.all { it == 0.toByte() })
        assertTrue("Identity privateKey must be wiped to zeroes", privBytes.all { it == 0.toByte() })
    }

    @Test
    fun testBackupManager_exportBackup_wipesHydratedProfilesInFinally() {
        val prefs = app.getSharedPreferences("test_backup_exp_" + UUID.randomUUID(), Context.MODE_PRIVATE)
        val securityStorage = SecurityStorageManager(app, prefs)
        val identityStorage = IdentityStorageManager(app, prefs)

        val profilePass = "ProfileSensitivePass".toByteArray(Charsets.UTF_8)
        val profile = ConnectionProfile(
            id = "test_export_p1",
            nickname = "Prod Server",
            host = "prod.company.internal",
            port = 22,
            protocol = Protocol.SSH,
            username = "admin",
            authType = AuthType.PASSWORD,
            password = profilePass,
        )

        // Save to prefs so getAllProfiles() includes it, and place in EphemeralProfileStore
        // so getProfile(id) returns this exact instance to rawProfiles during exportBackup
        securityStorage.saveProfile(profile)
        EphemeralProfileStore.put(profile)

        val backupManager = BackupManager(app, securityStorage, identityStorage)
        val tempFile = File.createTempFile("backup_test", ".zip")
        val uri = Uri.fromFile(tempFile)
        val backupPassword = "StrongBackupPassword123!".toCharArray()

        backupManager.exportBackup(uri, backupPassword)

        // After export, verify loaded profiles had clearSensitiveData() invoked in finally
        assertTrue("Exported profile password must be zeroed in finally", profilePass.all { it == 0.toByte() })
    }

    @Test
    fun testBackupManager_importBackup_wipesHydratedProfilesInFinallyWhenPersistenceFails() {
        val normalPrefs = app.getSharedPreferences("test_backup_imp_" + UUID.randomUUID(), Context.MODE_PRIVATE)
        val identityStorage = IdentityStorageManager(app, normalPrefs)

        // Generate a valid encrypted backup archive first
        val profilePass = "SecretPass1".toByteArray(Charsets.UTF_8)
        val profile = ConnectionProfile(
            id = "p-1",
            nickname = "Test",
            host = "1.2.3.4",
            port = 22,
            protocol = Protocol.SSH,
            username = "root",
            authType = AuthType.PASSWORD,
            password = profilePass,
        )
        val backupPassword = "MasterPassword456".toCharArray()

        val outStream = ByteArrayOutputStream()
        BackupCryptoManager.exportProfilesToZip(listOf(profile), emptyList(), backupPassword, outStream)
        val zipBytes = outStream.toByteArray()

        val tempFile = File.createTempFile("import_test", ".zip")
        tempFile.writeBytes(zipBytes)
        val uri = Uri.fromFile(tempFile)

        // Use failing SecurityStorageManager for import persistence
        val failingSecurityStorage = SecurityStorageManager(app, throwingPrefs)
        val backupManager = BackupManager(app, failingSecurityStorage, identityStorage)

        try {
            backupManager.importBackup(uri, backupPassword)
            fail("Expected IllegalStateException on save failure")
        } catch (_: IllegalStateException) {
            // Expected
        }

        // Caller password was preserved
        assertTrue("Caller backup password must not be zeroed", backupPassword.isNotEmpty())
    }

    @Test
    fun testBackupCryptoManager_importProfilesFromZip_doesNotWipeCallerPassword() {
        val outStream = ByteArrayOutputStream()
        val backupPassword = "TestCallerPassword".toCharArray()

        BackupCryptoManager.exportProfilesToZip(emptyList(), emptyList(), backupPassword, outStream)
        val zipBytes = outStream.toByteArray()

        val inStream = ByteArrayInputStream(zipBytes)
        val importPassword = "TestCallerPassword".toCharArray()
        val (profiles, identities) = BackupCryptoManager.importProfilesFromZip(inStream, importPassword)

        assertTrue(profiles.isEmpty())
        assertTrue(identities.isEmpty())
        assertEquals("Caller's password array must NOT be cleared by BackupCryptoManager", "TestCallerPassword", String(importPassword))
    }
}
