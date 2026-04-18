# Verification Proof for SSH-35

## Summary
The goal of this ticket is to provide actionable error dialogs when an SSH connection fails (e.g., wrong password, timeout) instead of silent failures or hangs.

## Implementation
1. **Connection States:** Added a `ConnectionState` sealed interface (`Connecting`, `Connected`, `Error`) to `SshSessionProvider`. The provider now maintains a `Map<String, ConnectionState>` representing the state of each connection profile.
2. **SshService:** Modified `SshService` to update these states. When a connection is attempted, the state transitions to `Connecting`. If the SSH PTY connection drops or fails authentication, it emits `ConnectionState.Error(message)` before shutting down the service.
3. **UI Error Dialog:** The `TerminalScreen` UI now observes the `connectionStates` StateFlow. If any profile transitions to the `Error` state, a dialog is rendered with the exact message from the SSHJ library (e.g. `UserAuthException`).
4. **State Transition Test:** Added `test service connection state transitions to error on failure` to `SshServiceForegroundTest.kt`. It starts the service, forces an unreachable connection, and verifies the `SshSessionProvider` state correctly flips to `Error`.
5. **UI Snapshot Test:** Added `connectionFailedDialogScreen` to `TerminalScreenDialogScreenshotTest.kt` to capture a Paparazzi snapshot showing the Compose AlertDialog rendering the "Connection Failed" error.

## Artifacts
- **Screenshot Artifacts:** Paparazzi test snapshot verifying the dialog UI.
- **Log output:** The complete log of `testDebugUnitTest` run with metrics passing successfully, stored at `docs/qa/SSH-35.log`.
