# Backup Module (`com.adamoutler.ssh.backup`)

This module provides a secure mechanism for exporting and importing connection profiles.

## Package Responsibility
The backup package acts as a high-level orchestrator for data portability. It allows users to export their connection profiles into encrypted ZIP archives and restore them. A critical aspect of this module is its explicit handling of `ConnectionProfile` passwords, which are marked as transient in the core data model and must be handled specially during backup and restore.

## Core Components
- **`BackupManager`**: The high-level entry point providing a clean API for the UI layer to trigger exports and imports. Coordinates with the Android `ContentResolver` and `SecurityStorageManager`.
- **`BackupCryptoManager`**: Handles the core cryptographic logic. Uses PBKDF2 (65,536 iterations) for key derivation from a user-provided password and AES-GCM (256-bit) for data encryption of the `BackupPayload`.

## Dependencies
- **`com.adamoutler.ssh.crypto`**: Depends on `SecurityStorageManager` to retrieve existing profiles for export and to save imported profiles.
- **`com.adamoutler.ssh.data`**: Uses `ConnectionProfile` and `BackupPayload` models.

## Dependents
- **`com.adamoutler.ssh.ui`**: `ConnectionListViewModel` is the primary consumer, integrating `BackupManager` into user-facing UI flows.

## Testing Context
Thoroughly tested through local unit tests (`BackupCryptoManagerTest`) and instrumented integration tests (`SecurityStorageManagerInstrumentedTest`). Ensure new backup features maintain cryptographic integrity and properly handle transient fields like passwords.
