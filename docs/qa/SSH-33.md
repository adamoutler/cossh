# QA Proof for SSH-33: Global JVM Exception Handler

**User Story:** As a developer, I need a global UncaughtExceptionHandler so that fatal crashes are intercepted, logged securely, and handled gracefully instead of showing the default OS crash dialog.

## Verification Proof

### 1. Test Execution
The custom `CoSshApplication` handler successfully intercepts uncaught exceptions, redacts sensitive data, writes to disk, and gracefully kills the process.

```
./gradlew testDebugUnitTest --tests "com.adamoutler.ssh.CoSshApplicationTest"

> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 3s
```

Test Cases Added and Passing:
- `testSecureCrashHandlerIsRegistered`: Verifies that the global handler is registered.
- `testSecureCrashHandlerRedactsAndWritesToDisk`: Triggers a fake crash with a fake private key and an IP address. Asserts that the process is commanded to terminate (preventing the OS crash dialog), verifies that the crash log file is written to internal storage `secure_crashes/`, and asserts that the file contents have completely redacted the PII, IP, and key block (`[REDACTED_EXCEPTION_MESSAGE]`).

### 2. File Changes
- `app/src/main/kotlin/com/adamoutler/ssh/CoSshApplication.kt` registers the handler.
- `app/src/main/AndroidManifest.xml` points the `android:name` property to `.CoSshApplication`.
- `app/src/main/kotlin/com/adamoutler/ssh/security/SecureCrashHandler.kt` implements a secure, PII-scrubbing exception handler writing to internal storage. It terminates the application using `android.os.Process.killProcess(android.os.Process.myPid())` and `System.exit(10)`, completely bypassing the default OS crash dialog.
