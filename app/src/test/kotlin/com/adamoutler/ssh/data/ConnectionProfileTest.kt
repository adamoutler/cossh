package com.adamoutler.ssh.data

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class ConnectionProfileTest {

    @Test
    fun testProtocolSerialization() {
        val originalProfile = ConnectionProfile(
            id = "test-telnet-1",
            nickname = "Legacy Switch",
            host = "10.0.0.1",
            port = 23,
            username = "admin",
            authType = AuthType.PASSWORD,
            protocol = Protocol.TELNET,
            password = "password123".toByteArray() // @Transient field
        )

        // Serialize
        val jsonString = Json.encodeToString(originalProfile)

        // Deserialize
        val restoredProfile = Json.decodeFromString<ConnectionProfile>(jsonString)

        // Assert Protocol is restored
        assertEquals(Protocol.TELNET, restoredProfile.protocol)
        assertEquals("Legacy Switch", restoredProfile.nickname)
        assertEquals("10.0.0.1", restoredProfile.host)
        assertEquals(23, restoredProfile.port)
        assertEquals(AuthType.PASSWORD, restoredProfile.authType)
        
        // Assert the new protocol does not break existing data
        assertEquals(originalProfile.id, restoredProfile.id)
    }

    @Test
    fun testLegacyDeserializationDefaultsToSsh() {
        val legacyJson = """{"id":"test-legacy","nickname":"Old Server","host":"10.0.0.1","port":22,"username":"admin","authType":"PASSWORD","keyReference":0}"""
        val restoredProfile = Json { ignoreUnknownKeys = true }.decodeFromString<ConnectionProfile>(legacyJson)
        assertEquals(Protocol.SSH, restoredProfile.protocol)
        assertEquals("Old Server", restoredProfile.nickname)
    }
}