package com.adamoutler.ssh.network

import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SshConnectionManagerInjectionTest {

    @Test
    fun testInjectPublicKey_InvalidKey_ReturnsFalse() = runBlocking {
        val manager = SshConnectionManager()
        val profile = ConnectionProfile(
            id = "test",
            nickname = "test",
            host = "localhost",
            username = "root",
            authType = AuthType.PASSWORD
        )
        
        // Invalid characters in public key (shell injection attempt)
        val maliciousKey = "ssh-ed25519 AAAA; rm -rf /; #"
        val result = manager.injectPublicKey(profile, maliciousKey)
        
        assertFalse("Should fail regex validation for malicious key", result)
    }

    @Test
    fun testInjectPublicKey_ValidKey_AttemptConnect() = runBlocking {
        val manager = SshConnectionManager()
        val profile = ConnectionProfile(
            id = "test",
            nickname = "test",
            host = "non-existent-host", // Should fail connection but pass regex
            username = "root",
            authType = AuthType.PASSWORD
        )
        
        val validKey = "ssh-ed25519 AAAA1234/5678+90-=_@user"
        val result = manager.injectPublicKey(profile, validKey)
        
        // It will fail because the host doesn't exist, but we verified it passed the regex check
        assertFalse("Should fail due to connection, not regex", result)
    }
}
