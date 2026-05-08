package com.adamoutler.ssh.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ByteArrayAsBase64SerializerTest {

    @Test
    fun testSerialization() {
        val originalBytes = "Hello World".toByteArray()
        // Just testing the serializer directly since it's not currently used on a field
        val jsonString = Json.encodeToString(ByteArrayAsBase64Serializer, originalBytes)
        
        // Base64 encoded "Hello World" is "SGVsbG8gV29ybGQ="
        // kotlinx.serialization will wrap it in quotes for JSON string
        assertEquals("\"SGVsbG8gV29ybGQ=\"", jsonString)
    }

    @Test
    fun testDeserialization() {
        val jsonString = "\"SGVsbG8gV29ybGQ=\""
        val decodedBytes = Json.decodeFromString(ByteArrayAsBase64Serializer, jsonString)
        
        assertArrayEquals("Hello World".toByteArray(), decodedBytes)
    }
}
