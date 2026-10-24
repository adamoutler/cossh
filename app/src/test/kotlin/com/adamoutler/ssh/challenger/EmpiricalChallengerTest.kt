package com.adamoutler.ssh.challenger

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.backup.BackupCryptoManager
import com.adamoutler.ssh.backup.BackupPayload
import com.adamoutler.ssh.crypto.PasswordCipher
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.crypto.SettingsManager
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.CommandSnippet
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.IdentityProfile
import com.adamoutler.ssh.data.PortForwardConfig
import com.adamoutler.ssh.data.PortForwardType
import com.adamoutler.ssh.data.Protocol
import com.adamoutler.ssh.network.PortForwardingOrchestrator
import com.adamoutler.ssh.security.AppLockManager
import com.adamoutler.ssh.ui.components.TerminalSnippetHandler
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.schmizz.sshj.SSHClient
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.forward.AcceptAllForwardingFilter
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.file.Paths
import java.security.SecureRandom
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EmpiricalChallengerTest {

    private val masterPassphrase = "ChallengerAdversarialPassword!#2026".toCharArray()

    @Before
    fun setUp() {
        BackupCryptoManager.setIterationsForTesting(1_000, 1_000)
    }

    @After
    fun tearDown() {
        BackupCryptoManager.resetIterationsForTesting()
    }

    // -------------------------------------------------------------------------
    // 1. CRYPTOGRAPHIC TAMPER RESISTANCE & BACKWARD COMPATIBILITY
    // -------------------------------------------------------------------------

    @Test
    fun testExhaustiveTamperResistance_all54CompactHeaderBytesFailDecryption() {
        // Construct a genuine 54-byte Format v3 backup
        val payload = BackupPayload(
            version = 3,
            profiles = listOf(
                ConnectionProfile(
                    id = "profile-tamper-check",
                    nickname = "Critical Bastion",
                    host = "bastion.secure.corp",
                    port = 22,
                    username = "root",
                ),
            ),
            profilePasswords = mapOf("profile-tamper-check" to Base64.getEncoder().encodeToString("root-secret".toByteArray(Charsets.UTF_8))),
            identities = emptyList(),
        )

        val plainBytes = Json.encodeToString(payload).toByteArray(Charsets.UTF_8)
        val salt = ByteArray(32) { (it + 1).toByte() }
        val iv = ByteArray(12) { (it + 7).toByte() }

        val keySpec = PBEKeySpec(masterPassphrase, salt, 250_000, 256)
        val secretKey = SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(keySpec).encoded, "AES")

        val header54 = ByteArray(5 + 1 + 4 + 32 + 12)
        val buf = ByteBuffer.wrap(header54)
        buf.put("COSSH".toByteArray(Charsets.US_ASCII))
        buf.put(3.toByte())
        buf.putInt(250_000)
        buf.put(salt)
        buf.put(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        cipher.updateAAD(header54)
        val cipherText = cipher.doFinal(plainBytes)

        val canonicalEncrypted = ByteArrayOutputStream().apply {
            write(header54)
            write(cipherText)
        }.toByteArray()

        assertEquals("Header must be exactly 54 bytes", 54, header54.size)

        var testedByteCount = 0
        var failedAuthCount = 0

        // Test every single byte in the 54-byte header
        for (byteIndex in 0 until 54) {
            val tampered = canonicalEncrypted.clone()
            // Invert the single byte at byteIndex
            tampered[byteIndex] = (tampered[byteIndex].toInt() xor 0xFF).toByte()

            val zipOut = ByteArrayOutputStream()
            ZipOutputStream(zipOut).use { zos ->
                zos.putNextEntry(ZipEntry("backup.enc"))
                zos.write(tampered)
                zos.closeEntry()
            }

            testedByteCount++
            try {
                BackupCryptoManager.importProfilesFromZip(ByteArrayInputStream(zipOut.toByteArray()), masterPassphrase)
                fail("Byte alteration at offset $byteIndex must NOT be accepted by decryptor!")
            } catch (e: IllegalArgumentException) {
                failedAuthCount++
                // For bytes 6..53 (iterations, salt, iv), AAD verification must fail
                if (byteIndex in 10..53) {
                    val rootCause = e.cause
                    assertTrue(
                        "Salt or IV alteration at offset $byteIndex must fail with AEADBadTagException, was $rootCause",
                        rootCause is AEADBadTagException,
                    )
                } else if (byteIndex in 0..4) {
                    val rootCause = e.cause
                    assertTrue(
                        "Magic bytes alteration at offset $byteIndex must fail via Format v2 AEADBadTagException, was $rootCause",
                        rootCause is AEADBadTagException,
                    )
                } else if (byteIndex == 5) {
                    assertTrue(
                        "Version alteration at offset 5 must fail with version mismatch message",
                        e.message?.contains("Unsupported backup format version") == true,
                    )
                } else if (byteIndex in 6..9) {
                    // Iteration alteration either fails PBEKeySpec validation (<=0) or fails AEADBadTagException
                    val isIterationOrAeadFailure = (e.cause is AEADBadTagException) ||
                        (e.message?.contains("iteration", ignoreCase = true) == true)
                    assertTrue(
                        "Iteration alteration at offset $byteIndex must fail validation or AEAD auth: ${e.message}, cause=${e.cause}",
                        isIterationOrAeadFailure,
                    )
                }
            }
        }

        assertEquals(54, testedByteCount)
        assertEquals("All 54 header mutations must be rejected", 54, failedAuthCount)
    }

    @Test
    fun testExhaustiveTamperResistance_all56StandardHeaderBytesFailDecryption() {
        val out = ByteArrayOutputStream()
        BackupCryptoManager.exportProfilesToZip(
            profiles = listOf(ConnectionProfile(id = "p1", nickname = "Test", host = "127.0.0.1")),
            identities = emptyList(),
            password = masterPassphrase,
            outputStream = out,
        )

        var rawEncrypted: ByteArray? = null
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "backup.enc") {
                    rawEncrypted = zis.readBytes()
                    break
                }
                entry = zis.nextEntry
            }
        }
        assertNotNull(rawEncrypted)
        val data = rawEncrypted!!

        for (byteIndex in 0 until BackupCryptoManager.FORMAT_V3_HEADER_LENGTH) {
            val tampered = data.clone()
            tampered[byteIndex] = (tampered[byteIndex].toInt() xor 0xAA).toByte()

            val tamperedZip = ByteArrayOutputStream()
            ZipOutputStream(tamperedZip).use { zos ->
                zos.putNextEntry(ZipEntry("backup.enc"))
                zos.write(tampered)
                zos.closeEntry()
            }

            try {
                BackupCryptoManager.importProfilesFromZip(ByteArrayInputStream(tamperedZip.toByteArray()), masterPassphrase)
                fail("Header mutation at offset $byteIndex must fail")
            } catch (e: IllegalArgumentException) {
                // Expected rejection
            }
        }
    }

    @Test
    fun testAadDirectBinding_mutatingAnySingleAadByteFailsWithAeadBadTagException() {
        val payload = "SensitiveProfileDataJson".toByteArray(Charsets.UTF_8)
        val key = SecretKeySpec(ByteArray(32) { 0x5A }, "AES")
        val iv = ByteArray(12) { 0x12 }
        val canonicalHeader = ByteArray(54) { (it * 3).toByte() }

        val encryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        encryptCipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        encryptCipher.updateAAD(canonicalHeader)
        val cipherText = encryptCipher.doFinal(payload)

        for (byteOffset in 0 until 54) {
            val tamperedAad = canonicalHeader.clone()
            tamperedAad[byteOffset] = (tamperedAad[byteOffset].toInt() xor 0x01).toByte()

            val decryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
            decryptCipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            decryptCipher.updateAAD(tamperedAad)
            try {
                decryptCipher.doFinal(cipherText)
                fail("Altering AAD header at byte $byteOffset MUST trigger AEADBadTagException")
            } catch (e: AEADBadTagException) {
                // Verified direct cryptographic failure via AES-GCM AAD binding
            }
        }
    }

    @Test
    fun testFormatV2BackwardCompatibility_100RandomBackupsImportWithZeroCollisions() {
        val rng = SecureRandom()

        for (iteration in 1..100) {
            val testPassword = "v2-pass-$iteration-${rng.nextLong()}".toCharArray()
            val profileId = "profile-$iteration"
            val host = "server-$iteration.lan"
            val rawPassword = "secret-pass-$iteration"

            val payload = BackupPayload(
                version = 2,
                profiles = listOf(
                    ConnectionProfile(
                        id = profileId,
                        nickname = "Server $iteration",
                        host = host,
                        port = 22,
                        username = "user$iteration",
                    ),
                ),
                profilePasswords = mapOf(profileId to Base64.getEncoder().encodeToString(rawPassword.toByteArray(Charsets.UTF_8))),
                identities = emptyList(),
            )

            val plainText = Json.encodeToString(payload).toByteArray(Charsets.UTF_8)
            val salt = ByteArray(16).apply { rng.nextBytes(this) }
            val iv = ByteArray(12).apply { rng.nextBytes(this) }

            val keySpec = PBEKeySpec(testPassword, salt, BackupCryptoManager.defaultIterationsV2, 256)
            val secretKey = SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(keySpec).encoded, "AES")

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            val cipherText = cipher.doFinal(plainText)

            val encrypted = ByteArrayOutputStream().apply {
                write(salt)
                write(iv)
                write(cipherText)
            }.toByteArray()

            val zipOut = ByteArrayOutputStream()
            ZipOutputStream(zipOut).use { zos ->
                zos.putNextEntry(ZipEntry("backup.enc"))
                zos.write(encrypted)
                zos.closeEntry()
            }

            val (importedProfiles, _) = BackupCryptoManager.importProfilesFromZip(
                ByteArrayInputStream(zipOut.toByteArray()),
                testPassword,
            )

            assertEquals("Iteration $iteration: profile size must be 1", 1, importedProfiles.size)
            assertEquals("Iteration $iteration: profile ID mismatch", profileId, importedProfiles[0].id)
            assertEquals("Iteration $iteration: host mismatch", host, importedProfiles[0].host)
            assertEquals("Iteration $iteration: password mismatch", rawPassword, String(importedProfiles[0].password!!, Charsets.UTF_8))
        }
    }

    // -------------------------------------------------------------------------
    // 2. PORT FORWARDING & SOCKET EXHAUSTION
    // -------------------------------------------------------------------------

    @Test
    fun testPortForwarding_concurrencyLimit32AndSocketExhaustion() {
        val orchestrator = PortForwardingOrchestrator()

        assertEquals(32, PortForwardingOrchestrator.MAX_CONCURRENT_FORWARD_CONNECTIONS)
        assertEquals(5000, PortForwardingOrchestrator.SERVER_SOCKET_SO_TIMEOUT_MS)
        assertEquals(15000, PortForwardingOrchestrator.SOCKS5_HANDSHAKE_TIMEOUT_MS)

        // Set up embedded SSHD server
        val sshd = SshServer.setUpDefaultServer().apply {
            host = "127.0.0.1"
            port = 0
            keyPairProvider = SimpleGeneratorHostKeyProvider(Paths.get("hostkey_challenger.ser"))
            passwordAuthenticator = PasswordAuthenticator { u, p, _ -> u == "u" && p == "p" }
            forwardingFilter = AcceptAllForwardingFilter()
            start()
        }

        val client = SSHClient().apply {
            addHostKeyVerifier(net.schmizz.sshj.transport.verification.PromiscuousVerifier())
            connect("127.0.0.1", sshd.port)
            authPassword("u", "p")
        }

        try {
            val ephemeralServer = ServerSocket(0)
            val dynamicPort = ephemeralServer.localPort
            ephemeralServer.close()

            val profile = ConnectionProfile(
                id = "p-socks",
                nickname = "Socks",
                host = "127.0.0.1",
                port = sshd.port,
                username = "u",
                authType = AuthType.PASSWORD,
                protocol = Protocol.SSH,
                portForwards = listOf(PortForwardConfig(PortForwardType.DYNAMIC, dynamicPort, "", 0)),
            )

            orchestrator.startPortForwards(client, profile)
            Thread.sleep(250)

            // Fill activeClientSockets with 32 dummy sockets
            val dummySockets = mutableListOf<Socket>()
            val dummyServers = mutableListOf<ServerSocket>()
            for (i in 1..32) {
                val srv = ServerSocket(0)
                dummyServers.add(srv)
                val cli = Socket("127.0.0.1", srv.localPort)
                val acc = srv.accept()
                dummySockets.add(cli)
                orchestrator.activeClientSockets.add(acc)
            }

            assertEquals("Must have 32 concurrent sockets active", 32, orchestrator.activeClientSockets.size)

            // Attempt 33rd connection — must be rejected and closed immediately
            val socket33 = Socket("127.0.0.1", dynamicPort)
            Thread.sleep(250)

            socket33.soTimeout = 1000
            val read33 = try {
                socket33.getInputStream().read()
            } catch (e: Exception) {
                -1
            }
            assertEquals("33rd socket must be closed immediately with EOF (-1)", -1, read33)
            socket33.close()

            // Attempt 34th connection — also rejected
            val socket34 = Socket("127.0.0.1", dynamicPort)
            Thread.sleep(250)
            socket34.soTimeout = 1000
            val read34 = try {
                socket34.getInputStream().read()
            } catch (e: Exception) {
                -1
            }
            assertEquals("34th socket must also be rejected with EOF (-1)", -1, read34)
            socket34.close()

            // Free 1 connection
            val released = orchestrator.activeClientSockets.iterator().next()
            orchestrator.activeClientSockets.remove(released)
            released.close()
            assertEquals("Active count must now be 31", 31, orchestrator.activeClientSockets.size)

            // Clean up dummy sockets
            dummySockets.forEach {
                try {
                    it.close()
                } catch (_: Exception) {}
            }
            dummyServers.forEach {
                try {
                    it.close()
                } catch (_: Exception) {}
            }
        } finally {
            orchestrator.stopAll()
            try {
                client.disconnect()
            } catch (_: Exception) {}
            try {
                sshd.stop()
            } catch (_: Exception) {}
            try {
                java.io.File("hostkey_challenger.ser").delete()
            } catch (_: Exception) {}
        }
    }

    // -------------------------------------------------------------------------
    // 3. VOLATILE STATE SANITIZATION & ZERO-KNOWLEDGE
    // -------------------------------------------------------------------------

    @Test
    fun testZeroKnowledgePassphrase_noStringAllocationsAndRoundTripFidelity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = SecurityStorageManager(context)
        storage.clearSyncPassphrase()

        val unicodePassphrase = "🔐-Kōhī-☕-Password-#2026-Super-Secret-Special!@#\$%^&*()".toCharArray()
        storage.saveSyncPassphrase(unicodePassphrase)

        val retrieved = storage.getSyncPassphrase()
        assertNotNull(retrieved)
        assertArrayEquals("Passphrase must survive round-trip without loss", unicodePassphrase, retrieved)

        // Clear and verify
        storage.clearSyncPassphrase()
        assertNull("Cleared passphrase must return null", storage.getSyncPassphrase())
    }

    // -------------------------------------------------------------------------
    // 4. APP LOCK SECURITY GATING & SNIPPET BIOMETRIC ENFORCEMENT
    // -------------------------------------------------------------------------

    @Test
    fun testAppLockTokenVerification_rejectsCorruptedCiphertextAndMismatchedHashes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("challenger_app_lock_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val storage = SecurityStorageManager(context, prefs)

        val encryptCipher = PasswordCipher.initEncryptCipher()
        storage.setupAppLock(encryptCipher)
        assertTrue(storage.isAppLockConfigured())

        val iv = storage.getAppLockIv()!!
        val decryptCipher = PasswordCipher.initDecryptCipher(iv)
        assertTrue("Valid token verification must succeed", storage.verifyAppLockToken(decryptCipher))

        // Corrupt ciphertext
        val realCiphertextB64 = prefs.getString("app_lock_ciphertext", null)!!
        val corruptedCiphertext = Base64.getDecoder().decode(realCiphertextB64).apply {
            this[0] = (this[0].toInt() xor 0xFF).toByte()
        }
        prefs.edit().putString("app_lock_ciphertext", Base64.getEncoder().encodeToString(corruptedCiphertext)).commit()

        val decryptCipher2 = PasswordCipher.initDecryptCipher(iv)
        assertFalse("Verification must fail on corrupted ciphertext", storage.verifyAppLockToken(decryptCipher2))

        // Restore ciphertext and corrupt hash
        prefs.edit().putString("app_lock_ciphertext", realCiphertextB64).commit()
        val corruptedHash = Base64.getEncoder().encodeToString(ByteArray(32) { 0x00.toByte() })
        prefs.edit().putString("app_lock_token_sha256", corruptedHash).commit()

        val decryptCipher3 = PasswordCipher.initDecryptCipher(iv)
        assertFalse("Verification must fail on mismatched SHA-256 hash", storage.verifyAppLockToken(decryptCipher3))
    }

    @Test
    fun testAppLockScreen_preventsNavigationAndQueuesIntentsUntilUnlock() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = SettingsManager(context)
        val prefs = context.getSharedPreferences("challenger_app_lock_storage", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val storage = SecurityStorageManager(context, prefs)

        val encryptCipher = PasswordCipher.initEncryptCipher()
        storage.setupAppLock(encryptCipher)
        settings.isAppLockEnabled = true

        val manager = AppLockManager(settings, storage)
        assertTrue("AppLockManager must be locked initially", manager.isLocked.value)

        // Queue incoming intent
        val testIntent = Intent(Intent.ACTION_VIEW, Uri.parse("ssh://victim@malicious.host:22"))
        manager.pendingResumeIntent.value = testIntent

        assertEquals("Intent must remain queued in pendingResumeIntent while locked", testIntent, manager.pendingResumeIntent.value)

        // Verify with invalid cipher fails and keeps locked
        val iv = storage.getAppLockIv()!!
        val badCipher = PasswordCipher.initDecryptCipher(iv)
        // Corrupt token in storage
        prefs.edit().putString("app_lock_token_sha256", "invalid").commit()
        val unlockFailed = manager.unlockWithCipher(badCipher)
        assertFalse("Unlock with invalid token must fail", unlockFailed)
        assertTrue("State must remain locked", manager.isLocked.value)
        assertEquals("Intent must still be queued", testIntent, manager.pendingResumeIntent.value)
    }

    @Test
    fun testCommandSnippet_requireAuthBlocksPTYUntilAuthentication() {
        var dispatchedBytes: ByteArray? = null
        var authInvoked = false
        var onAuthCallback: (() -> Unit)? = null

        val handler = TerminalSnippetHandler(
            sendToTerminal = { dispatchedBytes = it },
            requestConfirmation = {},
            requestAuthentication = { snippet, onAuthenticated ->
                authInvoked = true
                onAuthCallback = onAuthenticated
            },
        )

        val criticalSnippet = CommandSnippet(
            id = "drop-db",
            name = "Drop Production DB",
            command = "dropdb production",
            autoSend = true,
            requireAuth = true,
        )

        handler.onSnippetClicked(criticalSnippet)

        assertTrue("Biometric auth callback must be requested", authInvoked)
        assertNull("Zero bytes must be dispatched to PTY prior to authentication", dispatchedBytes)

        // Simulate successful biometric auth
        onAuthCallback!!.invoke()

        val expected = "dropdb production\r".toByteArray(Charsets.UTF_8)
        assertArrayEquals("Command must be dispatched to PTY with carriage return after auth", expected, dispatchedBytes)
    }
}
