package com.adamoutler.ssh.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class CommandSnippetSerializationTest {
    @Test
    fun testSerializationAndDeserialization() {
        val snippet = CommandSnippet(
            id = UUID.randomUUID().toString(),
            name = "Restart Server",
            command = "sudo systemctl restart nginx",
            autoSend = true,
            requireAuth = true
        )
        val jsonString = Json.encodeToString(snippet)
        val deserialized = Json.decodeFromString<CommandSnippet>(jsonString)
        
        assertEquals(snippet.id, deserialized.id)
        assertEquals(snippet.name, deserialized.name)
        assertEquals(snippet.command, deserialized.command)
        assertEquals(snippet.autoSend, deserialized.autoSend)
        assertEquals(snippet.requireAuth, deserialized.requireAuth)
    }

    @Test
    fun testConnectionProfileWithSnippets() {
        val snippets = listOf(
            CommandSnippet("1", "Test1", "echo 1", true, false),
            CommandSnippet("2", "Test2", "echo 2", false, true)
        )
        val profile = ConnectionProfile(
            id = "test",
            nickname = "Test Profile",
            host = "localhost",
            port = 22,
            username = "root",
            authType = AuthType.PASSWORD,
            commandSnippets = snippets
        )
        val jsonString = Json.encodeToString(profile)
        val deserialized = Json.decodeFromString<ConnectionProfile>(jsonString)

        assertEquals(2, deserialized.commandSnippets?.size)
        assertEquals("Test1", deserialized.commandSnippets?.get(0)?.name)
        assertEquals("Test2", deserialized.commandSnippets?.get(1)?.name)
        assertEquals(profile, deserialized)
    }
}
