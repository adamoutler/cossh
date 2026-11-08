# Sync Module Context

## Logical Purpose
The `com.adamoutler.ssh.sync` package implements a secure cloud backup system using Google Drive's hidden `appDataFolder`. It provides seamless synchronization of connection profiles and identities to the cloud, ensuring data portability while maintaining a strict zero-knowledge security model. Google never has access to the raw user data or the encryption passphrase.

## Key Components & APIs
- **`DriveSyncManager`**: Handles the technical integration and HTTP transport with the Google Drive REST API v3 and OAuth2.
  - Acts as a pure authenticated transport layer to upload (`POST`/`PATCH`), query, download, and delete encrypted backup archives in Google Drive `appDataFolder`.
  - Delegates all cryptographic operations (key derivation, authenticated encryption with AAD) to `BackupCryptoManager`.
  - Manages the OAuth flow, using a companion object proxy (`currentInstance`) to route activity results from `MainActivity` back to the initiating manager.
- **`SyncWorker`**: A `WorkManager`-based component that acts as the orchestrator.
  - Retrieves the application state, utilizes `BackupCryptoManager` for authenticated packaging and encryption, and calls `DriveSyncManager` to perform the upload.
  - Acts as a functional bridge, enforcing the 'Cloud Sync' entitlement check via `BillingManager` before executing the sync task.

## Behavioral Contracts & Design Patterns
- **Single Authority Encryption Architecture**: Data is encrypted solely by `BackupCryptoManager` using its 250,000-iteration PBKDF2 v3 AAD cipher before transmission. `DriveSyncManager` acts strictly as an authenticated transport layer to Google Drive's hidden `appDataFolder`, eliminating redundant or weak encryption layers.
- **Volatile State Scrubbing**: Both the `SyncWorker` and `DriveSyncManager` aggressively zero-fill passphrase `CharArray` and `ByteArray` objects after use, and nullify token references to prevent memory-dump attacks.
- **Transient Authentication**: OAuth tokens are ephemeral and are strictly not persisted locally.
- **Entitlement Gating**: Background and manual sync operations are gated by the `isCloudSyncEnabled` state provided by `BillingManager`.

## Dependencies
- **Security & Identity Storage**: For retrieving user data and the dedicated sync passphrase.
- **Credential Manager & Identity GMS (`com.google.android.gms`)**: For secure authentication and OAuth2 scope authorization.
- **WorkManager (`androidx.work`)**: For managing the execution lifecycle of background sync tasks.
- **BillingManager (`com.adamoutler.ssh.billing`)**: For validating lifetime premium entitlements.
