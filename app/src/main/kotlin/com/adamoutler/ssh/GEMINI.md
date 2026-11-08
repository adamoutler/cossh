# Root Application Module Context

## Logical Purpose
The `com.adamoutler.ssh` root package serves as the application lifecycle entrypoint and primary Android OS integration layer for CoSSH (Cobalt SSH). It coordinates process-level initialization, security hardening, root activity hosting, and window-level privacy boundaries.

## Key Components & APIs
- **`CoSshApplication`**: The `android.app.Application` implementation.
  - Registers the BouncyCastle security provider at position 2 (preserving the default Android TLS provider at position 1 while providing hardened cryptographic algorithms).
  - Installs `SecureCrashHandler` as the default uncaught exception handler to prevent credential leakage into stack traces or crash reports.
- **`MainActivity`**: The primary `ComponentActivity` hosting Jetpack Compose UI.
  - Enforces `WindowManager.LayoutParams.FLAG_SECURE` to block screenshots and task-switcher previews.
  - Sets `filterTouchesWhenObscured = true` to protect against tapjacking and overlay attacks.
  - Configures edge-to-edge window insets and handles incoming intent URIs for quick connection launches.
  - Coordinates Google Drive OAuth authorization result dispatching via `DriveSyncManager`.
- **`ReflectionTest`**: Internal sanity check validating reflection accessibility constraints across build targets.

## Behavioral Contracts & Design Patterns
- **Single-Activity Architecture**: Jetpack Compose manages screen transitions and UI state under a single activity.
- **Immediate Security Hardening**: Window protection flags and exception traps are configured before any UI composition occurs.
- **Provider Ordering Invariant**: BouncyCastle must never usurp position 1 to prevent overriding standard Android TLS protocol suites.

## Dependencies
- AndroidX Activity & Jetpack Compose (`androidx.activity.compose`, `androidx.compose.material3`)
- Android WindowManager & Security APIs
- Submodules: `ui`, `security`, `crypto`, `network`, `sync`, `util`
- BouncyCastle (`org.bouncycastle.jce.provider.BouncyCastleProvider`)
