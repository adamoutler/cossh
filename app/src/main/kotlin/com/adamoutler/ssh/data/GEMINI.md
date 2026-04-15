# Data Module (`com.adamoutler.ssh.data`)

This module serves as the central data definition layer for the CoSSH application.

## Package Responsibility
The data package acts as the single source of truth for the structure of connection data. It defines the schema for SSH connection metadata and leverages Kotlinx Serialization to facilitate saving to storage and exporting to backups. It strictly enforces the 'Volatile State Sanitization' mandate by explicitly managing sensitive data.

## Core Components
- **`ConnectionProfile`**: The primary data class representing a saved SSH connection configuration (host, port, auth type, etc.). Note that the `password` field is explicitly transient and must be handled specially by encryption layers.
- **`AuthType`**: Enum defining the supported authentication methods (Password, Key, etc.).
- **Data Scrubbing**: Includes logic like `clearSensitiveData()` to actively scrub passwords from memory.

## Dependencies
- **Kotlinx Serialization**: For structured data parsing and generation (`@Serializable`).
- **Java Standard Library**: Uses `java.util.Base64` for binary data encoding.

## Dependents
This package is a critical dependency for almost every functional block in the app:
- **`com.adamoutler.ssh.crypto`**: `SecurityStorageManager` serializes profiles before encryption.
- **`com.adamoutler.ssh.network`**: `SshConnectionManager` and `SshService` consume `ConnectionProfile` to establish sessions.
- **`com.adamoutler.ssh.backup`**: `BackupCryptoManager` packages these profiles into encrypted ZIP archives.
- **`com.adamoutler.ssh.ui`**: ViewModels consume these models to drive the interface.

## Testing Context
Data models must be tested in conjunction with their serialization and deserialization processes in realistic flows. Ensure `clearSensitiveData()` is verified to prevent memory leaks of passwords.
