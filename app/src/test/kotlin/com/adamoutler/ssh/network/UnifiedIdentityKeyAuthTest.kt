package com.adamoutler.ssh.network

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.crypto.IdentityStorageManager
import com.adamoutler.ssh.crypto.SSHKeyGenerator
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.IdentityProfile
import com.adamoutler.ssh.data.Protocol
import net.schmizz.sshj.userauth.UserAuthException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class UnifiedIdentityKeyAuthTest {

    private fun generateRsaKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        return kpg.generateKeyPair()
    }

    @Test
    fun `test key auth with null identity and null keypair throws UserAuthException`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = app.getSharedPreferences("test_unified_auth_" + UUID.randomUUID(), 0)
        val identityStorageManager = IdentityStorageManager(app, prefs)
        val coordinator = SshHandshakeCoordinator(
            identityStorageManager = identityStorageManager,
            context = app,
        )

        val profile = ConnectionProfile(
            id = "prof-key-no-identity",
            nickname = "Key Profile Without Identity",
            host = "10.0.0.1",
            username = "admin",
            authType = AuthType.KEY,
            protocol = Protocol.SSH,
            identityId = null,
        )

        try {
            coordinator.getAuthenticator(profile, keyPair = null, identity = null)
            fail("Expected UserAuthException for key authentication without identity")
        } catch (e: UserAuthException) {
            assertTrue(
                "Exception message must guide user to select Identity Profile: ${e.message}",
                e.message?.contains("Identity Profile") == true,
            )
        }
    }

    @Test
    fun `test key auth with configured identity returns valid authenticator`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = app.getSharedPreferences("test_unified_auth_" + UUID.randomUUID(), 0)
        val identityStorageManager = IdentityStorageManager(app, prefs)
        val coordinator = SshHandshakeCoordinator(
            identityStorageManager = identityStorageManager,
            context = app,
        )

        val generatedKey = SSHKeyGenerator.generateRSAKeyPair()
        val pubKeyStr = SSHKeyGenerator.encodePublicKey(generatedKey)
        val privKeyBytes = SSHKeyGenerator.encodePrivateKey(generatedKey)

        val identity = IdentityProfile(
            id = "identity-prod",
            name = "Production Bastion Key",
            username = "ubuntu",
            publicKey = pubKeyStr,
            privateKey = privKeyBytes,
            authType = AuthType.KEY,
        )
        identityStorageManager.saveIdentity(identity)

        val profile = ConnectionProfile(
            id = "prof-with-identity",
            nickname = "Prod Bastion",
            host = "192.168.1.50",
            username = "ignored",
            authType = AuthType.KEY,
            protocol = Protocol.SSH,
            identityId = identity.id,
        )

        val resolvedIdentity = coordinator.resolveIdentity(profile)
        assertNotNull(resolvedIdentity)
        assertEquals(identity.id, resolvedIdentity?.id)

        val authenticator = coordinator.getAuthenticator(profile, keyPair = null, identity = resolvedIdentity)
        assertNotNull(authenticator)
    }

    @Test
    fun `test empty identity throws UserAuthException`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = app.getSharedPreferences("test_unified_auth_" + UUID.randomUUID(), 0)
        val identityStorageManager = IdentityStorageManager(app, prefs)
        val coordinator = SshHandshakeCoordinator(
            identityStorageManager = identityStorageManager,
            context = app,
        )

        val emptyIdentity = IdentityProfile(
            id = "empty-id",
            name = "Blank Identity",
            username = "blank",
            publicKey = null,
            privateKey = null,
            password = null,
            authType = AuthType.KEY,
        )

        val profile = ConnectionProfile(
            id = "prof-blank-ident",
            nickname = "Blank Profile",
            host = "10.0.0.2",
            username = "user",
            authType = AuthType.KEY,
            identityId = emptyIdentity.id,
        )

        try {
            coordinator.getAuthenticator(profile, keyPair = null, identity = emptyIdentity)
            fail("Expected UserAuthException for blank identity")
        } catch (e: UserAuthException) {
            assertTrue(
                "Exception message must mention identity has neither password nor private key: ${e.message}",
                e.message?.contains("neither a valid password nor an SSH private key") == true,
            )
        }
    }

    @Test
    fun `test backward compatibility when keypair is directly supplied`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val coordinator = SshHandshakeCoordinator(context = app)
        val keyPair = generateRsaKeyPair()

        val profile = ConnectionProfile(
            id = "prof-direct-key",
            nickname = "Direct Key",
            host = "127.0.0.1",
            username = "test",
            authType = AuthType.KEY,
        )

        val authenticator = coordinator.getAuthenticator(profile, keyPair = keyPair, identity = null)
        assertNotNull(authenticator)
    }
}
