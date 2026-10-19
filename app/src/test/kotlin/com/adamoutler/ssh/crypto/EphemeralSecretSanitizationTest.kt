package com.adamoutler.ssh.crypto

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.IdentityProfile
import com.adamoutler.ssh.network.AuthCredentials
import com.adamoutler.ssh.network.SshConnectionManager
import com.adamoutler.ssh.network.SshHandshakeCoordinator
import com.adamoutler.ssh.ui.screens.AddEditProfileViewModel
import com.adamoutler.ssh.ui.screens.IdentityViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.DisconnectReason
import net.schmizz.sshj.transport.TransportException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class EphemeralSecretSanitizationTest {

    private lateinit var application: Application

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun executeWithConnection_whenHandshakeFails_zeroWipesProfilePassword() = runBlocking {
        val failingCoordinator = object : SshHandshakeCoordinator(context = application) {
            override fun connectClient(client: SSHClient, host: String, port: Int) {
                throw TransportException(DisconnectReason.HOST_KEY_NOT_VERIFIABLE, "Host key rejected")
            }
        }

        val rawPasswordBytes = byteArrayOf(0x41, 0x42, 0x43, 0x44, 0x45)
        val profile = ConnectionProfile(
            id = "wipe-fail-test",
            nickname = "WipeOnFail",
            host = "127.0.0.1",
            port = 22,
            authType = AuthType.PASSWORD,
            password = rawPasswordBytes,
        )

        try {
            failingCoordinator.executeWithConnection(SSHClient(), profile, null) {
                fail("Block should not execute on connection failure")
            }
            fail("Expected TransportException")
        } catch (e: TransportException) {
            assertEquals(DisconnectReason.HOST_KEY_NOT_VERIFIABLE, e.disconnectReason)
        }

        assertTrue(
            "Profile password buffer must be zeroed when connection handshake fails",
            rawPasswordBytes.all { it == 0.toByte() },
        )
    }

    @Test
    fun executeWithConnection_whenCancelled_zeroWipesProfilePassword() = runBlocking {
        val readyLatch = java.util.concurrent.CountDownLatch(1)
        val mockSuccessCoordinator = object : SshHandshakeCoordinator(context = application) {
            override fun connectClient(client: SSHClient, host: String, port: Int) {
                // Return immediately without network
            }

            override fun authenticateClient(
                client: SSHClient,
                profile: ConnectionProfile,
                keyPair: java.security.KeyPair?,
                identity: IdentityProfile?,
                effectiveProfile: ConnectionProfile,
            ) {
                // Return immediately
            }
        }

        val rawPasswordBytes = byteArrayOf(0x10, 0x20, 0x30, 0x40)
        val profile = ConnectionProfile(
            id = "wipe-cancel-test",
            nickname = "WipeOnCancel",
            host = "127.0.0.1",
            port = 22,
            authType = AuthType.PASSWORD,
            password = rawPasswordBytes,
        )

        val job = launch(kotlinx.coroutines.Dispatchers.Default) {
            try {
                mockSuccessCoordinator.executeWithConnection(SSHClient(), profile, null) {
                    readyLatch.countDown()
                    kotlinx.coroutines.awaitCancellation()
                }
            } catch (e: CancellationException) {
                // Expected
            }
        }

        // Wait until coroutine is suspended inside executeWithConnection's block
        readyLatch.await(5, java.util.concurrent.TimeUnit.SECONDS)
        job.cancelAndJoin()

        assertTrue(
            "Profile password buffer must be zeroed when coroutine is cancelled",
            rawPasswordBytes.all { it == 0.toByte() },
        )
    }

    @Test
    fun executeWithConnection_onSuccessfulExecution_zeroWipesProfilePassword() = runBlocking {
        val mockSuccessCoordinator = object : SshHandshakeCoordinator(context = application) {
            override fun connectClient(client: SSHClient, host: String, port: Int) {
                // Simulate successful connection without actual network
            }

            override fun authenticateClient(
                client: SSHClient,
                profile: ConnectionProfile,
                keyPair: java.security.KeyPair?,
                identity: IdentityProfile?,
                effectiveProfile: ConnectionProfile,
            ) {
                // Simulate successful auth
            }
        }

        val rawPasswordBytes = byteArrayOf(0x7F, 0x6E, 0x5D, 0x4C)
        val profile = ConnectionProfile(
            id = "wipe-success-test",
            nickname = "WipeOnSuccess",
            host = "127.0.0.1",
            port = 22,
            authType = AuthType.PASSWORD,
            password = rawPasswordBytes,
        )

        val result = mockSuccessCoordinator.executeWithConnection(SSHClient(), profile, null) { effective ->
            effective.nickname
        }

        assertEquals("WipeOnSuccess", result)
        assertTrue(
            "Profile password buffer must be zeroed upon completion of executeWithConnection",
            rawPasswordBytes.all { it == 0.toByte() },
        )
    }

    @Test
    fun identityViewModel_onResetState_zeroWipesSensitiveBuffers() {
        val storageManager = IdentityStorageManager(application, application.getSharedPreferences("test_id_wipe", 0))
        val viewModel = IdentityViewModel(application, storageManager)

        val rawPassword = byteArrayOf(1, 2, 3, 4, 5)
        val rawPrivateKey = byteArrayOf(6, 7, 8, 9, 10)

        viewModel.updateState {
            it.copy(
                originalPassword = rawPassword,
                privateKey = rawPrivateKey,
            )
        }

        viewModel.resetState()

        assertTrue(
            "Identity originalPassword must be zero-wiped on resetState",
            rawPassword.all { it == 0.toByte() },
        )
        assertTrue(
            "Identity privateKey must be zero-wiped on resetState",
            rawPrivateKey.all { it == 0.toByte() },
        )
        assertNull("ViewModel uiState originalPassword must be cleared", viewModel.uiState.value.originalPassword)
        assertNull("ViewModel uiState privateKey must be cleared", viewModel.uiState.value.privateKey)
    }

    @Test
    fun identityViewModel_onCleared_zeroWipesSensitiveBuffers() {
        val storageManager = IdentityStorageManager(application, application.getSharedPreferences("test_id_clear", 0))
        val viewModel = IdentityViewModel(application, storageManager)

        val rawPassword = byteArrayOf(0x0A, 0x0B, 0x0C)
        val rawPrivateKey = byteArrayOf(0x0D, 0x0E, 0x0F)

        viewModel.updateState {
            it.copy(
                originalPassword = rawPassword,
                privateKey = rawPrivateKey,
            )
        }

        // Invoke protected ViewModel.onCleared() via reflection
        val onClearedMethod = ViewModel::class.java.getDeclaredMethod("onCleared")
        onClearedMethod.isAccessible = true
        onClearedMethod.invoke(viewModel)

        assertTrue(
            "Identity originalPassword must be zero-wiped when ViewModel is cleared",
            rawPassword.all { it == 0.toByte() },
        )
        assertTrue(
            "Identity privateKey must be zero-wiped when ViewModel is cleared",
            rawPrivateKey.all { it == 0.toByte() },
        )
    }

    @Test
    fun addEditProfileViewModel_onResetAndCleared_zeroWipesOriginalPassword() {
        val prefs = application.getSharedPreferences("test_prof_wipe", 0)
        val secStorage = SecurityStorageManager(application, prefs)
        val idStorage = IdentityStorageManager(application, prefs)
        val viewModel = AddEditProfileViewModel(application, secStorage, idStorage)

        val rawPassword1 = byteArrayOf(0x11, 0x22, 0x33, 0x44)
        viewModel.updateState { it.copy(originalPassword = rawPassword1) }

        viewModel.resetState()
        assertTrue(
            "Profile originalPassword must be zero-wiped on resetState",
            rawPassword1.all { it == 0.toByte() },
        )
        assertNull("uiState originalPassword must be null after reset", viewModel.uiState.value.originalPassword)

        val rawPassword2 = byteArrayOf(0x55, 0x66, 0x77, 0x88.toByte())
        viewModel.updateState { it.copy(originalPassword = rawPassword2) }

        val onClearedMethod = ViewModel::class.java.getDeclaredMethod("onCleared")
        onClearedMethod.isAccessible = true
        onClearedMethod.invoke(viewModel)

        assertTrue(
            "Profile originalPassword must be zero-wiped when ViewModel is cleared",
            rawPassword2.all { it == 0.toByte() },
        )
    }

    @Test
    fun authCredentials_clearAndAutoCloseable_zeroWipesCharArray() {
        val passwordChars = "sensitivePromptPassword123".toCharArray()
        val credentials = AuthCredentials("admin", passwordChars)

        credentials.clear()

        assertTrue(
            "AuthCredentials password char array must be wiped with null characters",
            passwordChars.all { it == '\u0000' },
        )

        val autoCloseChars = "temporaryToken".toCharArray()
        AuthCredentials("operator", autoCloseChars).use {
            // inside block
        }
        assertTrue(
            "AuthCredentials password must be zeroed when closed via AutoCloseable",
            autoCloseChars.all { it == '\u0000' },
        )
    }

    @Test
    fun connectAndExecute_whenFailed_zeroWipesProfilePassword() = runBlocking {
        val rawPassword = byteArrayOf(9, 8, 7, 6, 5)
        val profile = ConnectionProfile(
            id = "connect-fail",
            nickname = "FailProfile",
            host = "unreachable.local",
            port = 22,
            authType = AuthType.PASSWORD,
            password = rawPassword,
        )

        val manager = SshConnectionManager(context = application)
        try {
            manager.connectAndExecute(profile, "echo hello")
        } catch (e: Exception) {
            // Expected connection failure
        }

        assertTrue(
            "Profile password must be zeroed when connectAndExecute fails",
            rawPassword.all { it == 0.toByte() },
        )
    }
}
