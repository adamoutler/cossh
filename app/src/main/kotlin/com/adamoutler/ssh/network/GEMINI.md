# Network Module Context

## Logical Purpose
The `com.adamoutler.ssh.network` package is the core engine for remote connectivity in CoSSH. It provides a robust, multi-protocol (SSH, Telnet) infrastructure with advanced state management to bridge complex, blocking network operations with a reactive, asynchronous UI layer.

## Key Components & APIs
- **`ConnectionProtocol` & `ConnectionProtocolFactory`**: Abstract the underlying protocol (SSH vs Telnet), providing a unified interface for connection lifecycle (`connect`, `disconnect`, `write`, `resizePty`).
- **`ConnectionStateRepository`**: The central state broker between the background service and the UI.
  - Manages `ActiveSessionState` for each connection.
  - Employs an internal `outputBuffer` to prevent data loss when the UI is detached (e.g., during navigation or configuration changes).
  - Handles **Synchronous-to-Asynchronous Bridging**: Uses `CompletableDeferred` to suspend background network threads while waiting for UI interaction (e.g., `requestPrompt`, `requestAuthPrompt`).
- **`SshHandshakeCoordinator` & `SshConnectionManager`**: Orchestrates the complex SSH handshake phase.
  - Implements **Trust On First Use (TOFU)** via `TofuHostKeyVerifier` with SHA-256 fingerprinting.
  - Coordinates multiple authentication strategies (`PasswordAuthenticator`, `KeyAuthenticator`).
- **`SshService`**: A Foreground Service that ensures session persistence and manages notification lifecycles, ensuring connections survive app backgrounding.
- **`PortForwardingOrchestrator`**: Handles local and remote port forwards concurrently with the main PTY session.
- **`TelnetConnectionHandler`**: Implements Telnet with specific CR/LF translation logic for terminal compatibility.

## Behavioral Contracts & Design Patterns
- **Memory Safety & Scrubbing**: Explicitly zeroes out sensitive data (passwords, private keys) after authentication attempts are completed.
- **Background Blocking vs UI Reactivity**: The underlying libraries (`sshj`, `commons-net`) perform blocking operations. The network layer suspends these operations using coroutines when UI interaction is required (e.g., accepting an unknown host key), maintaining the connection without hanging the main thread.
- **Error Mapping**: Translates complex, opaque network exceptions into user-friendly `UiText` messages, hiding protocol noise while preserving technical accuracy.
- **Design Patterns**: Heavy use of Factory (protocols), Strategy (authenticators), and Repository (state management) patterns.

## Dependencies
- `com.hierynomus.sshj` (SSH protocol)
- `commons-net` (Telnet protocol)
- `org.bouncycastle` (Cryptography for SSH)
- `com.adamoutler.ssh.crypto` & `com.adamoutler.ssh.data` (Internal dependencies)