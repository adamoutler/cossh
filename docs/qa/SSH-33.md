# QA Proof for SSH-33: Global JVM Exception Handler

**User Story:** As a developer, I need a global UncaughtExceptionHandler so that fatal crashes are intercepted, logged securely, and handled gracefully instead of showing the default OS crash dialog.

## Verification Proof

### 1. Test Execution
The custom `CoSshApplication` handler successfully intercepts uncaught exceptions.

```
./gradlew testDebugUnitTest --tests "com.adamoutler.ssh.CoSshApplicationTest"

> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 3s
```

### 2. File Changes
- `app/src/main/kotlin/com/adamoutler/ssh/CoSshApplication.kt` registers the handler.
- `app/src/main/AndroidManifest.xml` points the `android:name` property to `.CoSshApplication`.
- `app/src/main/kotlin/com/adamoutler/ssh/security/SecureCrashHandler.kt` implements a secure, PII-scrubbing exception handler writing to internal storage, strictly meeting all paranoia-grade security guidelines to prevent credential or memory leakages.
