package com.adamoutler.ssh.crypto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.IdentityProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class IdentityStorageManagerTest {

    private lateinit var context: Context
    private lateinit var storageManager: IdentityStorageManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val testPrefs = context.getSharedPreferences("test_identity_prefs", Context.MODE_PRIVATE)
        storageManager = IdentityStorageManager(context, testPrefs)
    }

    @Test
    fun testSaveAndRetrieveIdentity() {
        val passwordBytes = "mypassword".toByteArray()
        val privateKeyBytes = "fake-private-key-data".toByteArray()
        val identity = IdentityProfile(
            id = "identity-1",
            name = "My Identity",
            username = "adam",
            password = passwordBytes,
            privateKey = privateKeyBytes,
            publicKey = "ssh-ed25519 AAAA...",
            authType = AuthType.KEY,
        )

        storageManager.saveIdentity(identity)

        val retrieved = storageManager.getIdentity("identity-1")!!
        assertNotNull(retrieved)
        assertEquals(identity.name, retrieved.name)
        assertEquals(identity.username, retrieved.username)
        assertEquals(identity.publicKey, retrieved.publicKey)
        assertNotNull(retrieved.password)
        assertEquals("mypassword", String(retrieved.password!!))
        assertNotNull(retrieved.privateKey)
        assertEquals("fake-private-key-data", String(retrieved.privateKey!!))

        // Test volatile state sanitization
        identity.clearSensitiveData()
        assertEquals(0.toByte(), identity.password!![0])
        assertEquals(0.toByte(), identity.privateKey!![0])
    }

    @Test
    fun testGetAllIdentities() {
        val id1 = IdentityProfile(name = "ID 1", username = "user1")
        val id2 = IdentityProfile(name = "ID 2", username = "user2", password = "p2".toByteArray())

        storageManager.saveIdentity(id1)
        storageManager.saveIdentity(id2)

        val all = storageManager.getAllIdentities()
        assertTrue(all.size >= 2)
        assertNotNull(all.find { it.name == "ID 1" })
        assertNotNull(all.find { it.name == "ID 2" })
    }

    @Test
    fun testDeleteIdentity() {
        val id = IdentityProfile(id = "to-delete", name = "Delete Me", username = "gone")
        storageManager.saveIdentity(id)
        assertNotNull(storageManager.getIdentity("to-delete"))

        storageManager.deleteIdentity("to-delete")
        assertNull(storageManager.getIdentity("to-delete"))
    }

    @Test
    fun testResetInvalidatedKeys() {
        storageManager.resetInvalidatedKeys()
    }

    @Test
    fun testDecryptNullSensitiveData() {
        val id = IdentityProfile(id = "no-sensitive", name = "No Sensitive", username = "test")
        storageManager.saveIdentity(id)
        val retrieved = storageManager.getIdentity("no-sensitive")
        assertNull(retrieved?.password)
        assertNull(retrieved?.privateKey)
    }

    @Test
    fun testGetAllIdentitiesSkipsPwdAndKey() {
        storageManager.encryptedPrefs.edit().putString("test_id_pwd", "fake_pwd").apply()
        storageManager.encryptedPrefs.edit().putString("test_id_key", "fake_key").apply()
        val allIdentities = storageManager.getAllIdentities()
        assertTrue(allIdentities.none { it.id == "test_id_pwd" || it.id == "test_id_key" })
    }

    @Test
    fun testGetIdentityWithCorruptedData() {
        storageManager.encryptedPrefs.edit().putString("corrupt_id", "{ invalid json }").apply()
        val profile = storageManager.getIdentity("corrupt_id")
        assertNull(profile)

        val allProfiles = storageManager.getAllIdentities()
        assertTrue(allProfiles.none { it.id == "corrupt_id" })
    }

    @Test
    fun testDefaultInitialization() {
        // Test that without injectedPrefs, it initializes correctly (falls back to Robolectric preferences in this environment)
        val defaultStorageManager = IdentityStorageManager(context, null)
        assertNotNull(defaultStorageManager.encryptedPrefs)
    }
}
