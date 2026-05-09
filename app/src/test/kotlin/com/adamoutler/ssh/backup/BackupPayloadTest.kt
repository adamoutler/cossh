package com.adamoutler.ssh.backup

import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.IdentityProfile
import com.adamoutler.ssh.data.Protocol
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Base64

class BackupPayloadTest {

    @Test
    fun testSerializationAndDeserialization() {
        val p1 = ConnectionProfile(
            id = "test_profile_id",
            nickname = "test_profile",
            host = "localhost",
            port = 22,
            protocol = Protocol.SSH,
            username = "user",
            authType = AuthType.PASSWORD,
        )

        val i1 = IdentityProfile(
            id = "test_id",
            name = "test_identity",
            username = "user2",
            authType = AuthType.KEY
        )

        val profilePasswords = mapOf("test_profile_id" to Base64.getEncoder().encodeToString("password".toByteArray()))
        val identityPasswords = mapOf("test_id" to Base64.getEncoder().encodeToString("keypass".toByteArray()))
        val identityPrivateKeys = mapOf("test_id" to Base64.getEncoder().encodeToString("privatekey".toByteArray()))

        val payload = BackupPayload(
            version = 2,
            profiles = listOf(p1),
            profilePasswords = profilePasswords,
            identities = listOf(i1),
            identityPasswords = identityPasswords,
            identityPrivateKeys = identityPrivateKeys
        )

        val jsonString = Json.encodeToString(payload)

        val deserialized = Json.decodeFromString<BackupPayload>(jsonString)
        
        assertEquals(payload.version, deserialized.version)
        assertEquals(payload.profiles.size, deserialized.profiles.size)
        assertEquals(payload.profiles[0].id, deserialized.profiles[0].id)
        
        assertEquals(payload.profilePasswords, deserialized.profilePasswords)
        
        assertEquals(payload.identities.size, deserialized.identities.size)
        assertEquals(payload.identities[0].id, deserialized.identities[0].id)
        
        assertEquals(payload.identityPasswords, deserialized.identityPasswords)
        assertEquals(payload.identityPrivateKeys, deserialized.identityPrivateKeys)
    }
}
