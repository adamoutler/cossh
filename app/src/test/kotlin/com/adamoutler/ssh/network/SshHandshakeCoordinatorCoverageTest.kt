package com.adamoutler.ssh.network

import com.adamoutler.ssh.crypto.IdentityStorageManager
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.IdentityProfile
import kotlinx.coroutines.runBlocking
import net.schmizz.sshj.SSHClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import androidx.test.core.app.ApplicationProvider
import android.os.Build
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyPairGenerator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class SshHandshakeCoordinatorCoverageTest {

    @Test
    fun testConfigureHostKeyVerifier_withContext() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val coordinator = SshHandshakeCoordinator(context = app)
        val sshClient = SSHClient()
        coordinator.configureHostKeyVerifier(sshClient)
        // Should not throw
    }

    @Test
    fun testConfigureHostKeyVerifier_withoutContext_throws() {
        val coordinator = SshHandshakeCoordinator()
        val sshClient = SSHClient()
        var exceptionThrown = false
        try {
            coordinator.configureHostKeyVerifier(sshClient)
        } catch (e: java.io.IOException) {
            exceptionThrown = true
        }
        assertTrue(exceptionThrown)
    }

    @Test
    fun testGetAuthenticator_Password() {
        val coordinator = SshHandshakeCoordinator()
        val profile = ConnectionProfile(id = "1", nickname = "test", host = "localhost", authType = AuthType.PASSWORD)
        val authenticator = coordinator.getAuthenticator(profile, null, null)
        assertTrue(authenticator is PasswordAuthenticator)
    }

    @Test
    fun testGetAuthenticator_Key() {
        val coordinator = SshHandshakeCoordinator()
        val profile = ConnectionProfile(id = "1", nickname = "test", host = "localhost", authType = AuthType.KEY)
        
        val kpGen = KeyPairGenerator.getInstance("RSA")
        kpGen.initialize(2048)
        val keyPair = kpGen.generateKeyPair()
        
        val authenticator = coordinator.getAuthenticator(profile, keyPair, null)
        assertTrue(authenticator is KeyAuthenticator)
    }

    @Test
    fun testGetAuthenticator_Identity() {
        val coordinator = SshHandshakeCoordinator()
        val profile = ConnectionProfile(id = "1", nickname = "test", host = "localhost", authType = AuthType.PASSWORD)
        
        val identity = IdentityProfile(id = "id1", name = "id", username = "user", authType = AuthType.PASSWORD)
        identity.password = "pass".toByteArray()
        
        val authenticator = coordinator.getAuthenticator(profile, null, identity)
        assertTrue(authenticator != null)
    }

    @Test
    fun testResolveIdentity() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val storageManager = IdentityStorageManager(app, app.getSharedPreferences("test", 0))
        val identity = IdentityProfile(id = "id1", name = "id", username = "user", authType = AuthType.PASSWORD)
        storageManager.saveIdentity(identity)
        
        val coordinator = SshHandshakeCoordinator(identityStorageManager = storageManager)
        val profile = ConnectionProfile(id = "1", nickname = "test", host = "localhost", identityId = "id1")
        
        val resolved = coordinator.resolveIdentity(profile)
        assertEquals("user", resolved?.username)
    }

    @Test
    fun testLoadKeyPairFromIdentity_missingPrivateKey() {
        val coordinator = SshHandshakeCoordinator()
        val identity = IdentityProfile(id = "id1", name = "id", username = "user", authType = AuthType.PASSWORD)
        var exceptionThrown = false
        try {
            coordinator.loadKeyPairFromIdentity(identity)
        } catch (e: Exception) {
            exceptionThrown = true
        }
        assertTrue(exceptionThrown)
    }

    @Test
    fun testLoadKeyPairFromIdentity_invalidPublicKeyFormat() {
        val coordinator = SshHandshakeCoordinator()
        val kpGen = KeyPairGenerator.getInstance("Ed25519", "BC")
        val keyPair = kpGen.generateKeyPair()
        val sw = java.io.StringWriter()
        val pw = org.bouncycastle.openssl.jcajce.JcaPEMWriter(sw)
        pw.writeObject(keyPair)
        pw.close()
        val identity = IdentityProfile(id = "id1", name = "id", username = "user", authType = AuthType.KEY, publicKey = "ssh-ed25519 INVALIDBASE64FORMAT")
        identity.privateKey = sw.toString().toByteArray()
        
        // Should parse private key but public key extraction fails internally and falls back to PemUtils
        val kp = coordinator.loadKeyPairFromIdentity(identity)
        assertTrue(kp.private != null)
    }

    @Test
    fun testExecuteWithConnection_fails() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val storageManager = IdentityStorageManager(app, app.getSharedPreferences("test", 0))
        val identity = IdentityProfile(id = "id1", name = "id", username = "user", authType = AuthType.PASSWORD)
        identity.password = "pass".toByteArray()
        storageManager.saveIdentity(identity)
        val coordinator = SshHandshakeCoordinator(identityStorageManager = storageManager, context = app)
        val profile = ConnectionProfile(id = "1", nickname = "test", host = "127.0.0.1", port = 12345, identityId = "id1")
        
        var exceptionThrown = false
        try {
            coordinator.executeWithConnection(SSHClient(), profile, null) {
                // Do nothing
            }
        } catch (e: Exception) {
            exceptionThrown = true
        }
        assertTrue(exceptionThrown)
    }
}

