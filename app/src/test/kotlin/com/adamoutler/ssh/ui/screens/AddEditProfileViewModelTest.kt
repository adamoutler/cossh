package com.adamoutler.ssh.ui.screens

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.crypto.IdentityStorageManager
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.IdentityProfile
import com.adamoutler.ssh.data.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AddEditProfileViewModelTest {

    private fun createViewModel(): Triple<AddEditProfileViewModel, SecurityStorageManager, IdentityStorageManager> {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = app.getSharedPreferences("test_prefs_" + java.util.UUID.randomUUID(), android.content.Context.MODE_PRIVATE)
        val storageManager = SecurityStorageManager(app, prefs)
        val identityStorageManager = IdentityStorageManager(app, prefs)
        val viewModel = AddEditProfileViewModel(app, storageManager, identityStorageManager)
        return Triple(viewModel, storageManager, identityStorageManager)
    }

    @Test
    fun `test save and retrieve profile`() {
        val (viewModel, _, _) = createViewModel()

        viewModel.saveProfile(
            id = "test-id-123",
            nickname = "Local Test",
            host = "127.0.0.1",
            port = "2222",
            protocol = Protocol.SSH,
            username = "root",
            authType = AuthType.PASSWORD,
            password = "testpassword".toByteArray(),
            keyReference = null,
        )

        val profile = viewModel.getProfile("test-id-123")
        assertNotNull(profile)
        assertEquals("Local Test", profile?.nickname)
        assertEquals("127.0.0.1", profile?.host)
        assertEquals(2222, profile?.port)
        assertEquals("root", profile?.username)
        assertEquals(AuthType.PASSWORD, profile?.authType)
        assertEquals("testpassword", profile?.password?.decodeToString())
    }

    @Test
    fun `test updateState and resetState`() {
        val (viewModel, _, _) = createViewModel()

        viewModel.updateState { it.copy(nickname = "New Name", host = "1.2.3.4") }
        assertEquals("New Name", viewModel.uiState.value.nickname)
        assertEquals("1.2.3.4", viewModel.uiState.value.host)

        viewModel.resetState()
        assertEquals("", viewModel.uiState.value.nickname)
        assertEquals("", viewModel.uiState.value.host)
    }

    @Test
    fun `test loadProfileIfNeeded`() {
        val (viewModel, storageManager, _) = createViewModel()

        val profile = ConnectionProfile(
            id = "load-test",
            nickname = "Load",
            host = "test",
            envVars = mapOf("A" to "B"),
            commandSnippets = listOf(com.adamoutler.ssh.data.CommandSnippet("c1", "Restart", "systemctl restart", true, false)),
        )
        storageManager.saveProfile(profile)

        viewModel.loadProfileIfNeeded("load-test")
        assertEquals(true, viewModel.uiState.value.isLoaded)
        assertEquals("Load", viewModel.uiState.value.nickname)
        assertEquals("A=B", viewModel.uiState.value.envVarsText)
        assertEquals(1, viewModel.uiState.value.commandSnippets.size)

        // Loading again shouldn't overwrite if modified
        viewModel.updateState { it.copy(nickname = "Modified") }
        viewModel.loadProfileIfNeeded("load-test")
        assertEquals("Modified", viewModel.uiState.value.nickname)
    }

    @Test
    fun `test getAvailableKeys and getIdentities`() {
        val (viewModel, _, identityStorageManager) = createViewModel()

        identityStorageManager.saveIdentity(IdentityProfile(id = "id-1", name = "Test Identity", username = "test"))

        val keys = viewModel.getAvailableKeys()
        assertNotNull(keys)

        val identities = viewModel.getIdentities()
        assertTrue(identities.any { it.name == "Test Identity" })
    }

    @Test
    fun `test validatePort with valid ports`() {
        val (viewModel, _, _) = createViewModel()

        assertTrue(viewModel.validatePort("1"))
        assertTrue(viewModel.validatePort("22"))
        assertTrue(viewModel.validatePort("80"))
        assertTrue(viewModel.validatePort("443"))
        assertTrue(viewModel.validatePort("8080"))
        assertTrue(viewModel.validatePort("65535"))
    }

    @Test
    fun `test validatePort with invalid ports`() {
        val (viewModel, _, _) = createViewModel()

        assertFalse(viewModel.validatePort("0"))
        assertFalse(viewModel.validatePort("-1"))
        assertFalse(viewModel.validatePort("65536"))
        assertFalse(viewModel.validatePort("70000"))
        assertFalse(viewModel.validatePort("abc"))
        assertFalse(viewModel.validatePort(""))
        assertFalse(viewModel.validatePort("   "))
    }

    @Test
    fun `test validateHost`() {
        val (viewModel, _, _) = createViewModel()

        assertTrue(viewModel.validateHost("localhost"))
        assertTrue(viewModel.validateHost("127.0.0.1"))
        assertTrue(viewModel.validateHost("ssh.example.com"))
        assertTrue(viewModel.validateHost("  my-server  "))

        assertFalse(viewModel.validateHost(""))
        assertFalse(viewModel.validateHost("   "))
    }

    @Test
    fun `test parseEnvVars`() {
        val (viewModel, _, _) = createViewModel()

        assertTrue(viewModel.parseEnvVars("").isEmpty())
        assertTrue(viewModel.parseEnvVars("   ").isEmpty())

        val single = viewModel.parseEnvVars("VAR=val")
        assertEquals(mapOf("VAR" to "val"), single)

        val multiple = viewModel.parseEnvVars("VAR1=val1, VAR2=val2 ,VAR3=val3")
        assertEquals(mapOf("VAR1" to "val1", "VAR2" to "val2", "VAR3" to "val3"), multiple)

        val complexValue = viewModel.parseEnvVars("CONN=host=db;port=5432,MODE=debug")
        assertEquals(mapOf("CONN" to "host=db;port=5432", "MODE" to "debug"), complexValue)

        val withMalformed = viewModel.parseEnvVars("VALID=1, NO_EQUALS, ALSO_VALID=2")
        assertEquals(mapOf("VALID" to "1", "ALSO_VALID" to "2"), withMalformed)
    }

    @Test
    fun `test resolveCredentials for SSH with inline password`() {
        val (viewModel, _, _) = createViewModel()

        val stateWithPass = ProfileFormState(
            protocol = Protocol.SSH,
            username = "user1",
            authType = AuthType.PASSWORD,
            password = "secretpassword",
            identityId = null,
        )
        val creds = viewModel.resolveCredentials(stateWithPass)
        assertEquals("user1", creds.username)
        assertEquals(AuthType.PASSWORD, creds.authType)
        assertEquals("secretpassword", creds.password?.decodeToString())
        assertNull(creds.keyReference)
        assertNull(creds.identityId)

        // Test with empty password
        val stateEmptyPass = ProfileFormState(
            protocol = Protocol.SSH,
            username = "user1",
            authType = AuthType.PASSWORD,
            password = "",
            identityId = null,
        )
        val credsEmpty = viewModel.resolveCredentials(stateEmptyPass)
        assertNull(credsEmpty.password)

        // Test with locked password
        val stateLockedPass = ProfileFormState(
            protocol = Protocol.SSH,
            username = "user1",
            authType = AuthType.PASSWORD,
            isPasswordLocked = true,
            originalPassword = "lockedsecret".toByteArray(),
            password = "",
            identityId = null,
        )
        val credsLocked = viewModel.resolveCredentials(stateLockedPass)
        assertEquals("lockedsecret", credsLocked.password?.decodeToString())
    }

    @Test
    fun `test resolveCredentials for SSH with inline key`() {
        val (viewModel, _, _) = createViewModel()

        val stateWithKey = ProfileFormState(
            protocol = Protocol.SSH,
            username = "keyuser",
            authType = AuthType.KEY,
            keyReference = "key-ref-456",
            identityId = null,
        )
        val creds = viewModel.resolveCredentials(stateWithKey)
        assertEquals("keyuser", creds.username)
        assertEquals(AuthType.KEY, creds.authType)
        assertEquals("key-ref-456", creds.keyReference)
        assertNull(creds.password)
        assertNull(creds.identityId)
    }

    @Test
    fun `test resolveCredentials for SSH with selected identity`() {
        val (viewModel, _, identityStorageManager) = createViewModel()

        identityStorageManager.saveIdentity(
            IdentityProfile(
                id = "ident-99",
                name = "My Identity",
                username = "identityUser",
                authType = AuthType.KEY,
            ),
        )

        val stateWithIdent = ProfileFormState(
            protocol = Protocol.SSH,
            username = "screenUser",
            authType = AuthType.PASSWORD,
            identityId = "ident-99",
        )
        val creds = viewModel.resolveCredentials(stateWithIdent)
        assertEquals("identityUser", creds.username)
        assertEquals(AuthType.KEY, creds.authType)
        assertEquals("ident-99", creds.identityId)
        assertNull(creds.password)
        assertNull(creds.keyReference)
    }

    @Test
    fun `test resolveCredentials for Telnet`() {
        val (viewModel, _, identityStorageManager) = createViewModel()

        identityStorageManager.saveIdentity(
            IdentityProfile(
                id = "ident-telnet",
                name = "Telnet Identity",
                username = "telnetIdentUser",
                authType = AuthType.KEY,
            ),
        )

        // Telnet should ignore identity and enforce password auth
        val stateTelnet = ProfileFormState(
            protocol = Protocol.TELNET,
            username = "telnetUser",
            authType = AuthType.KEY, // Even if set to KEY, Telnet overrides to PASSWORD
            password = "telnetPass",
            identityId = "ident-telnet",
        )
        val creds = viewModel.resolveCredentials(stateTelnet)
        assertEquals("telnetUser", creds.username)
        assertEquals(AuthType.PASSWORD, creds.authType)
        assertEquals("telnetPass", creds.password?.decodeToString())
        assertNull(creds.identityId)
        assertNull(creds.keyReference)
    }

    @Test
    fun `test setProtocol switches default ports and auth types`() {
        val (viewModel, _, _) = createViewModel()

        // Default state is SSH on port 22
        assertEquals(Protocol.SSH, viewModel.uiState.value.protocol)
        assertEquals("22", viewModel.uiState.value.port)

        // Switch to TELNET
        viewModel.setProtocol(Protocol.TELNET)
        assertEquals(Protocol.TELNET, viewModel.uiState.value.protocol)
        assertEquals("23", viewModel.uiState.value.port)
        assertEquals(AuthType.PASSWORD, viewModel.uiState.value.authType)

        // Switch back to SSH
        viewModel.setProtocol(Protocol.SSH)
        assertEquals(Protocol.SSH, viewModel.uiState.value.protocol)
        assertEquals("22", viewModel.uiState.value.port)

        // When custom port is used, it should be preserved
        viewModel.updateState { it.copy(port = "2222") }
        viewModel.setProtocol(Protocol.TELNET)
        assertEquals("2222", viewModel.uiState.value.port)

        viewModel.setProtocol(Protocol.SSH)
        assertEquals("2222", viewModel.uiState.value.port)
    }

    @Test
    fun `test saveProfileFromState successfully saves valid profile`() {
        val (viewModel, storageManager, _) = createViewModel()

        viewModel.updateState {
            it.copy(
                nickname = "Saved Server",
                host = "192.168.1.50",
                port = "22",
                protocol = Protocol.SSH,
                username = "ubuntu",
                password = "mypassword",
                envVarsText = "TERM=xterm-256color",
            )
        }

        val success = viewModel.saveProfile("saved-profile-id")
        assertTrue(success)

        val saved = storageManager.getProfile("saved-profile-id")
        assertNotNull(saved)
        assertEquals("Saved Server", saved?.nickname)
        assertEquals("192.168.1.50", saved?.host)
        assertEquals(22, saved?.port)
        assertEquals("ubuntu", saved?.username)
        assertEquals(mapOf("TERM" to "xterm-256color"), saved?.envVars)
        assertEquals("mypassword", saved?.password?.decodeToString())

        // Form state should be reset after save
        assertEquals("", viewModel.uiState.value.host)
        assertNull(viewModel.uiState.value.hostError)
        assertNull(viewModel.uiState.value.portError)
    }

    @Test
    fun `test saveProfileFromState validation failure on empty host`() {
        val (viewModel, storageManager, _) = createViewModel()

        viewModel.updateState {
            it.copy(
                host = "   ",
                port = "22",
                nickname = "Invalid Host Profile",
            )
        }

        val success = viewModel.saveProfile("should-not-save")
        assertFalse(success)
        assertEquals("Host cannot be empty", viewModel.uiState.value.hostError)
        assertNull(viewModel.uiState.value.portError)
        assertNull(storageManager.getProfile("should-not-save"))
    }

    @Test
    fun `test saveProfileFromState validation failure on invalid port`() {
        val (viewModel, storageManager, _) = createViewModel()

        // Test out of range port (> 65535)
        viewModel.updateState {
            it.copy(
                host = "10.0.0.1",
                port = "70000",
                nickname = "Port Too High",
            )
        }
        assertFalse(viewModel.saveProfile("port-high-fail"))
        assertEquals("Port must be between 1 and 65535", viewModel.uiState.value.portError)
        assertNull(viewModel.uiState.value.hostError)
        assertNull(storageManager.getProfile("port-high-fail"))

        // Test port 0 (below 1)
        viewModel.updateState { it.copy(port = "0") }
        assertFalse(viewModel.saveProfile("port-zero-fail"))
        assertEquals("Port must be between 1 and 65535", viewModel.uiState.value.portError)

        // Test non-numeric port
        viewModel.updateState { it.copy(port = "not_a_port") }
        assertFalse(viewModel.saveProfile("port-string-fail"))
        assertEquals("Port must be between 1 and 65535", viewModel.uiState.value.portError)
    }

    @Test
    fun `test saveProfileFromState validation failure on both host and port`() {
        val (viewModel, storageManager, _) = createViewModel()

        viewModel.updateState {
            it.copy(
                host = "",
                port = "-5",
            )
        }

        val success = viewModel.saveProfile("dual-fail")
        assertFalse(success)
        assertEquals("Host cannot be empty", viewModel.uiState.value.hostError)
        assertEquals("Port must be between 1 and 65535", viewModel.uiState.value.portError)
        assertNull(storageManager.getProfile("dual-fail"))
    }
}
