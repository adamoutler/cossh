package com.adamoutler.ssh.network

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import net.schmizz.sshj.userauth.UserAuthException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import java.net.ConnectException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class SshServiceTest {

    private lateinit var context: Context
    private lateinit var serviceController: ServiceController<SshService>
    private lateinit var service: SshService

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        serviceController = Robolectric.buildService(SshService::class.java)
        service = serviceController.create().get()
    }

    @Test
    fun `mapExceptionMessage formats authentication errors cleanly`() {
        val authEx = UserAuthException("Exhausted available authentication methods")
        assertEquals("Connection failed", SshService.mapExceptionMessage(authEx))

        val authEx2 = Exception("All authentication methods failed")
        assertEquals("Connection failed", SshService.mapExceptionMessage(authEx2))
    }

    @Test
    fun `mapExceptionMessage passes through other errors`() {
        val ex = ConnectException("Connection refused")
        assertEquals("Connection refused", SshService.mapExceptionMessage(ex))

        val exNull = Exception()
        assertEquals("Connection failed", SshService.mapExceptionMessage(exNull))
    }

    @Test
    fun `onBind returns null`() {
        assertNull(service.onBind(Intent()))
    }

    @Test
    fun `ACTION_DISCONNECT with no session ID and no profile ID stops all connections and self`() {
        val intent = Intent(SshService.ACTION_DISCONNECT)
        service.onStartCommand(intent, 0, 1)
        // At this point serviceScope should be cancelled. We can verify basic execution without crash
    }

    @Test
    fun `ACTION_START with no profile ID stops self`() {
        val intent = Intent(SshService.ACTION_START)
        service.onStartCommand(intent, 0, 1)
        // basic execution without crash
    }

    @Test
    fun `onDestroy stops all connections`() {
        service.onDestroy()
        // basic execution without crash
    }

    @Test
    fun `ACTION_DISCONNECT with session ID stops specific connection`() {
        val intent = Intent(SshService.ACTION_DISCONNECT).apply {
            putExtra(SshService.EXTRA_SESSION_ID, "test_session_id")
        }
        service.onStartCommand(intent, 0, 1)
        // basic execution without crash
    }

    @Test
    fun `ACTION_DISCONNECT with profile ID stops matching sessions`() {
        val intent = Intent(SshService.ACTION_DISCONNECT).apply {
            putExtra(SshService.EXTRA_PROFILE_ID, "test_profile_id")
        }
        service.onStartCommand(intent, 0, 1)
        // basic execution without crash
    }

    @Test
    fun `ACTION_START starts connection and service coroutine`() {
        val storageManager = com.adamoutler.ssh.crypto.SecurityStorageManager(context)
        val profile = com.adamoutler.ssh.data.ConnectionProfile(
            id = "test_profile_id",
            nickname = "Test Profile",
            host = "localhost",
            port = 22,
            authType = com.adamoutler.ssh.data.AuthType.PASSWORD,
            username = "testuser",
            password = "testpassword".toByteArray(),
        )
        storageManager.saveProfile(profile)

        val intent = Intent(SshService.ACTION_START).apply {
            putExtra(SshService.EXTRA_PROFILE_ID, "test_profile_id")
            putExtra(SshService.EXTRA_SESSION_ID, "test_session_id")
        }
        service.onStartCommand(intent, 0, 1)
        // Service starts a coroutine which does DB reads (which might fail or pass depending on setup)
        // We just ensure no crash on Main Thread.
    }

    @Test
    fun `test startSshConnection with missing profile transitions to Error state`() = kotlinx.coroutines.runBlocking {
        val intent = Intent(SshService.ACTION_START).apply {
            putExtra(SshService.EXTRA_PROFILE_ID, "non_existent_profile_id")
            putExtra(SshService.EXTRA_SESSION_ID, "test_session_id_missing")
        }
        service.onStartCommand(intent, 0, 1)

        var retries = 0
        var currentState: ConnectionState? = null
        while (retries < 50) {
            currentState = ConnectionStateRepository.connectionStates.value["non_existent_profile_id"]
            if (currentState is ConnectionState.Error) break
            kotlinx.coroutines.delay(10)
            retries++
        }

        assertTrue("State should be Error when profile is missing", currentState is ConnectionState.Error)
        assertEquals("Profile not found", (currentState as ConnectionState.Error).message)
    }

    @Test
    fun `test startSshConnection with failing connection updates state to Error`() = kotlinx.coroutines.runBlocking {
        val storageManager = com.adamoutler.ssh.crypto.SecurityStorageManager(context)
        // Connect to a blackhole IP to force a connection timeout/error
        val profile = com.adamoutler.ssh.data.ConnectionProfile(
            id = "fail_profile_id",
            nickname = "Fail Profile",
            host = "192.0.2.1", // TEST-NET-1, should timeout/fail to route
            port = 22,
            authType = com.adamoutler.ssh.data.AuthType.PASSWORD,
            username = "testuser",
            password = "testpassword".toByteArray(),
        )
        storageManager.saveProfile(profile)

        val intent = Intent(SshService.ACTION_START).apply {
            putExtra(SshService.EXTRA_PROFILE_ID, "fail_profile_id")
            putExtra(SshService.EXTRA_SESSION_ID, "test_session_id_fail")
        }
        service.onStartCommand(intent, 0, 1)

        var retries = 0
        var currentState: ConnectionState? = null
        while (retries < 1500) { // Wait up to 15 seconds for connection timeout
            currentState = ConnectionStateRepository.connectionStates.value["fail_profile_id"]
            if (currentState is ConnectionState.Error) break
            kotlinx.coroutines.delay(10)
            retries++
        }

        assertTrue("State should transition to Error on connection failure", currentState is ConnectionState.Error)
    }
}
