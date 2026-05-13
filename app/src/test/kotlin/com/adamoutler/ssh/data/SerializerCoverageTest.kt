package com.adamoutler.ssh.data

import com.adamoutler.ssh.backup.BackupPayload
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.Assert.assertNotNull

class SerializerCoverageTest {

    @Test
    fun testConnectionProfileSerializationCoverage() {
        val profile = ConnectionProfile(
            id = "test-id",
            nickname = "test-nick",
            host = "localhost",
            port = 22,
            protocol = Protocol.SSH,
            username = "root",
            authType = AuthType.PASSWORD,
            sortOrder = 1,
            sshKeyPasswordReferenceId = "ref",
            identityId = "id_ref",
            fontSize = 12,
            folderId = "folder_1",
            envVars = mapOf("KEY" to "VAL"),
            portForwards = listOf(PortForwardConfig(type = PortForwardType.LOCAL, localPort = 8080, remoteHost = "localhost", remotePort = 80)),
            initialDirectory = "/tmp",
            terminalInputState = 1,
            keepScreenOnMode = KeepScreenOnMode.ALWAYS_ON
        )
        profile.password = "test".toByteArray()
        val json = Json.encodeToString(ConnectionProfile.serializer(), profile)
        assertNotNull(json)
        val decoded = Json.decodeFromString(ConnectionProfile.serializer(), json)
        assertNotNull(decoded)
        
        // Minimal JSON to trigger defaults in deserializer
        val minimalJson = """{"id":"min-id","nickname":"min-nick","host":"min-host"}"""
        val decodedMin = Json { ignoreUnknownKeys = true }.decodeFromString(ConnectionProfile.serializer(), minimalJson)
        assertNotNull(decodedMin)
    }

    @Test
    fun testIdentityProfileSerializationCoverage() {
        val identity = IdentityProfile(
            id = "test-id",
            name = "test-identity",
            username = "root",
            publicKey = "pub",
            authType = AuthType.KEY
        )
        identity.password = "test".toByteArray()
        identity.privateKey = "key".toByteArray()

        val json = Json.encodeToString(IdentityProfile.serializer(), identity)
        assertNotNull(json)
        val decoded = Json.decodeFromString(IdentityProfile.serializer(), json)
        assertNotNull(decoded)

        // Minimal JSON
        val minimalJson = """{"id":"min-id","name":"min-name","username":"min-user"}"""
        val decodedMin = Json { ignoreUnknownKeys = true }.decodeFromString(IdentityProfile.serializer(), minimalJson)
        assertNotNull(decodedMin)
    }

    @Test
    fun testBackupPayloadSerializationCoverage() {
        val payload = BackupPayload(
            version = 1,
            profiles = listOf(),
            profilePasswords = mapOf("a" to "b"),
            identities = listOf(),
            identityPasswords = mapOf("c" to "d"),
            identityPrivateKeys = mapOf("e" to "f")
        )
        val json = Json.encodeToString(BackupPayload.serializer(), payload)
        assertNotNull(json)
        val decoded = Json.decodeFromString(BackupPayload.serializer(), json)
        assertNotNull(decoded)

        // Minimal JSON
        val minimalJson = """{"version":2,"profiles":[],"profilePasswords":{}}"""
        val decodedMin = Json { ignoreUnknownKeys = true }.decodeFromString(BackupPayload.serializer(), minimalJson)
        assertNotNull(decodedMin)
    }

    @Test
    fun testPortForwardConfigSerializationCoverage() {
        val config = PortForwardConfig(type = PortForwardType.LOCAL, localPort = 8080, remoteHost = "localhost", remotePort = 80)
        val json = Json.encodeToString(PortForwardConfig.serializer(), config)
        assertNotNull(json)
        val decoded = Json.decodeFromString(PortForwardConfig.serializer(), json)
        assertNotNull(decoded)

        // Minimal JSON
        val minimalJson = """{"type":"LOCAL","localPort":8080,"remoteHost":"localhost","remotePort":80}"""
        val decodedMin = Json { ignoreUnknownKeys = true }.decodeFromString(PortForwardConfig.serializer(), minimalJson)
        assertNotNull(decodedMin)
    }
}
