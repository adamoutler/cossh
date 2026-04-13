# Security Module

This module implements system-wide hardening and defensive measures against data leakage.

## Functionality
- Provides the `SecureCrashHandler` to intercept application crashes.
- Ensures that sensitive data, secrets, or passwords are scrubbed from crash reports or logs before the OS handles the crash.
- Adheres to the "Volatile State Sanitization" security invariant by strictly managing what gets logged during unexpected process death.

## Dependencies
- **Android System:** Interacts with the default `UncaughtExceptionHandler` and system logging.

## Dependents
- **Application Scope (`CoSshApplication`):** The main application class depends on this module to set up global crash handlers at startup.
