# SFTP Subsystem Module Context

## Logical Purpose
The `com.adamoutler.ssh.sftp` package implements an interactive SFTP (SSH File Transfer Protocol) client engine, remote file browser, Storage Access Framework (SAF) integration, and asynchronous file transfer orchestration for CoSSH.

## Key Components & APIs
- **`SftpClientAdapter` & `SshjSftpClientAdapter`**: Abstract client interface and SSHJ-backed concrete implementation for remote filesystem operations (`ls`, `stat`, `mkdir`, `rm`, `rename`, `chmod`, `read`, `write`).
- **`FakeSftpClientAdapter`**: In-memory test double for deterministic unit and UI testing without network sockets.
- **`SftpClientManager`**: Session lifecycle manager bridging active `SshConnectionManager` sessions to persistent SFTP client adapters.
- **`SftpPathValidator`**: Path sanitization utility preventing directory traversal attacks (`..` escapes) and checking path bounds.
- **`SftpSafBridge`**: Bridges Android's Storage Access Framework (`Uri`) with remote SFTP streams for document imports and exports.
- **`SftpTransferManager`**: Background transfer coordinator managing upload/download queues, byte-level progress reporting, and cooperative cancellation.
- **UI Subsystem (`sftp.ui`)**:
  - `SftpBrowserScreen` & `SftpBrowserViewModel`: Main remote file navigation interface.
  - `SftpTextViewerModal`: In-app secure text viewer for remote files.
  - `SftpPermissionsDialog`: Remote file permissions editor (chmod).
  - `SftpTransferBanner`: Persistent non-blocking transfer progress indicator.

## Behavioral Contracts & Design Patterns
- **Adapter Pattern**: Decouples high-level SFTP navigation and UI state from the underlying SSHJ SFTP protocol library.
- **Defensive Path Canonicalization**: All remote file paths must pass validation prior to filesystem operations to prevent jailbreaking the intended directory scope.
- **Non-blocking Coroutine I/O**: All disk and remote network transfers run on `Dispatchers.IO` with StateFlow progress streaming.

## Dependencies
- `com.adamoutler.ssh.network` (`SshConnectionManager`, `SshSessionProvider`)
- SSHJ SFTP Client (`net.schmizz.sshj.sftp.SFTPClient`)
- Android Storage Access Framework (`android.net.Uri`, `android.content.ContentResolver`)
- Jetpack Compose & Material 3
- Kotlinx Coroutines & Flow
