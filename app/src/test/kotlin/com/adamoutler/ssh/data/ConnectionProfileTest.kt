package com.adamoutler.ssh.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class ConnectionProfileTest {
    @Test
    fun testSerialization() {
        val profile = ConnectionProfile(
            id = "123",
            nickname = "Test",
            host = "localhost",
            port = 2222,
            protocol = Protocol.SSH,
            username = "admin",
            authType = AuthType.KEY,
            password = byteArrayOf(1, 2, 3), // Transient
            envVars = mapOf("TEST" to "VAL"),
            portForwards = listOf(PortForwardConfig(PortForwardType.LOCAL, 8080, "local", 80))
        )
        
        val jsonString = Json.encodeToString(profile)
        
        // Assert serialization works and transient data is excluded
        assertTrue(jsonString.contains(""""id":"123""""))
        assertTrue(jsonString.contains(""""host":"localhost""""))
        assertTrue(jsonString.contains(""""TEST":"VAL""""))
        assertTrue(jsonString.contains(""""portForwards":[{"type":"LOCAL","localPort":8080,"remoteHost":"local","remotePort":80}]"""))
        assertTrue(!jsonString.contains("password"))
        
        val deserialized = Json.decodeFromString<ConnectionProfile>(jsonString)
        
        assertEquals(profile.id, deserialized.id)
        assertEquals(profile.host, deserialized.host)
        assertEquals(profile.portForwards, deserialized.portForwards)
        assertEquals(profile.envVars, deserialized.envVars)
        // Password should be null after deserialization because it's transient
        assertEquals(null, deserialized.password)
    }

    @Test
    fun testEqualsAndHashCode() {
        val profile1 = ConnectionProfile("1", "test", "host", 22, Protocol.SSH, "user", AuthType.PASSWORD, password = byteArrayOf(1, 2, 3))
        val profile2 = ConnectionProfile("1", "test", "host", 22, Protocol.SSH, "user", AuthType.PASSWORD, password = byteArrayOf(1, 2, 3))
        val profile3 = ConnectionProfile("2", "test", "host", 22, Protocol.SSH, "user", AuthType.PASSWORD, password = byteArrayOf(1, 2, 3))
        val profile4 = ConnectionProfile("1", "test", "host", 22, Protocol.SSH, "user", AuthType.PASSWORD, password = null)

        assertEquals(profile1, profile1)
        assertEquals(profile1, profile2)
        assertNotEquals(profile1, profile3)
        assertNotEquals(profile1, profile4)
        assertNotEquals(profile4, profile1)
        assertNotEquals(profile1, null)
        assertNotEquals(profile1, Any())

        assertEquals(profile1.hashCode(), profile2.hashCode())
        assertNotEquals(profile1.hashCode(), profile3.hashCode())

        profile1.clearSensitiveData()
        assertEquals(0.toByte(), profile1.password!![0])
    }

    @Test
    fun testAuthTypeAndProtocol() {
        assertEquals(AuthType.PASSWORD, AuthType.valueOf("PASSWORD"))
        assertEquals(Protocol.SSH, Protocol.valueOf("SSH"))
    }

    @Test
    fun testSerializationWithDefaults() {
        val jsonString = """{"id":"123","nickname":"Test","host":"localhost","port":22,"protocol":"SSH","username":"admin","authType":"PASSWORD"}"""
        val deserialized = Json { ignoreUnknownKeys = true }.decodeFromString<ConnectionProfile>(jsonString)
        assertEquals("123", deserialized.id)
        assertEquals("Test", deserialized.nickname)
        assertEquals("localhost", deserialized.host)
        assertEquals(22, deserialized.port)
        assertEquals(Protocol.SSH, deserialized.protocol)
        assertEquals("admin", deserialized.username)
        assertEquals(AuthType.PASSWORD, deserialized.authType)
        // Check defaults
        assertEquals(emptyMap<String, String>(), deserialized.envVars)
        assertEquals(emptyList<PortForwardConfig>(), deserialized.portForwards)
        assertEquals(null, deserialized.identityId)
        assertEquals(null, deserialized.folderId)
        assertEquals(0, deserialized.sortOrder)
        assertEquals(null, deserialized.sshKeyPasswordReferenceId)
        assertEquals(null, deserialized.fontSize)
        assertEquals(null, deserialized.initialDirectory)
    }
}

class IdentityProfileTest {
    @Test
    fun testSerializationWithDefaults() {
        val p1 = IdentityProfile("1", "test", "user", null, byteArrayOf(2), "pub", AuthType.KEY)
        val jsonString = Json.encodeToString(p1)
        val deserialized = Json.decodeFromString<IdentityProfile>(jsonString)
        assertEquals(p1.id, deserialized.id)
        assertEquals(p1.name, deserialized.name)
        assertEquals(p1.username, deserialized.username)
        assertEquals(p1.publicKey, deserialized.publicKey)
        assertEquals(p1.authType, deserialized.authType)
    }

    @Test
    fun testEqualsAndHashCode() {
        val p1 = IdentityProfile("1", "test", "user", byteArrayOf(1), byteArrayOf(2), "pub", AuthType.KEY)
        val p2 = IdentityProfile("1", "test", "user", byteArrayOf(1), byteArrayOf(2), "pub", AuthType.KEY)
        val p3 = IdentityProfile("2", "test", "user", byteArrayOf(1), byteArrayOf(2), "pub", AuthType.KEY)

        assertEquals(p1, p2)
        assertNotEquals(p1, p3)
        assertEquals(p1.hashCode(), p2.hashCode())
        assertNotEquals(p1.hashCode(), p3.hashCode())

        p1.clearSensitiveData()
        assertEquals(0.toByte(), p1.password!![0])
    }
}
