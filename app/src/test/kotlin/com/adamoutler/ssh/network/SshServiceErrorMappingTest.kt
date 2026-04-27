package com.adamoutler.ssh.network

import org.junit.Assert.assertEquals
import org.junit.Test

class SshServiceErrorMappingTest {

    @Test
    fun testAuthenticationExhaustedMapping() {
        val originalException = Exception("Exhausted available authentication methods")
        val mappedMessage = SshService.mapExceptionMessage(originalException)
        
        assertEquals(
            "Connection failed", 
            mappedMessage
        )
    }

    @Test
    fun testOtherExceptionMapping() {
        val originalException = Exception("Connection refused")
        val mappedMessage = SshService.mapExceptionMessage(originalException)
        
        assertEquals(
            "Connection refused", 
            mappedMessage
        )
    }
}
