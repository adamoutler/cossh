---
name: cossh-secure-architecture
description: Architectural and security guidelines for the CoSSH project, including Volatile State Serialization, Headless UI testing with Paparazzi, and Keystore fallbacks.
---

# CoSSH Secure Architecture Guidelines

This skill defines the strict architectural implementations required to survive the `reality-checker` QA gate for the CoSSH project.

## 1. Headless UI Testing Standard (Paparazzi) & ARM64 Host Execution
For UI screenshot requirements in headless CI environments, you **MUST** use the JVM-based screenshot testing library **Paparazzi**. 
- Do not assume screenshots are impossible in a headless CI. 
- You must generate pixel-perfect screenshot artifacts via `./gradlew recordPaparazziDebug` and commit them to `app/src/test/snapshots/images/` (and copy ticket-specific visual proofs to `docs/qa/`). 
- Provide the exact path to these artifacts in the Kanban ticket comments as proof using the `update_ticket` tool. Code snippets will NEVER be accepted as visual proof.
- **ARM64 Host Execution for Unit/Robolectric Tests:** Because Android LayoutLib (`libandroid_runtime.so`) is an `x86_64` ELF binary, running unit tests directly on the ARM64 Linux host requires `QEMU_LD_PREFIX`:
  ```bash
  JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64 QEMU_LD_PREFIX=/home/adamoutler/Android/x86_64-rootfs ./gradlew :app:testDebugUnitTest --tests "com.adamoutler.ssh..."
  ```
- **Recording Paparazzi Snapshots on ARM64 Host:** `recordPaparazziDebug` loads native `x86_64` `libandroid_runtime.so` inside the JVM process, which cannot be dlopened directly by an `aarch64` JVM. To record or update Paparazzi snapshots locally, run inside the `amd64` runner image with cached toolchains:
  ```bash
  docker run --rm --platform linux/amd64 \
    -v /home/adamoutler/git/ssh:/workspace \
    -v /home/adamoutler/Android/Sdk:/opt/android-sdk:ro \
    -v /home/adamoutler/.act_runner_gradle:/root/.gradle \
    -v act-toolcache:/opt/hostedtoolcache:ro \
    -w /workspace \
    -e ANDROID_HOME=/opt/android-sdk \
    -e ANDROID_SDK_ROOT=/opt/android-sdk \
    -e JAVA_HOME=/opt/hostedtoolcache/Java_Temurin-Hotspot_jdk/21.0.12-101.0.LTS/x64 \
    docker.gitea.com/runner-images:ubuntu-latest \
    bash -c 'export PATH="$JAVA_HOME/bin:$PATH" && ./gradlew :app:recordPaparazziDebug --tests "*YourScreenshotTest*" && chown -R 1000:1000 /workspace/app /workspace/.gradle'
  ```

## 2. The Volatile State Serialization & Memory Sanitization Trap
- **JSON Serialization of Secrets:** When serializing data models containing secrets (e.g., passwords, keys) to JSON:
  - The secret MUST be stored in memory as a `ByteArray` or `CharArray` to allow active wiping.
  - You MUST symmetrically encrypt the array via the Android Keystore BEFORE it touches the JSON serializer.
  - Standard JSON serialization natively converts data into immutable JVM Strings, causing plaintext secrets to linger in the JVM string pool. You must use a standalone `PasswordCipher` or similar mechanism to prevent this memory leak.
- **Volatile Stream Buffer Zero-Wiping:**
  - Socket and PTY read loops (such as `PtyStreamBridge.startBridge()`) MUST wrap buffer consumption in a `try ... finally { buffer.fill(0) }` block so raw terminal output and credential streams are actively wiped upon socket closure, stream EOF, or coroutine cancellation.
  - `ActiveSessionState.sanitize()` in `SessionStreamRegistry` MUST drain and zero-fill all queued byte buffers in `outputBuffer`, close `ptyOutputStream`, and null out all socket/channel references immediately when a session disconnects.

## 3. Cryptographic Invariants & The Hostile Wire
- **Hardware Keystore vs. Robolectric (StrongBox):**
  - The CoSSH security invariant mandates `MasterKey.Builder` with hardware backing (`setRequestStrongBoxBacked(true)`). 
  - Robolectric (the local Android JVM test framework) **does not support StrongBox**. You must implement a software Keystore fallback for JVM tests within a `try/catch` block. 
  - During unit testing, ensure this fallback operates symmetrically (cache the fallback AES key in memory during tests so decryption doesn't fail).
- **SSHJ Cryptographic Suite Hardening (`SecureSshConfig`):**
  - Default `AndroidConfig` in SSHJ registers legacy, broken algorithms: SHA-1 KEX (`diffie-hellman-group1-sha1`, etc.), 64-bit block ciphers (`3des-cbc`, `blowfish-cbc`), and weak MACs (`hmac-sha1`, `hmac-md5`).
  - Always instantiate `SSHClient(SecureSshConfig())`. `SecureSshConfig` filters out any factory containing `sha1`, `group1-`, `3des`, `blowfish`, or `md5`, eliminating Sweet32, Logjam, and hash collision downgrade attacks on hostile networks.

## 4. Status Bar Notification Silhouette Standard
- Active background foreground service notifications (`SshService.kt`) must NEVER use generic Android system placeholders like `android.R.drawable.ic_dialog_info`.
- Android 12+ requires notification small icons to be a single-color (monochrome white `#FFFFFF`) silhouette on a transparent background (e.g., `app/src/main/res/drawable/ic_stat_ssh.xml`). Multi-color launcher icons will render as solid unreadable white blocks.

## 5. Gradle Test Runner & Forgejo CI Stability Invariants
- **Single-Fork Serialization (`maxParallelForks = 1`):** In `app/build.gradle.kts`, `maxParallelForks` MUST be `1` in both `testOptions.unitTests.all` and `tasks.withType<Test>`. Running multiple parallel test JVMs causes a fatal race condition in Robolectric's `MavenArtifactFetcher.java` when two forks simultaneously download and move `android-all-instrumented` jars into `/root/.m2/repository`.
- **JVM Reuse (`forkEvery = 25`):** Keep `forkEvery = 25` (with `maxHeapSize = "3072m"`), NEVER `forkEvery = 1`. Setting `forkEvery = 1` forces 75+ cold JVM + Robolectric + LayoutLib startups under QEMU emulation, inflating `testDebugUnitTest` duration beyond 35 minutes.
- **QEMU Timing Tolerance:** Heavy cryptographic operations (such as 4096-bit RSA keypair generation in `IdentityViewModelTest`) and coroutine throughput benchmarks (`TerminalWriteBatcherTest`) run significantly slower under QEMU x86_64 emulation. Always warm up coroutine dispatchers outside `measureTimeMillis` blocks and provide generous polling timeouts for asymmetric key generation.
- **Forgejo Artifact Uploads:** Use `actions/upload-artifact@v3` (with `continue-on-error: true`) in `.forgejo/workflows/ci.yml`. The Forgejo instance supports the v3 artifact API and returns `404` on `actions/upload-artifact@v4`'s Twirp `CreateArtifact` endpoint.

