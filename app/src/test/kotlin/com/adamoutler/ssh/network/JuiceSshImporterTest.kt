package com.adamoutler.ssh.network

import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.UUID

class JuiceSshImporterTest {

    @Test
    fun testStandardJuiceSshParsingWithGroups() {
        val json = """
        {
          "version": 1,
          "groups": [
            { "id": "g-corp", "name": "Corporate Infrastructure" },
            { "id": "g-cloud", "name": "Cloud Providers" }
          ],
          "connections": [
            {
              "id": "c1",
              "name": "Production Web",
              "address": "198.51.100.10",
              "port": 2222,
              "username": "deployer",
              "type": "SSH",
              "group": "g-corp"
            },
            {
              "id": "c2",
              "name": "Legacy Switch",
              "address": "10.0.0.1",
              "port": 23,
              "username": "admin",
              "type": "TELNET",
              "group": "g-corp"
            },
            {
              "id": "c3",
              "name": "Edge Mosh",
              "address": "edge.example.org",
              "port": 22,
              "username": "sysadmin",
              "type": "MOSH",
              "group": "g-cloud"
            }
          ]
        }
        """.trimIndent()

        val result = JuiceSshImporter.import(json)

        assertEquals(3, result.profiles.size)
        assertEquals(3, result.importedProfiles.size)
        assertTrue(result.skippedProfiles.isEmpty())

        val web = result.profiles.find { it.nickname == "Production Web" }
        assertNotNull(web)
        assertEquals("198.51.100.10", web?.host)
        assertEquals(2222, web?.port)
        assertEquals("deployer", web?.username)
        assertEquals(Protocol.SSH, web?.protocol)
        assertEquals("Corporate Infrastructure", web?.folderId)

        val switch = result.profiles.find { it.nickname == "Legacy Switch" }
        assertNotNull(switch)
        assertEquals("10.0.0.1", switch?.host)
        assertEquals(23, switch?.port)
        assertEquals(Protocol.TELNET, switch?.protocol)
        assertEquals("Corporate Infrastructure", switch?.folderId)

        val edge = result.profiles.find { it.nickname == "Edge Mosh" }
        assertNotNull(edge)
        assertEquals("edge.example.org", edge?.host)
        assertEquals(22, edge?.port)
        assertEquals(Protocol.SSH, edge?.protocol)
        assertEquals("Cloud Providers", edge?.folderId)
    }

    @Test
    fun testPayloadSizeLimitEnforcement() {
        val largeJson = " ".repeat(1024 * 1024 + 10)

        val result = JuiceSshImporter.import(largeJson)

        assertTrue(result.importedProfiles.isEmpty())
        assertTrue(result.skippedProfiles.contains("PAYLOAD_SIZE_EXCEEDED"))
        assertTrue(result.warnings.any { it.contains("exceeds maximum allowed limit") })
    }

    @Test
    fun testMaxEntryCountEnforcement() {
        val sb = StringBuilder()
        sb.append("{\"connections\": [")
        for (i in 1..550) {
            if (i > 1) sb.append(",")
            sb.append("""{"name":"host-$i","address":"10.0.0.$i","port":22,"type":"SSH"}""")
        }
        sb.append("]}")

        val result = JuiceSshImporter.import(sb.toString())

        assertEquals(500, result.profiles.size)
        assertEquals(500, result.importedProfiles.size)
        assertEquals(50, result.skippedProfiles.size)
        assertTrue(result.warnings.any { it.contains("Exceeded maximum entry limit") })
    }

    @Test
    fun testControlCharacterAndInjectionStripping() {
        val maliciousJson = """
        {
          "connections": [
            {
              "name": "Evil\r\nProxyCommand rm -rf /\u0000",
              "address": "192.168.1.10\r\n",
              "port": 22,
              "username": "user\u0000inject\r\n",
              "type": "SSH"
            },
            {
              "name": "DisallowedHost",
              "address": "192.168.1.1; rm -rf /",
              "port": 22,
              "username": "root",
              "type": "SSH"
            },
            {
              "name": "InvalidPortHost",
              "address": "valid.example.com",
              "port": 70000,
              "username": "admin",
              "type": "SSH"
            }
          ]
        }
        """.trimIndent()

        val result = JuiceSshImporter.import(maliciousJson)

        // Only 1 connection is valid; 2 must be skipped due to invalid host and invalid port
        assertEquals(1, result.profiles.size)
        val sanitized = result.profiles.first()
        assertFalse(sanitized.nickname.contains("\r"))
        assertFalse(sanitized.nickname.contains("\n"))
        assertFalse(sanitized.nickname.contains("\u0000"))
        assertEquals("EvilProxyCommand rm -rf /", sanitized.nickname)
        assertEquals("192.168.1.10", sanitized.host)
        assertEquals("userinject", sanitized.username)

        assertTrue(result.skippedProfiles.contains("DisallowedHost"))
        assertTrue(result.skippedProfiles.contains("InvalidPortHost"))
    }

    @Test
    fun testCollisionStrategySkip() {
        val existing = listOf(
            ConnectionProfile(
                id = "existing_id_1",
                nickname = "Production DB",
                host = "10.0.0.50",
                port = 5432,
            ),
        )

        val importJson = """
        {
          "connections": [
            {
              "name": "Production DB",
              "address": "10.0.0.99",
              "port": 22
            },
            {
              "name": "Staging DB",
              "address": "10.0.0.51",
              "port": 22
            }
          ]
        }
        """.trimIndent()

        val result = JuiceSshImporter.import(
            json = importJson,
            existingProfiles = existing,
            strategy = ImportCollisionStrategy.SKIP,
        )

        assertEquals(2, result.profiles.size)
        val prodDb = result.profiles.find { it.nickname == "Production DB" }
        assertEquals("10.0.0.50", prodDb?.host) // Original retained
        assertEquals("existing_id_1", prodDb?.id)

        val stagingDb = result.profiles.find { it.nickname == "Staging DB" }
        assertNotNull(stagingDb)

        assertTrue(result.skippedProfiles.contains("Production DB"))
        assertEquals(1, result.importedProfiles.size)
    }

    @Test
    fun testCollisionStrategyOverwrite() {
        val existing = listOf(
            ConnectionProfile(
                id = "existing_id_1",
                nickname = "Production DB",
                host = "10.0.0.50",
                port = 5432,
                sortOrder = 4,
            ),
        )

        val importJson = """
        {
          "connections": [
            {
              "name": "Production DB",
              "address": "10.0.0.99",
              "port": 2222
            }
          ]
        }
        """.trimIndent()

        val result = JuiceSshImporter.import(
            json = importJson,
            existingProfiles = existing,
            strategy = ImportCollisionStrategy.OVERWRITE,
        )

        assertEquals(1, result.profiles.size)
        val prodDb = result.profiles.first()
        assertEquals("10.0.0.99", prodDb.host) // Overwritten with new host
        assertEquals(2222, prodDb.port)
        assertEquals("existing_id_1", prodDb.id) // Preserves original id
        assertEquals(4, prodDb.sortOrder) // Preserves original sort order
        assertTrue(result.skippedProfiles.isEmpty())
        assertEquals(1, result.importedProfiles.size)
    }

    @Test
    fun testCollisionStrategyRename() {
        val existing = listOf(
            ConnectionProfile(
                id = "existing_id_1",
                nickname = "Web Server",
                host = "10.0.0.1",
            ),
        )

        val importJson = """
        {
          "connections": [
            {
              "name": "Web Server",
              "address": "10.0.0.2",
              "port": 22
            },
            {
              "name": "Web Server",
              "address": "10.0.0.3",
              "port": 22
            }
          ]
        }
        """.trimIndent()

        val result = JuiceSshImporter.import(
            json = importJson,
            existingProfiles = existing,
            strategy = ImportCollisionStrategy.RENAME,
        )

        assertEquals(3, result.profiles.size)
        assertTrue(result.profiles.any { it.nickname == "Web Server" && it.host == "10.0.0.1" })
        assertTrue(result.profiles.any { it.nickname == "Web Server (1)" && it.host == "10.0.0.2" })
        assertTrue(result.profiles.any { it.nickname == "Web Server (2)" && it.host == "10.0.0.3" })
        assertEquals(2, result.importedProfiles.size)
    }

    @Test
    fun testStreamImportAndBounds() {
        val validJson = """
        {
          "connections": [
            { "name": "Streamed Host", "address": "192.168.1.200", "port": 22 }
          ]
        }
        """.trimIndent()

        val stream = ByteArrayInputStream(validJson.toByteArray(Charsets.UTF_8))
        val result = JuiceSshImporter.import(stream)

        assertEquals(1, result.profiles.size)
        assertEquals("Streamed Host", result.profiles.first().nickname)

        val oversizedBytes = ByteArray(1024 * 1024 + 10) { 'A'.code.toByte() }
        val oversizedStream = ByteArrayInputStream(oversizedBytes)
        val oversizedResult = JuiceSshImporter.import(oversizedStream)

        assertTrue(oversizedResult.importedProfiles.isEmpty())
        assertTrue(oversizedResult.skippedProfiles.contains("PAYLOAD_SIZE_EXCEEDED"))
    }

    @Test
    fun testHostValidatorBehaviors() {
        assertTrue(HostValidator.isValidHost("192.168.1.1"))
        assertTrue(HostValidator.isValidHost("server.example.com"))
        assertTrue(HostValidator.isValidHost("sub-domain.internal-net.org"))
        assertTrue(HostValidator.isValidHost("2001:db8::1"))
        assertTrue(HostValidator.isValidHost("[::1]"))

        assertFalse(HostValidator.isValidHost(""))
        assertFalse(HostValidator.isValidHost("   "))
        assertFalse(HostValidator.isValidHost("host; rm -rf /"))
        assertFalse(HostValidator.isValidHost("host`id`"))
        assertFalse(HostValidator.isValidHost("host with spaces"))
        assertFalse(HostValidator.isValidHost("a".repeat(300)))

        assertTrue(HostValidator.isValidPort(22))
        assertTrue(HostValidator.isValidPort(65535))
        assertFalse(HostValidator.isValidPort(0))
        assertFalse(HostValidator.isValidPort(-1))
        assertFalse(HostValidator.isValidPort(65536))

        assertTrue(HostValidator.isValidUsername("ubuntu"))
        assertTrue(HostValidator.isValidUsername(""))
        assertFalse(HostValidator.isValidUsername("user;evil"))
        assertFalse(HostValidator.isValidUsername("user name with spaces"))
    }
}
