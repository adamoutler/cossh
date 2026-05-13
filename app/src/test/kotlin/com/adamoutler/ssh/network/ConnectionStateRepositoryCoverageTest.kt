package com.adamoutler.ssh.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class ConnectionStateRepositoryCoverageTest {

    @Test
    fun testRequestAuthPrompt() = runBlocking {
        val repo = ConnectionStateRepository
        
        launch {
            delay(100)
            repo.resolveAuthPrompt(AuthCredentials("user", "pass".toCharArray()))
        }

        val result = repo.requestAuthPrompt("profileId", requireUsername = true, isRetry = false)
        assertNotNull(result)
        assertEquals("user", result?.username)
        assertEquals("pass", String(result?.password!!))
        
        // Null resolve
        launch {
            delay(100)
            repo.resolveAuthPrompt(null)
        }
        val result2 = repo.requestAuthPrompt("profileId2", requireUsername = false, isRetry = true)
        assertNull(result2)
    }

    @Test
    fun testRequestPrompt() = runBlocking {
        val repo = ConnectionStateRepository
        
        launch {
            delay(100)
            repo.resolvePrompt(true)
        }

        val result = repo.requestPrompt("host", "old", "new", false)
        assertEquals(true, result)

        launch {
            delay(100)
            repo.resolvePrompt(false)
        }

        val result2 = repo.requestPrompt("host2", null, "new2", true)
        assertEquals(false, result2)
    }
}
