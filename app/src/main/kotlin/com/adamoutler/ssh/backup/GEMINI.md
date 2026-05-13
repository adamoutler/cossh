# Backup Module Context

## Logical Purpose
The `com.adamoutler.ssh.backup` package provides a secure mechanism for exporting and importing connection profiles and cryptographic identities. It acts as a bridge between the application's internal secure storage (Keystore-backed) and external portability (encrypted ZIP archives). It ensures that even sensitive fields marked as `@Transient` (passwords, private keys) are securely transported.

## Key Components & APIs
- **`BackupManager`**: A facade that handles Android-specific I/O (via `Uri` and `ContentResolver`) and coordinates with `SecurityStorageManager` and `IdentityStorageManager`.
  - `exportBackup`: Coordinates the extraction and encryption of all local data.
  - `importBackup`: Coordinates decryption and merging into local storage.
- **`BackupCryptoManager`**: An object containing the pure logic for cryptographic operations and ZIP packaging.
- **`BackupPayload`**: A data transfer object (DTO) that structures the backup data, including separate maps for sensitive bytes that are Base64-encoded.

## Behavioral Contracts & Design Patterns
- **Confidentiality & Integrity**: Uses AES-256-GCM for authenticated encryption, ensuring data hasn't been tampered with.
- **Key Derivation**: Uses PBKDF2 with 65,536 iterations and a 16-byte random salt to derive keys from user passwords.
- **Transient Field Handling**: Explicitly extracts `password` and `privateKey` fields from profiles during export and re-injects them during import, as they are excluded from standard JSON serialization.
- **Facade Pattern**: `BackupManager` simplifies the backup process for the UI layer.

## Dependencies
- `com.adamoutler.ssh.crypto` (for storage managers)
- `com.adamoutler.ssh.data` (for core data models like `ConnectionProfile`, `IdentityProfile`)
- `kotlinx.serialization` (for JSON serialization)
- Standard JCE (`Cipher`, `SecretKeyFactory`, `SecureRandom`)

**Note:** Any changes to `ConnectionProfile` or `IdentityProfile` that add sensitive fields must be mirrored in `BackupPayload` and handled in `BackupCryptoManager`'s mapping logic. Cryptographic constants are hardcoded; changes will break backward compatibility.