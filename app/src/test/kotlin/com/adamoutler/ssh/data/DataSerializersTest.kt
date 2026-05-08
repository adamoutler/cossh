package com.adamoutler.ssh.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Test

class DataSerializersTest {

    @Test
    fun `test ConnectionProfile serialization and deserialization`() {
        val original = ConnectionProfile(
            id = "test-id",
            nickname = "Test Profile",
            host = "localhost",
            port = 22,
            username = "user",
            authType = AuthType.PASSWORD,
            protocol = Protocol.SSH,
            password = "password".toByteArray(),
            portForwards = listOf(
                PortForwardConfig(PortForwardType.LOCAL, 8080, "127.0.0.1", 80)
            )
        )

        val jsonString = Json.encodeToString(original)
        val deserialized = Json.decodeFromString<ConnectionProfile>(jsonString)

        assertEquals(original.id, deserialized.id)
        assertEquals(original.nickname, deserialized.nickname)
        assertEquals(original.host, deserialized.host)
        assertEquals(original.port, deserialized.port)
        assertEquals(original.username, deserialized.username)
        assertEquals(original.authType, deserialized.authType)
        assertEquals(original.protocol, deserialized.protocol)
        // ByteArray equivalence check - password is @Transient so it will be null
        assertEquals(null, deserialized.password)
        assertEquals(original.portForwards.size, deserialized.portForwards.size)
        assertEquals(original.portForwards[0].type, deserialized.portForwards[0].type)
        assertEquals(original.portForwards[0].localPort, deserialized.portForwards[0].localPort)
        assertEquals(original.portForwards[0].remoteHost, deserialized.portForwards[0].remoteHost)
        assertEquals(original.portForwards[0].remotePort, deserialized.portForwards[0].remotePort)
    }

    @Test
    fun `test IdentityProfile serialization and deserialization`() {
        val original = IdentityProfile(
            id = "id-1",
            name = "Test Identity",
            privateKey = "private-key-data".toByteArray(),
            publicKey = "public-key-data",
            password = "password".toByteArray(),
            username = "test-user"
        )

        val jsonString = Json.encodeToString(original)
        val deserialized = Json.decodeFromString<IdentityProfile>(jsonString)

        assertEquals(original.id, deserialized.id)
        assertEquals(original.name, deserialized.name)
        assertEquals(original.username, deserialized.username)
        // IdentityProfile byte arrays are transient so they become null when deserialized unless custom serialized
        assertEquals(null, deserialized.privateKey)
        assertEquals(original.publicKey, deserialized.publicKey)
        assertEquals(null, deserialized.password)
    }

    @Test
    fun `test ConnectionProfile partial deserialization`() {
        // JSON string with only required fields (if any) or missing optional fields
        // In Kotlinx.serialization, fields with default values can be omitted from JSON
        val jsonString = """{"id":"test-partial","nickname":"Partial Profile","host":"localhost","username":"user"}"""
        val deserialized = Json { ignoreUnknownKeys = true }.decodeFromString<ConnectionProfile>(jsonString)

        assertEquals("test-partial", deserialized.id)
        assertEquals("Partial Profile", deserialized.nickname)
        assertEquals("localhost", deserialized.host)
        assertEquals(22, deserialized.port) // default
        assertEquals(AuthType.PASSWORD, deserialized.authType) // default
        assertEquals(Protocol.SSH, deserialized.protocol) // default
        assertEquals(null, deserialized.password) // default
    }

    @Test
    fun `test IdentityProfile partial deserialization`() {
        val jsonString = """{"id":"id-partial","name":"Partial Identity","username":"user"}"""
        val deserialized = Json { ignoreUnknownKeys = true }.decodeFromString<IdentityProfile>(jsonString)

        assertEquals("id-partial", deserialized.id)
        assertEquals("Partial Identity", deserialized.name)
        assertEquals("user", deserialized.username)
        assertEquals(null, deserialized.publicKey) // default
    }
}
