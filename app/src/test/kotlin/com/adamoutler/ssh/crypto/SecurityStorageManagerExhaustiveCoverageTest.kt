package com.adamoutler.ssh.crypto

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.IdentityProfile
import com.adamoutler.ssh.data.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecurityStorageManagerExhaustiveCoverageTest {

    @Test
    fun testSecurityStorageManager_ComprehensiveOperations() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = app.getSharedPreferences("test_sec_comprehensive", 0)
        val manager = SecurityStorageManager(app, prefs)

        // 1. Save profile with null password
        val profileNoPass = ConnectionProfile(
            id = "prof-no-pass",
            nickname = "No Pass Server",
            host = "127.0.0.1",
            port = 22,
            username = "user1",
            authType = AuthType.KEY,
            protocol = Protocol.SSH,
            password = null,
        )
        manager.saveProfile(profileNoPass)

        val retrievedNoPass = manager.getProfile("prof-no-pass")
        assertNotNull(retrievedNoPass)
        assertNull(retrievedNoPass?.password)

        // 2. Save profile with password
        val profileWithPass = ConnectionProfile(
            id = "prof-with-pass",
            nickname = "Pass Server",
            host = "127.0.0.1",
            port = 22,
            username = "user2",
            authType = AuthType.PASSWORD,
            protocol = Protocol.SSH,
            password = "SecretPassword123".toByteArray(Charsets.UTF_8),
        )
        manager.saveProfile(profileWithPass)

        val retrievedWithPass = manager.getProfile("prof-with-pass")
        assertNotNull(retrievedWithPass)
        assertNotNull(retrievedWithPass?.password)
        assertEquals("SecretPassword123", String(retrievedWithPass!!.password!!, Charsets.UTF_8))

        // 3. getAllKeys()
        prefs.edit().putString("key_12345", "test-key-content").commit()
        val allKeys = manager.getAllKeys()
        assertTrue(allKeys.contains("key_12345"))

        // 4. getAllProfiles()
        val allProfiles = manager.getAllProfiles()
        assertTrue(allProfiles.any { it.id == "prof-no-pass" })
        assertTrue(allProfiles.any { it.id == "prof-with-pass" })

        // 5. Corrupt JSON in prefs to test SerializationException/IllegalArgumentException branches
        prefs.edit().putString("corrupt-profile-id", "NOT_A_VALID_JSON{:::").commit()
        val nullProfile = manager.getProfile("corrupt-profile-id")
        assertNull(nullProfile)

        // getAllProfiles skips corrupt entries
        val profilesWithCorrupt = manager.getAllProfiles()
        assertNotNull(profilesWithCorrupt)

        // 6. deleteProfile()
        manager.deleteProfile("prof-no-pass")
        assertNull(manager.getProfile("prof-no-pass"))

        // 7. Non-existent profile
        assertNull(manager.getProfile("non-existent-id"))

        // 8. resetInvalidatedKeys()
        manager.resetInvalidatedKeys()
    }

    @Test
    fun testIdentityStorageManager_ComprehensiveOperations() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = app.getSharedPreferences("test_id_comprehensive", 0)
        val manager = IdentityStorageManager(app, prefs)

        // 1. Save identity with all nulls
        val identityNull = IdentityProfile(
            id = UUID.randomUUID().toString(),
            name = "Null Identity",
            username = "user1",
            publicKey = "ssh-rsa AAAA...",
            password = null,
            privateKey = null,
        )
        manager.saveIdentity(identityNull)

        val retrievedNull = manager.getIdentity(identityNull.id)
        assertNotNull(retrievedNull)
        assertNull(retrievedNull?.password)
        assertNull(retrievedNull?.privateKey)

        // 2. Save identity with password and private key
        val identityFull = IdentityProfile(
            id = UUID.randomUUID().toString(),
            name = "Full Identity",
            username = "user2",
            publicKey = "ssh-ed25519 AAAA...",
            password = "IdentityPassword".toByteArray(Charsets.UTF_8),
            privateKey = "PRIVATE_KEY_BYTES".toByteArray(Charsets.UTF_8),
            authType = AuthType.KEY,
        )
        manager.saveIdentity(identityFull)

        val retrievedFull = manager.getIdentity(identityFull.id)
        assertNotNull(retrievedFull)
        assertEquals("IdentityPassword", String(retrievedFull!!.password!!, Charsets.UTF_8))
        assertEquals("PRIVATE_KEY_BYTES", String(retrievedFull.privateKey!!, Charsets.UTF_8))

        // Volatile sanitization & equals / hashCode
        assertTrue(identityFull == identityFull)
        assertTrue(identityFull != identityNull)
        assertTrue(identityFull.hashCode() != identityNull.hashCode())
        val cloneFull = identityFull.copy()
        cloneFull.password = "IdentityPassword".toByteArray(Charsets.UTF_8)
        cloneFull.privateKey = "PRIVATE_KEY_BYTES".toByteArray(Charsets.UTF_8)
        assertTrue(identityFull == cloneFull)
        identityFull.clearSensitiveData()

        // 3. getAllIdentities()
        val allIds = manager.getAllIdentities()
        assertTrue(allIds.any { it.id == identityNull.id })
        assertTrue(allIds.any { it.id == identityFull.id })

        // 4. Corrupt JSON in prefs
        prefs.edit().putString("corrupt-identity-id", "{invalid-json-content").commit()
        assertNull(manager.getIdentity("corrupt-identity-id"))
        val allWithCorrupt = manager.getAllIdentities()
        assertNotNull(allWithCorrupt)

        // 5. deleteIdentity()
        manager.deleteIdentity(identityNull.id)
        assertNull(manager.getIdentity(identityNull.id))

        // 6. Non-existent identity
        assertNull(manager.getIdentity("does-not-exist"))
    }
}
