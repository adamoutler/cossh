# Security Module (`com.adamoutler.ssh.security`)

This module implements system-wide hardening and defensive measures against data leakage.

## Package Responsibility
The security package enforces the "Volatile State Sanitization" security invariant by ensuring that sensitive data, secrets, or passwords are scrubbed from crash reports or logs before the OS handles an unexpected application crash. It is a critical boundary preventing cryptographic material or network metadata from leaking.

## Core Components
- **`SecureCrashHandler`**: A custom `Thread.UncaughtExceptionHandler`. It redacts sensitive data (IPs, Base64 strings, PEM blocks) from stack traces. It filters and completely redacts exceptions identified as security-sensitive (`GeneralSecurityException`, etc.). It securely writes sanitized logs to internal storage (`/files/secure_crashes/`) and immediately kills the process to prevent the default Android crash dialog from exposing state.

## Dependencies
- **Android System**: Interacts with `android.content.Context` (internal storage) and `android.os.Process` (termination).
- **Java Standard Library**: Extends `Thread.UncaughtExceptionHandler` and utilizes Regex utilities for redaction.

## Dependents
- **`com.adamoutler.ssh` (Root)**: The `CoSshApplication` entry point depends on this module to register the `SecureCrashHandler` globally during `onCreate()`.

## Testing Context
Security measures, specifically the crash redaction and volatile state sanitization, must be verified in realistic application contexts. Ensure `CoSshApplicationTest` accurately verifies that IPs, keys, and passwords never make it to disk in plain text during a crash.
