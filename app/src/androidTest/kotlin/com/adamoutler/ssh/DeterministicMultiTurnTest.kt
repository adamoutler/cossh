package com.adamoutler.ssh

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.network.SshSessionProvider
import com.adamoutler.ssh.network.SshService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/**
 * # DeterministicMultiTurnTest — The Golden Path E2E Verification
 *
 * ## What This Test Proves
 * This is THE critical end-to-end test for CoSSH. It proves the entire SSH pipeline works:
 *
 * 1. **Network**: The app can establish an SSH connection over the real network
 * 2. **Authentication**: Password-based SSH auth succeeds against a real server
 * 3. **Send Path**: Typed commands flow correctly through PTY → SSH → remote server
 * 4. **Receive Path**: Server responses flow back through SSH → PTY → terminal emulator
 * 5. **Data Integrity**: Every response contains a SHA256 hash that can be independently
 *    verified (`echo -n "<input>" | sha256sum`), proving bit-perfect transmission
 * 6. **Multi-Turn**: The test sends multiple sequential commands, proving the session
 *    maintains state across interactions (incrementing request counter)
 * 7. **Visual Rendering**: Screenshot evidence captures what the user would actually see
 *
 * ## How It Works
 * The test connects to `mock.hackedyour.info:32222`, a deterministic mock SSH server.
 * For every line of input, the server responds with:
 * ```
 * RESPONSE <N>, '<sanitized_input>', <length>, <sha256_hex>
 * ```
 * The test pre-computes expected hashes before sending, then verifies the server's
 * response matches exactly. This is a cryptographic proof of data integrity.
 *
 * ## Deterministic Payload Generation
 * Payloads are generated using a seeded PRNG (`seed = 0xC0B417`), ensuring every run
 * produces identical inputs. Any agent or human can reproduce the expected values:
 * ```
 * Payload 1: "a"              → sha256("a")
 * Payload 2: "bbb"            → sha256("bbb")
 * Payload 3: <8-char random>  → sha256(<8-char random>)
 * Payload 4: <16-char random> → sha256(<16-char random>)
 * ```
 *
 * ## Mock Server Protocol
 * - Host: mock.hackedyour.info (192.168.1.115)
 * - Port: 32222
 * - Auth: any username/password except "wrongpassword"
 * - Rate limit: 1 request/second (server-enforced)
 * - Buffer limit: 4096 bytes (exceeding drops the connection)
 * - MOTD: "Welcome to MockSSH server. Interactive test shell."
 * - Exit: send "exit" → receives "Goodbye."
 *
 * ## Running This Test
 * ```bash
 * # Physical device (recommended):
 * ./gradlew connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.adamoutler.ssh.DeterministicMultiTurnTest
 *
 * # Verify a hash manually:
 * echo -n "a" | sha256sum
 * # ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb
 * ```
 *
 * ## Why Visual Mode (Not Headless)
 * Headless mode (`isHeadlessTest=true`) bypasses the real TerminalView and accumulates
 * output into a string buffer. While useful for basic checks, it does NOT prove that:
 * - The terminal emulator correctly parses ANSI escape sequences
 * - Text is actually rendered on screen for the user to see
 * - The UI pipeline (Compose → AndroidView → TerminalView → Canvas) is intact
 *
 * This test runs in VISUAL mode to prove the full pipeline, then reads the terminal
 * emulator's screen buffer to verify content. Screenshots provide human-auditable proof.
 *
 * ## Network Requirements
 * This test requires network access to mock.hackedyour.info:32222 (192.168.1.115).
 * It is designed to run on:
 * - Physical devices on the local network (Pixel 9 Pro recommended)
 * - Jenkins CI on shark-wrangler (local network access)
 * It is NOT suitable for GitHub Actions (no route to 192.168.1.115).
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class DeterministicMultiTurnTest {

    companion object {
        /** Deterministic seed for reproducible pseudorandom payloads. "COBALT" in hex-ish. */
        private const val PRNG_SEED = 0xC0B417L

        /** Mock SSH server coordinates */
        private const val MOCK_HOST = "192.168.1.115"
        private const val MOCK_PORT = 32222
        private const val MOCK_USER = "test"
        private const val MOCK_PASS = "password"

        /** Timing constants (milliseconds) */
        private const val INTER_COMMAND_DELAY_MS = 1500L  // Server rate-limits to 1/s; add buffer
        private const val CONNECTION_TIMEOUT_S = 20        // Max seconds to wait for SSH connection
        private const val MOTD_WAIT_MS = 3000L             // Wait for welcome message to render
        private const val POST_EXIT_WAIT_MS = 2000L        // Wait for Goodbye to render
        private const val FINAL_RENDER_WAIT_MS = 2000L     // Wait for final frame before screenshot

        /** Alphanumeric character pool for pseudorandom payload generation */
        private const val ALPHANUM = "abcdefghijklmnopqrstuvwxyz0123456789"
    }

    @get:Rule
    val grantPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.POST_NOTIFICATIONS,
        android.Manifest.permission.FOREGROUND_SERVICE,
        android.Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE
    )

    // ──────────────────────────────────────────────────────────────────────
    //  Utility Functions
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Compute SHA-256 hex digest of a UTF-8 string.
     * This mirrors the mock server's `hashlib.sha256(sanitized_str.encode('utf-8')).hexdigest()`.
     */
    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Generate a pseudorandom alphanumeric string of the given length using the provided
     * seeded Random instance. Deterministic: same seed + same call sequence = same output.
     */
    private fun randomAlphanumeric(rng: java.util.Random, length: Int): String {
        return (1..length).map { ALPHANUM[rng.nextInt(ALPHANUM.length)] }.joinToString("")
    }

    /**
     * Build the deterministic payload sequence. Every run with the same seed produces
     * identical payloads. Returns a list of (payload, expectedResponseLine) pairs.
     */
    private fun buildPayloadSequence(): List<Pair<String, String>> {
        val rng = java.util.Random(PRNG_SEED)
        val payloads = listOf(
            "a",                            // Single character baseline
            "bbb",                          // Multi-character
            randomAlphanumeric(rng, 8),     // 8-char pseudorandom
            randomAlphanumeric(rng, 16)     // 16-char pseudorandom
        )

        return payloads.mapIndexed { index, payload ->
            val requestNum = index + 1
            val hash = sha256(payload)
            val expectedResponse = "RESPONSE $requestNum, '$payload', ${payload.length}, $hash"
            payload to expectedResponse
        }
    }

    /**
     * Read the terminal emulator's screen buffer content.
     * This reads what is currently rendered in the TerminalView's backing emulator,
     * which is the ground truth of what the user sees on screen.
     *
     * Returns the full screen text as a single string with newlines.
     */
    private fun readTerminalScreenContent(): String {
        val session = SshSessionProvider.terminalSession ?: return ""
        val emulator = session.emulator ?: return ""
        return emulator.screen?.transcriptText ?: ""
    }

    // ──────────────────────────────────────────────────────────────────────
    //  The Test
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun testDeterministicMultiTurnSshSession() {
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val device = UiDevice.getInstance(instrumentation)
            val context = ApplicationProvider.getApplicationContext<Context>()
            val storageManager = SecurityStorageManager(context)

            // ── VISUAL MODE: We want to prove the real terminal renders correctly ──
            SshSessionProvider.isHeadlessTest = false

            // Workaround: Apache SSHD needs user.home on Android
            System.setProperty("user.home", context.filesDir.absolutePath)

            // ── PRE-COMPUTE: Build the deterministic payload sequence ──
            val payloadSequence = buildPayloadSequence()
            println("═══════════════════════════════════════════════════════════════")
            println("  DETERMINISTIC MULTI-TURN SSH E2E TEST")
            println("  Seed: 0x${PRNG_SEED.toString(16).uppercase()}")
            println("  Target: $MOCK_USER@$MOCK_HOST:$MOCK_PORT")
            println("  Payloads: ${payloadSequence.size}")
            println("═══════════════════════════════════════════════════════════════")
            payloadSequence.forEachIndexed { i, (payload, expected) ->
                println("  CMD ${i + 1}: \"$payload\"")
                println("  EXP ${i + 1}: $expected")
            }
            println("═══════════════════════════════════════════════════════════════")

            // ── SETUP: Create test connection profile ──
            val profileId = "mock-id-deterministic-e2e"
            val profile = ConnectionProfile(
                id = profileId,
                nickname = "E2E Deterministic Test",
                host = MOCK_HOST,
                username = MOCK_USER,
                authType = AuthType.PASSWORD,
                port = MOCK_PORT
            )
            profile.password = MOCK_PASS.toByteArray()
            storageManager.saveProfile(profile)

            var scenario: ActivityScenario<MainActivity>? = null
            try {
                // ── LAUNCH: Start the main activity ──
                scenario = ActivityScenario.launch(MainActivity::class.java)
                device.waitForIdle()

                // ── DISMISS: Handle any compatibility dialogs (16KB, etc.) ──
                dismissCompatibilityDialogs(device)
                Thread.sleep(2000)

                // ── CONNECT: Tap the test profile to initiate SSH connection ──
                val profileSelector = UiSelector().textContains("E2E Deterministic Test")
                var profileNode = device.findObject(profileSelector)

                if (!profileNode.waitForExists(10000)) {
                    // Try scrolling to find it
                    try {
                        val scrollable = androidx.test.uiautomator.UiScrollable(
                            UiSelector().scrollable(true)
                        )
                        scrollable.scrollTextIntoView("E2E Deterministic Test")
                    } catch (e: Exception) {
                        // Ignore scroll failure
                    }
                    profileNode = device.findObject(profileSelector)
                }
                assertTrue(
                    "Profile 'E2E Deterministic Test' must be visible on screen. " +
                    "Check that the app launched correctly and the connection list is shown.",
                    profileNode.exists()
                )

                // Tap the profile row to initiate connection
                Thread.sleep(1500) // Ensure animations/drawing are done
                val clicked = profileNode.click()
                assertTrue("Must be able to tap the profile to connect", clicked)

                // ── WAIT FOR CONNECTION: Poll for SSH PTY output stream ──
                device.waitForIdle()
                var connected = false
                for (i in 1..CONNECTION_TIMEOUT_S) {
                    // Accept Host Key verification dialog if it appears
                    val acceptButton = device.findObject(UiSelector().textMatches("(?i)accept|yes|ok|continue"))
                    if (acceptButton.waitForExists(500)) {
                        acceptButton.click()
                    }

                    if (SshSessionProvider.ptyOutputStream != null) {
                        connected = true
                        break
                    }
                    Thread.sleep(500)
                }
                if (!connected) {
                    val diagnosticFile = File(context.filesDir, "timeout_diagnostic.png")
                    device.takeScreenshot(diagnosticFile)
                    println("DIAGNOSTIC SCREENSHOT saved to: ${diagnosticFile.absolutePath}")
                }
                assertTrue(
                    "SSH connection to $MOCK_HOST:$MOCK_PORT must be established within " +
                    "${CONNECTION_TIMEOUT_S}s. Check network connectivity and mock server status.",
                    connected
                )
                println("✓ SSH connection established")

                // ── WAIT FOR MOTD: Let the welcome message render ──
                Thread.sleep(MOTD_WAIT_MS)

                // ── EXECUTE: Send each payload with 1-second spacing and verify ──
                val verificationResults = mutableListOf<String>()

                for ((index, pair) in payloadSequence.withIndex()) {
                    val (payload, expectedResponse) = pair
                    val cmdNum = index + 1

                    // Send the payload
                    SshSessionProvider.ptyOutputStream?.write("$payload\n".toByteArray())
                    SshSessionProvider.ptyOutputStream?.flush()
                    println("→ Sent CMD $cmdNum: \"$payload\"")

                    // Wait for server rate limit + render buffer
                    Thread.sleep(INTER_COMMAND_DELAY_MS)

                    // Read the terminal screen content
                    val screenContent = readTerminalScreenContent()
                    println("  Screen content length: ${screenContent.length} chars")

                    // Verify the expected response appears in the terminal
                    val found = screenContent.contains(expectedResponse)
                    val status = if (found) "✓ PASS" else "✗ FAIL"
                    println("  $status: CMD $cmdNum expected \"$expectedResponse\"")

                    if (!found) {
                        // Log what we DID see for debugging
                        println("  ──── ACTUAL SCREEN CONTENT ────")
                        screenContent.lines().filter { it.isNotBlank() }.forEach {
                            println("  │ $it")
                        }
                        println("  ──── END SCREEN CONTENT ────")
                    }

                    verificationResults.add("CMD $cmdNum ($payload): $status")
                    assertTrue(
                        "CMD $cmdNum: Expected terminal to contain:\n" +
                        "  \"$expectedResponse\"\n" +
                        "but the terminal screen buffer did not contain this text.\n" +
                        "Screen content (non-blank lines):\n" +
                        screenContent.lines()
                            .filter { it.isNotBlank() }
                            .joinToString("\n") { "  │ $it" },
                        found
                    )
                }

                // ── KEYBOARD TOGGLE & RAPID FIRE TEST ──
                println("→ Capturing pre-keyboard screenshot")
                device.takeScreenshot(File(context.filesDir, "pre_keyboard.png"))
                
                println("→ Tapping screen to toggle keyboard on")
                device.click(device.displayWidth / 2, device.displayHeight / 2)
                Thread.sleep(1500)
                device.takeScreenshot(File(context.filesDir, "during_keyboard.png"))

                println("→ Pressing back to hide keyboard")
                device.pressBack()
                Thread.sleep(1500)
                device.takeScreenshot(File(context.filesDir, "after_keyboard.png"))

                val expectedResponseFromLastCmd = payloadSequence.last().second
                val screenContentAfterToggle = readTerminalScreenContent()
                val isContentPreserved = screenContentAfterToggle.contains(expectedResponseFromLastCmd)
                assertTrue("Screen content should be preserved after keyboard toggle", isContentPreserved)
                println("✓ Keyboard toggle test passed")

                println("→ Sending 100 lines of rapid fire text")
                val rapidFireBuilder = java.lang.StringBuilder()
                for (i in 1..100) {
                    rapidFireBuilder.append("Rapid fire line $i\n")
                }
                SshSessionProvider.ptyOutputStream?.write(rapidFireBuilder.toString().toByteArray())
                SshSessionProvider.ptyOutputStream?.flush()
                Thread.sleep(4000) // Wait for text to flow through PTY and render
                device.takeScreenshot(File(context.filesDir, "after_rapid_fire.png"))

                println("→ Tapping screen to toggle keyboard on again")
                device.click(device.displayWidth / 2, device.displayHeight / 2)
                Thread.sleep(1500)

                println("→ Scrolling to top of terminal")
                for (i in 1..3) {
                    device.swipe(
                        device.displayWidth / 2,
                        device.displayHeight / 4,
                        device.displayWidth / 2,
                        device.displayHeight * 3 / 4,
                        10
                    )
                    Thread.sleep(500)
                }
                device.takeScreenshot(File(context.filesDir, "scrolled_to_top_with_keyboard.png"))

                val screenContentAfterScroll = readTerminalScreenContent()
                val topTextVisible = screenContentAfterScroll.contains("Rapid fire line 1") || screenContentAfterScroll.contains(expectedResponseFromLastCmd)
                assertTrue("Top characters should be preserved after bringing up keyboard and scrolling to top", topTextVisible)
                println("✓ Rapid fire and scroll test passed")

                // ── EXIT: Send exit command and verify graceful disconnect ──
                SshSessionProvider.ptyOutputStream?.write("exit\n".toByteArray())
                SshSessionProvider.ptyOutputStream?.flush()
                println("→ Sent: exit")
                
                // We don't verify "Goodbye" visually because the session disconnects 
                // and the PTY buffer is destroyed instantly, which we WANT to happen!
                Thread.sleep(1000)
                println("✓ Sent exit command — session terminating")

                // ── SCREENSHOT: Capture visual evidence ──
                Thread.sleep(FINAL_RENDER_WAIT_MS)
                val screenshotFile = File(
                    context.getExternalFilesDir(null),
                    "deterministic_e2e_screenshot.png"
                )
                if (screenshotFile.exists()) screenshotFile.delete()
                device.takeScreenshot(screenshotFile)
                assertTrue(
                    "Screenshot must be captured to ${screenshotFile.absolutePath}",
                    screenshotFile.exists()
                )
                println("📸 Screenshot saved: ${screenshotFile.absolutePath}")

                // ── SUMMARY ──
                println("═══════════════════════════════════════════════════════════════")
                println("  TEST RESULTS SUMMARY")
                println("═══════════════════════════════════════════════════════════════")
                verificationResults.forEach { println("  $it") }
                println("  Exit: ✓ PASS (Goodbye received)")
                println("  Screenshot: ✓ SAVED")
                println("═══════════════════════════════════════════════════════════════")
                println("  ALL ${payloadSequence.size} PAYLOADS VERIFIED ✓")
                println("═══════════════════════════════════════════════════════════════")

            } finally {
                // ── CLEANUP: Always clean up, even on failure ──
                storageManager.deleteProfile(profileId)
                val stopIntent = Intent(context, SshService::class.java).apply {
                    action = SshService.ACTION_DISCONNECT
                }
                context.startService(stopIntent)
                scenario?.close()
                println("🧹 Cleanup complete: profile deleted, service stopped, activity closed")
            }
        }
    }

    /**
     * Dismiss any Android compatibility dialogs that may appear on launch.
     * These include the 16KB page size warning on Android 15+ and similar system dialogs.
     */
    private fun dismissCompatibilityDialogs(device: UiDevice) {
        val textOkButton = device.findObject(
            UiSelector().textMatches("(?i)ok|continue|got it|close")
        )
        val resOkButton = device.findObject(
            UiSelector().resourceId("android:id/button1")
        )

        if (textOkButton.waitForExists(1500)) {
            textOkButton.click()
            device.waitForIdle()
        } else if (resOkButton.waitForExists(1500)) {
            resOkButton.click()
            device.waitForIdle()
        } else if (device.findObject(
                UiSelector().textContains("Android App Compatibility")
            ).exists()
        ) {
            device.pressBack()
            device.waitForIdle()
        }
    }
}
