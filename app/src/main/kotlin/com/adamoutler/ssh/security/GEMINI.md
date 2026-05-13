# Security Module Context

## Logical Purpose
The `com.adamoutler.ssh.security` package implements the 'Volatile State Sanitization' security invariant at the process level. Its primary role is to act as a defensive boundary that scrubs sensitive information (IP addresses, cryptographic keys, credentials) from crash reports and logs, ensuring that even in failure, the application maintains a hardened security posture.

## Key Components & APIs
- **`SecureCrashHandler`**: The core implementation. It intercepts the standard JVM exception flow by implementing `Thread.UncaughtExceptionHandler`.
  - Registered globally in `CoSshApplication.onCreate()`.
  - `sanitizeThrowable()`: Recursively processes the exception chain to apply redaction rules.
  - `redactString()`: The regex engine responsible for identifying and replacing sensitive patterns.
  - `processKiller`: A functional dependency (lambda) used to cleanly terminate the process. This allows the handler to be tested without actually killing the test runner.

## Behavioral Contracts & Design Patterns
- **Decorator/Interceptor Pattern**: It wraps the default exception handler to apply security filters before logging or terminating.
- **Data Redaction**: Automatically scrubs IPv4 addresses, long Base64 strings (likely keys), and PEM blocks using regex patterns.
- **Sensitive Exception Masking**: Completely redacts the exception message for exceptions containing 'crypto', 'security', 'auth', or 'ssh' in their class names or types.
- **Fail-Closed Strategy**: If the `SecureCrashHandler` itself encounters an error during redaction or writing, it catches the error and immediately terminates the process without writing potentially unsafe, unredacted data.
- **Process Stealth**: Terminates the process before the Android system's default crash handler can execute. This prevents the standard OS crash dialog (which could leak unredacted memory or stack traces) from displaying or capturing the data.

## Dependencies
- **Android Framework**: `Context` (for resolving internal storage paths), `android.os.Process` (for termination).
- **JVM APIs**: `Thread.UncaughtExceptionHandler`, `java.util.regex` (redaction engine).
- **Storage**: Writes redacted crash reports exclusively to the internal app files directory (`/files/secure_crashes/`), inaccessible to other apps.