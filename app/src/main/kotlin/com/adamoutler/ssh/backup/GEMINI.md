# Backup Module

This module handles the secure export and import of CoSSH connection profiles.

## Functionality
- Encrypts backup data using AES-GCM and PBKDF2 for key derivation.
- Packages encrypted profiles into ZIP archives for portability.
- Restores connection profiles securely from uploaded backups.

## Dependencies
- **crypto:** Uses `SecurityStorageManager` and encryption utilities.
- **data:** Operates on the `ConnectionProfile` model.

## Dependents
- **ui:** The UI layer provides triggers and screens for users to initiate backup and restore processes.
