# SSH-59: Fix SSH connection crash: no such algorithm X25519 for provider BC

## Issue
The SSH connection fails with `net.schmizz.sshj.transport.TransportException: no such algorithm: X25519 for provider BC`. This occurs because Android's bundled BouncyCastle provider lacks modern algorithms like X25519, and `sshj` attempts to use it.

## Verification Proof
- **File/System Changes:**
  - The BouncyCastle dependency `org.bouncycastle:bcprov-jdk18on` is included in `build.gradle.kts`.
  - In `CoSshApplication.kt`, the system `BC` provider is explicitly removed and replaced with the updated `BouncyCastleProvider` at position 1.
- **Logic/Backend Changes:**
  - Added a specific unit test `testBouncyCastleProviderIsRegistered` in `CoSshApplicationTest.kt` verifying that `BouncyCastleProvider` is registered correctly.
- **Validation:**
  - Standard out of `./gradlew testDebugUnitTest` and the integration tests passed.
  - Logcat output and E2E connectivity test log attached as `SSH-59.log`.