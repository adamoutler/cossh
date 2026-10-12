package com.adamoutler.ssh.network

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.crypto.IdentityStorageManager
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.IdentityProfile
import kotlinx.coroutines.runBlocking
import net.schmizz.sshj.SSHClient
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.KeyPair
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
        assertTrue(authenticator::class.java.simpleName == "CompositeAuthenticator")
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

    @Test
    fun testExecuteWithConnection_useLocalDns_resolvesAndPreservesHostname() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        var capturedInetAddress: java.net.InetAddress? = null
        var capturedPort: Int? = null
        var authenticated = false

        val coordinator = object : SshHandshakeCoordinator(context = app) {
            override fun resolveHostLocally(host: String): Array<java.net.InetAddress>? {
                assertEquals("mock.hackedyour.info", host)
                // Simulate local DHCP DNS returning 192.168.1.50 for LAN host
                return arrayOf(java.net.InetAddress.getByAddress(byteArrayOf(192.toByte(), 168.toByte(), 1.toByte(), 50.toByte())))
            }

            override fun connectClient(client: SSHClient, inetAddress: java.net.InetAddress, port: Int) {
                capturedInetAddress = inetAddress
                capturedPort = port
            }

            override fun authenticateClient(
                client: SSHClient,
                profile: ConnectionProfile,
                keyPair: KeyPair?,
                identity: IdentityProfile?,
                effectiveProfile: ConnectionProfile,
            ) {
                authenticated = true
            }
        }

        val profile = ConnectionProfile(
            id = "test-local-dns",
            nickname = "Local Mock Server",
            host = "mock.hackedyour.info",
            port = 2222,
            authType = AuthType.PASSWORD,
            useLocalDns = true,
        )

        val result = coordinator.executeWithConnection(SSHClient(), profile, null) { effective ->
            assertEquals("mock.hackedyour.info", effective.host)
            "CONNECTED"
        }

        assertEquals("CONNECTED", result)
        assertTrue(authenticated)
        assertEquals(2222, capturedPort)
        assertNotNull(capturedInetAddress)
        // Verify hostname is strictly preserved for Host Key Verification / TOFU
        assertEquals("mock.hackedyour.info", capturedInetAddress?.hostName)
        // Verify IP bytes correspond to the local network DHCP DNS answer
        assertArrayEquals(
            byteArrayOf(192.toByte(), 168.toByte(), 1.toByte(), 50.toByte()),
            capturedInetAddress?.address,
        )
    }

    @Test
    fun testExecuteWithConnection_useLocalDns_emptyResolutionFallsBackToSystem() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        var capturedHost: String? = null
        var capturedPort: Int? = null

        val coordinator = object : SshHandshakeCoordinator(context = app) {
            override fun resolveHostLocally(host: String): Array<java.net.InetAddress>? {
                return null
            }

            override fun connectClient(client: SSHClient, host: String, port: Int) {
                capturedHost = host
                capturedPort = port
            }

            override fun authenticateClient(
                client: SSHClient,
                profile: ConnectionProfile,
                keyPair: KeyPair?,
                identity: IdentityProfile?,
                effectiveProfile: ConnectionProfile,
            ) {}
        }

        val profile = ConnectionProfile(
            id = "test-fallback",
            nickname = "Fallback Server",
            host = "mock.hackedyour.info",
            port = 22,
            authType = AuthType.PASSWORD,
            useLocalDns = true,
        )

        val result = coordinator.executeWithConnection(SSHClient(), profile, null) { "FALLBACK_SUCCESS" }

        assertEquals("FALLBACK_SUCCESS", result)
        assertEquals("mock.hackedyour.info", capturedHost)
        assertEquals(22, capturedPort)
    }
}
