# Network Module (`com.adamoutler.ssh.network`)

This module is the core engine for SSH connectivity and terminal session management.

## Package Responsibility
The network package manages SSH session lifecycles, authenticates users (Password and RSA/ED25519 Keys), and provides the PTY streams necessary for the terminal emulator. It ensures connections persist in the background via Android Services and enforces strict network timeout security invariants (10s default).

## Core Components
- **`SshConnectionManager`**: A functional wrapper for the `sshj` library that handles connecting, authenticating, and managing PTY sessions.
- **`SshService`**: An Android Foreground Service that ensures SSH connections persist even when the app is backgrounded. It bridges the remote output to the local terminal emulator.
- **`SshSessionProvider`**: A singleton state broker connecting the background `SshService` to the Jetpack Compose `TerminalScreen`. It holds the `TerminalSession` and the PTY `OutputStream`.

## Dependencies
- **`com.adamoutler.ssh.data`**: Uses `ConnectionProfile` to determine connection targets.
- **`com.adamoutler.ssh.crypto`**: Uses credentials and generated keys for authentication.
- **External Libraries**: Heavibly relies on `com.hierynomus:sshj` (protocol), `com.github.termux.termux-app:terminal-view` (emulation), and `org.bouncycastle:bcprov-jdk18on` (crypto).

## Dependents
- **`com.adamoutler.ssh.ui`**: `TerminalScreen` and `ConnectionListScreen` depend on this package to initiate connections, monitor status, and read/write to the interactive terminal.

## Testing Context
Integration tests for SSH session lifecycles are mandatory. Tests must use real or robustly simulated containers to verify network operations and connection handling accurately.
