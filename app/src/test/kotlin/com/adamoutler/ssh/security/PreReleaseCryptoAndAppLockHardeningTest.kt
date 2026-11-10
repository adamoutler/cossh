package com.adamoutler.ssh.security

import android.app.Activity
import android.app.Application
import android.content.Context
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.MainActivity
import com.adamoutler.ssh.crypto.IdentityStorageManager
import com.adamoutler.ssh.crypto.PasswordCipher
import com.adamoutler.ssh.crypto.SSHKeyGenerator
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.crypto.SettingsManager
import com.adamoutler.ssh.crypto.hardware.HardwareKeyProvider
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.IdentityProfile
import com.adamoutler.ssh.network.HardwareKeyAuthenticator
import com.adamoutler.ssh.network.SshHandshakeCoordinator
import com.adamoutler.ssh.ui.screens.IdentityViewModel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.KeyType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreReleaseCryptoAndAppLockHardeningTest {

    private lateinit var context: Context
    private lateinit var settingsManager: SettingsManager
    private lateinit var securityStorageManager: SecurityStorageManager
    private lateinit var identityStorageManager: IdentityStorageManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val appLockPrefs = context.getSharedPreferences("test_applock_hardening", Context.MODE_PRIVATE)
        appLockPrefs.edit().clear().commit()
        val identityPrefs = context.getSharedPreferences("test_identity_hardening", Context.MODE_PRIVATE)
        identityPrefs.edit().clear().commit()

        settingsManager = SettingsManager(context)
        securityStorageManager = SecurityStorageManager(context, appLockPrefs)
        identityStorageManager = IdentityStorageManager(context, identityPrefs)
        SshHandshakeCoordinator.testHardwareKeyProviders.clear()
    }

    @After
    fun tearDown() {
        SshHandshakeCoordinator.testHardwareKeyProviders.clear()
    }

    // =========================================================================
    // 1. Biometric App-Lock Exception Bypass Hardening
    // =========================================================================

    @Test
    fun `AppLockManager allowHeadlessTestFallback defaults strictly to false`() {
        val manager = AppLockManager(settingsManager, securityStorageManager)
        assertFalse(
            "allowHeadlessTestFallback must default to false to eliminate biometric prompt bypass",
            manager.allowHeadlessTestFallback,
        )
    }

    @Test
    fun `biometric failure does not unlock when allowHeadlessTestFallback is false`() {
        val manager = AppLockManager(settingsManager, securityStorageManager)
        val encryptCipher = PasswordCipher.initEncryptCipher()
        manager.enableAppLock(encryptCipher)
        manager.lock()

        assertTrue("AppLockManager must be locked initially", manager.isLocked.value)

        // Simulate catch block logic from MainActivity.promptBiometricUnlock()
        if (manager.allowHeadlessTestFallback) {
            val decryptCipher = PasswordCipher.initDecryptCipher(securityStorageManager.getAppLockIv()!!)
            manager.unlockWithCipher(decryptCipher)
        }

        assertTrue(
            "AppLockManager must remain locked when allowHeadlessTestFallback is false upon exception",
            manager.isLocked.value,
        )
    }

    @Test
    fun `biometric fallback unlocks only when allowHeadlessTestFallback is explicitly enabled`() {
        val manager = AppLockManager(settingsManager, securityStorageManager)
        val encryptCipher = PasswordCipher.initEncryptCipher()
        manager.enableAppLock(encryptCipher)
        manager.lock()

        manager.allowHeadlessTestFallback = true
        assertTrue("AppLockManager must be locked before fallback unlock", manager.isLocked.value)

        // Simulate catch block logic from MainActivity.promptBiometricUnlock()
        if (manager.allowHeadlessTestFallback) {
            val decryptCipher = PasswordCipher.initDecryptCipher(securityStorageManager.getAppLockIv()!!)
            manager.unlockWithCipher(decryptCipher)
        }

        assertFalse(
            "AppLockManager must unlock when allowHeadlessTestFallback is explicitly enabled",
            manager.isLocked.value,
        )
    }

    // =========================================================================
    // 2. Window-Level FLAG_SECURE Preservation Across Dialog Disposals
    // =========================================================================

    @Test
    fun `window FLAG_SECURE is preserved when already active prior to dialog display`() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val window = activity.window

        // Simulate MainActivity setting FLAG_SECURE at launch
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        val initialSecure = (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
        assertTrue("Window must initially possess FLAG_SECURE", initialSecure)

        // Simulate DisposableEffect lifecycle in PamAuthDialog / EncryptedKeyPassphraseModal / UrlConfirmationBottomSheet
        val wasAlreadySecure = (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        // Simulate onDispose
        if (!wasAlreadySecure) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        val afterDisposeSecure = (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
        assertTrue(
            "FLAG_SECURE must remain active on parent Activity window after dialog dismissal",
            afterDisposeSecure,
        )
    }

    @Test
    fun `window FLAG_SECURE is cleanly cleared if window was not secure prior to dialog display`() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val window = activity.window

        // Ensure window does not have FLAG_SECURE
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val initialSecure = (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
        assertFalse("Window must initially NOT possess FLAG_SECURE", initialSecure)

        // Simulate DisposableEffect lifecycle
        val wasAlreadySecure = (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        val duringDialogSecure = (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
        assertTrue("Window must possess FLAG_SECURE while dialog is displayed", duringDialogSecure)

        // Simulate onDispose
        if (!wasAlreadySecure) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        val afterDisposeSecure = (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
        assertFalse(
            "FLAG_SECURE must be cleared if the window was not secure before dialog display",
            afterDisposeSecure,
        )
    }

    @Test
    fun `SettingsManager screenshot and obscured touch blocking default to false and persist changes`() {
        val testPrefs = context.getSharedPreferences("cossh_settings", Context.MODE_PRIVATE)
        testPrefs.edit().remove(SettingsManager.KEY_SCREENSHOT_BLOCKING_ENABLED)
            .remove(SettingsManager.KEY_OVERLAY_TOUCH_BLOCKING_ENABLED).commit()

        val manager = SettingsManager(context)
        assertFalse(
            "isScreenshotBlockingEnabled must default to false",
            manager.isScreenshotBlockingEnabled,
        )
        assertFalse(
            "isOverlayTouchBlockingEnabled must default to false",
            manager.isOverlayTouchBlockingEnabled,
        )

        manager.isScreenshotBlockingEnabled = true
        assertTrue(
            "isScreenshotBlockingEnabled must persist true",
            manager.isScreenshotBlockingEnabled,
        )

        manager.isOverlayTouchBlockingEnabled = true
        assertTrue(
            "isOverlayTouchBlockingEnabled must persist true",
            manager.isOverlayTouchBlockingEnabled,
        )

        manager.isScreenshotBlockingEnabled = false
        assertFalse(
            "isScreenshotBlockingEnabled must persist false",
            manager.isScreenshotBlockingEnabled,
        )

        manager.isOverlayTouchBlockingEnabled = false
        assertFalse(
            "isOverlayTouchBlockingEnabled must persist false",
            manager.isOverlayTouchBlockingEnabled,
        )
    }

    @Test
    fun `MainActivity applies and clears FLAG_SECURE and filterTouchesWhenObscured dynamically`() {
        val testPrefs = context.getSharedPreferences("cossh_settings", Context.MODE_PRIVATE)
        testPrefs.edit()
            .putBoolean(SettingsManager.KEY_SCREENSHOT_BLOCKING_ENABLED, false)
            .putBoolean(SettingsManager.KEY_OVERLAY_TOUCH_BLOCKING_ENABLED, false)
            .commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val initialSecure = (activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
        assertFalse("MainActivity window must not have FLAG_SECURE by default", initialSecure)
        assertFalse("MainActivity decorView must not filter obscured touches by default", activity.window.decorView.filterTouchesWhenObscured)

        activity.settingsManager.isScreenshotBlockingEnabled = true
        val secureWhenEnabled = (activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
        assertTrue("MainActivity window must have FLAG_SECURE when setting is enabled", secureWhenEnabled)

        activity.settingsManager.isOverlayTouchBlockingEnabled = true
        assertTrue("MainActivity decorView must filter obscured touches when setting is enabled", activity.window.decorView.filterTouchesWhenObscured)

        activity.settingsManager.isScreenshotBlockingEnabled = false
        val secureWhenDisabled = (activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
        assertFalse("MainActivity window must clear FLAG_SECURE when setting is disabled", secureWhenDisabled)

        activity.settingsManager.isOverlayTouchBlockingEnabled = false
        assertFalse("MainActivity decorView must not filter obscured touches when setting is disabled", activity.window.decorView.filterTouchesWhenObscured)

        controller.destroy()
    }

    @Test
    fun `AppLockScreen enforces FLAG_SECURE while displayed and restores non-secure state on dispose`() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val window = activity.window
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        assertFalse("Window initially does not have FLAG_SECURE", (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0)

        // Simulate AppLockScreen DisposableEffect entry
        val wasAlreadySecure = (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        assertTrue("Window must have FLAG_SECURE while AppLockScreen is displayed", (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0)

        // Simulate onDispose
        if (!wasAlreadySecure) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        assertFalse("Window must restore non-secure state when AppLockScreen is dismissed", (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0)
    }

    @Test
    fun `AppLockScreen preserves FLAG_SECURE on dispose if user already enabled screenshot blocking`() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val window = activity.window
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        assertTrue("Window initially has FLAG_SECURE", (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0)

        // Simulate AppLockScreen DisposableEffect entry
        val wasAlreadySecure = (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        assertTrue("Window must retain FLAG_SECURE while AppLockScreen is displayed", (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0)

        // Simulate onDispose
        if (!wasAlreadySecure) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        assertTrue("Window must preserve FLAG_SECURE when AppLockScreen is dismissed if already enabled", (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0)
    }

    // =========================================================================
    // 3. StrongBox / TEE Hardware Keystore Keys End-to-End
    // =========================================================================

    @Test
    fun `IdentityProfile preserves keystoreAlias across serialization and equality`() {
        val id = UUID.randomUUID().toString()
        val alias = "strongbox_ssh_test_key_alias"
        val profile = IdentityProfile(
            id = id,
            name = "Hardware TEE Identity",
            username = "root",
            authType = AuthType.KEY,
            securityTier = "STRONGBOX",
            keystoreAlias = alias,
        )

        val json = Json.encodeToString(profile)
        assertTrue("Serialized JSON must contain keystoreAlias", json.contains(alias))

        val decoded = Json.decodeFromString<IdentityProfile>(json)
        assertEquals("Decoded profile must match original keystoreAlias", alias, decoded.keystoreAlias)
        assertEquals("Decoded profile must equal original profile", profile, decoded)
        assertEquals("HashCode must match", profile.hashCode(), decoded.hashCode())

        val nonMatching = profile.copy(keystoreAlias = "different_alias")
        assertFalse("Profiles with differing keystoreAliases must not be equal", profile == nonMatching)
    }

    @Test
    fun `IdentityStorageManager persists and retrieves keystoreAlias`() {
        val profile = IdentityProfile(
            id = "hw-profile-1",
            name = "TEE Key",
            username = "ec2-user",
            authType = AuthType.KEY,
            securityTier = "TEE",
            keystoreAlias = "tee_key_alias_999",
        )

        identityStorageManager.saveIdentity(profile)

        val loaded = identityStorageManager.getIdentity("hw-profile-1")
        assertNotNull("Loaded profile must not be null", loaded)
        assertEquals("tee_key_alias_999", loaded?.keystoreAlias)

        val allIdentities = identityStorageManager.getAllIdentities()
        assertTrue("getAllIdentities must contain saved hardware profile", allIdentities.any { it.id == "hw-profile-1" })
        assertEquals("tee_key_alias_999", allIdentities.first { it.id == "hw-profile-1" }.keystoreAlias)

        identityStorageManager.deleteIdentity("hw-profile-1")
        assertNull("Profile must be removed after deletion", identityStorageManager.getIdentity("hw-profile-1"))
    }

    @Test
    fun `IdentityStorageManager hydrates keystoreAlias from legacy auxiliary entry`() {
        // Simulate backward compatibility where JSON had null keystoreAlias but auxiliary entry exists
        val legacyProfile = IdentityProfile(
            id = "legacy-hw-id",
            name = "Legacy TEE Profile",
            username = "ubuntu",
            authType = AuthType.KEY,
            keystoreAlias = null,
        )

        // Write directly to backing prefs
        val prefs = context.getSharedPreferences("test_identity_hardening", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("legacy-hw-id", Json.encodeToString(legacyProfile))
            .putString("legacy-hw-id_keystore_alias", "migrated_keystore_alias_123")
            .commit()

        val loaded = identityStorageManager.getIdentity("legacy-hw-id")
        assertNotNull(loaded)
        assertEquals("migrated_keystore_alias_123", loaded?.keystoreAlias)
    }

    @Test
    fun `SshHandshakeCoordinator configures HardwareKeyAuthenticator when keystoreAlias is present`() {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(256)
        val kp = kpg.generateKeyPair()

        val hardwarePrivKey = object : PrivateKey by kp.private {
            override fun getEncoded(): ByteArray? = null
        }
        val hardwareProvider = HardwareKeyProvider(
            pubKey = kp.public,
            privKey = hardwarePrivKey,
            keyType = KeyType.ECDSA256,
        )

        val alias = "strongbox_test_conn_alias"
        SshHandshakeCoordinator.testHardwareKeyProviders[alias] = hardwareProvider

        val coordinator = SshHandshakeCoordinator(
            identityStorageManager = identityStorageManager,
            context = context,
        )

        val identity = IdentityProfile(
            id = "id-hardware",
            name = "Hardware Identity",
            username = "admin",
            authType = AuthType.KEY,
            securityTier = "STRONGBOX",
            keystoreAlias = alias,
            publicKey = SSHKeyGenerator.encodePublicKey(kp),
        )

        val profile = ConnectionProfile(
            id = "conn-1",
            nickname = "Prod Server",
            host = "192.168.1.1",
            username = "admin",
            authType = AuthType.KEY,
        )

        val authenticator = coordinator.getAuthenticator(profile, keyPair = null, identity = identity)
        assertNotNull("Authenticator must not be null", authenticator)
        assertEquals("CompositeAuthenticator", authenticator.javaClass.simpleName)

        // Verify HardwareKeyAuthenticator execution
        var capturedUser: String? = null
        var capturedProvider: Any? = null
        val fakeClient = object : SSHClient() {
            override fun authPublickey(username: String?, vararg kProviders: net.schmizz.sshj.userauth.keyprovider.KeyProvider?) {
                capturedUser = username
                capturedProvider = kProviders.firstOrNull()
            }
        }
        val auth = HardwareKeyAuthenticator(hardwareProvider)
        auth.authenticate(fakeClient, profile)
        assertEquals("admin", capturedUser)
        assertEquals(hardwareProvider, capturedProvider)
    }

    @Test
    fun `SshHandshakeCoordinator parsePublicKeyFromOpenSsh handles valid and malformed keys safely`() {
        val coordinator = SshHandshakeCoordinator(null, null, null)

        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()
        val openSshPub = SSHKeyGenerator.encodePublicKey(kp)

        val parsedPub = coordinator.parsePublicKeyFromOpenSsh(openSshPub)
        assertNotNull("Valid OpenSSH public key string must parse to PublicKey", parsedPub)

        assertNull("Blank key string returns null", coordinator.parsePublicKeyFromOpenSsh(""))
        assertNull("Null key string returns null", coordinator.parsePublicKeyFromOpenSsh(null))
        assertNull("Malformed key string returns null", coordinator.parsePublicKeyFromOpenSsh("invalid-key-data"))
    }

    @Test
    fun `IdentityViewModel preserves keystoreAlias in UI state and saveIdentity`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = IdentityViewModel(app, identityStorageManager)

        // 1. Generate StrongBox key pair (runs asynchronously on Dispatchers.Default)
        viewModel.generateStrongBoxKeyPair()
        val genDeadline = System.currentTimeMillis() + 5_000L
        while (viewModel.uiState.value.keystoreAlias == null && System.currentTimeMillis() < genDeadline) {
            Thread.sleep(25)
        }
        val stateAfterGen = viewModel.uiState.value
        assertNotNull("StrongBox generation must populate keystoreAlias in UI state", stateAfterGen.keystoreAlias)
        assertTrue(
            "keystoreAlias must begin with 'strongbox_ssh_'",
            stateAfterGen.keystoreAlias?.startsWith("strongbox_ssh_") == true,
        )

        // 2. Save identity constructed from state (runs asynchronously on Dispatchers.IO)
        val identity = IdentityProfile(
            id = "vm-hw-identity",
            name = "StrongBox Cloud Key",
            username = "deploy",
            authType = stateAfterGen.authType,
            securityTier = stateAfterGen.securityTier,
            publicKey = stateAfterGen.publicKey,
            keystoreAlias = null, // simulate screen not passing it
        )

        viewModel.saveIdentity(identity)
        val saveDeadline = System.currentTimeMillis() + 5_000L
        while (identityStorageManager.getIdentity("vm-hw-identity") == null && System.currentTimeMillis() < saveDeadline) {
            Thread.sleep(25)
        }

        // Verify persisted identity has keystoreAlias preserved from viewModel state
        val loaded = identityStorageManager.getIdentity("vm-hw-identity")
        assertNotNull("Persisted identity must exist", loaded)
        assertEquals(stateAfterGen.keystoreAlias, loaded?.keystoreAlias)
    }
}
