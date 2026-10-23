package com.adamoutler.ssh.ui.screens

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.crypto.IdentityStorageManager
import com.adamoutler.ssh.crypto.PemUtils
import com.adamoutler.ssh.crypto.SSHKeyGenerator
import com.adamoutler.ssh.data.AuthType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.pkcs.jcajce.JcePKCSPBEOutputEncryptorBuilder
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.StringWriter

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IdentityFileImportTest {

    private lateinit var application: Application
    private lateinit var storageManager: IdentityStorageManager
    private lateinit var viewModel: IdentityViewModel

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        val testPrefs = application.getSharedPreferences("test_identity_file_import_prefs", 0)
        storageManager = IdentityStorageManager(application, testPrefs)
        viewModel = IdentityViewModel(application, storageManager)
    }

    @Test
    fun testGenerateECDSAKeyPair() = kotlinx.coroutines.runBlocking {
        viewModel.generateECDSAKeyPair("secp256r1")

        var state = viewModel.uiState.value
        var attempts = 0
        while (state.publicKey.isEmpty() && attempts < 200) {
            delay(20)
            state = viewModel.uiState.value
            attempts++
        }

        assertTrue("Public key must start with ecdsa-sha2-nistp256", state.publicKey.startsWith("ecdsa-sha2-nistp256"))
        assertNotNull("Private key must be generated", state.privateKey)
        assertEquals("AuthType must be KEY", AuthType.KEY, state.authType)
    }

    @Test
    fun testImportUnencryptedKey_SetsKeysDirectly() = kotlinx.coroutines.runBlocking {
        val keyPair = SSHKeyGenerator.generateEd25519KeyPair()
        val pemBytes = SSHKeyGenerator.encodePrivateKey(keyPair)

        viewModel.importKeyFromBytes(pemBytes)

        var state = viewModel.uiState.value
        var attempts = 0
        while (state.publicKey.isEmpty() && attempts < 200) {
            delay(20)
            state = viewModel.uiState.value
            attempts++
        }

        assertTrue("Public key must start with ssh-ed25519", state.publicKey.startsWith("ssh-ed25519"))
        assertNotNull("Private key must be set", state.privateKey)
        assertNull("Pending encrypted key must be null", state.pendingEncryptedKeyBytes)
        assertEquals("AuthType must be KEY", AuthType.KEY, state.authType)
    }

    @Test
    fun testImportEncryptedKey_ShowsModal_UnlocksWithCorrectPassphrase() = kotlinx.coroutines.runBlocking {
        val keyPair = SSHKeyGenerator.generateRSAKeyPair()
        val passphrase = "test-secret-passphrase"

        // Build encrypted PKCS#8
        val encryptor = JcePKCSPBEOutputEncryptorBuilder(PKCSObjectIdentifiers.pbeWithSHAAnd3_KeyTripleDES_CBC)
            .build(passphrase.toCharArray())
        val encryptedHolder = org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfoBuilder(
            keyPair.private.encoded,
        ).build(encryptor)

        val sw = StringWriter()
        JcaPEMWriter(sw).use { writer ->
            writer.writeObject(encryptedHolder)
        }
        val encryptedPemBytes = sw.toString().toByteArray(Charsets.UTF_8)

        viewModel.importKeyFromBytes(encryptedPemBytes)

        var state = viewModel.uiState.value
        var attempts = 0
        while (state.pendingEncryptedKeyBytes == null && attempts < 200) {
            delay(20)
            state = viewModel.uiState.value
            attempts++
        }

        assertNotNull("Pending encrypted key bytes must be populated", state.pendingEncryptedKeyBytes)
        assertNotNull("Cipher summary must be present", state.pendingCipherSummary)
        assertTrue("Cipher summary should mention PKCS#8", state.pendingCipherSummary!!.contains("PKCS#8"))

        // Unlock with correct passphrase
        viewModel.unlockPendingKey(passphrase.toCharArray())

        attempts = 0
        while (state.pendingEncryptedKeyBytes != null && attempts < 200) {
            delay(20)
            state = viewModel.uiState.value
            attempts++
        }

        assertNull("Pending encrypted key bytes must be cleared", state.pendingEncryptedKeyBytes)
        assertTrue("Public key must start with ssh-rsa", state.publicKey.startsWith("ssh-rsa"))
        assertNotNull("Private key must be decrypted", state.privateKey)
        assertNull("Passphrase error should be null", state.passphraseError)
    }

    @Test
    fun testImportEncryptedKey_WrongPassphraseSetsError() = kotlinx.coroutines.runBlocking {
        val keyPair = SSHKeyGenerator.generateRSAKeyPair()
        val passphrase = "real-password-123"

        val encryptor = JcePKCSPBEOutputEncryptorBuilder(PKCSObjectIdentifiers.pbeWithSHAAnd3_KeyTripleDES_CBC)
            .build(passphrase.toCharArray())
        val encryptedHolder = org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfoBuilder(
            keyPair.private.encoded,
        ).build(encryptor)

        val sw = StringWriter()
        JcaPEMWriter(sw).use { writer ->
            writer.writeObject(encryptedHolder)
        }
        val encryptedPemBytes = sw.toString().toByteArray(Charsets.UTF_8)

        viewModel.importKeyFromBytes(encryptedPemBytes)

        var state = viewModel.uiState.value
        var attempts = 0
        while (state.pendingEncryptedKeyBytes == null && attempts < 200) {
            delay(20)
            state = viewModel.uiState.value
            attempts++
        }

        // Try unlocking with wrong passphrase
        viewModel.unlockPendingKey("wrong-password".toCharArray())

        attempts = 0
        while (state.passphraseError == null && attempts < 200) {
            delay(20)
            state = viewModel.uiState.value
            attempts++
        }

        assertNotNull("Passphrase error must be displayed", state.passphraseError)
        assertNotNull("Pending bytes must still remain for retry", state.pendingEncryptedKeyBytes)
        assertTrue("Public key must not be populated", state.publicKey.isEmpty())

        // Cancel unlock
        viewModel.cancelPendingKeyUnlock()
        val canceledState = viewModel.uiState.value
        assertNull("Pending bytes must be wiped upon cancellation", canceledState.pendingEncryptedKeyBytes)
        assertNull("Cipher summary must be cleared", canceledState.pendingCipherSummary)
    }

    @Test
    fun testImportKeyExceeding64KBLimit_IsRejected() = kotlinx.coroutines.runBlocking {
        val oversizedBytes = ByteArray(65537) { 0x41 }

        viewModel.importKeyFromBytes(oversizedBytes)

        delay(50)
        val state = viewModel.uiState.value
        assertNull("Pending encrypted key must not be set", state.pendingEncryptedKeyBytes)
        assertTrue("Public key must not be set", state.publicKey.isEmpty())
        assertNull("Private key must not be set", state.privateKey)
    }
}
