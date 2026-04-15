# Crypto Module (`com.adamoutler.ssh.crypto`)

This module is the security core of CoSSH, managing key storage, encryption, and secure data persistence.

## Package Responsibility
The crypto package implements the application's security invariants. It provides secure storage for connection profiles, encrypts sensitive fields (like passwords) that are explicitly excluded from standard serialization, and generates SSH keys for authentication. It utilizes a layered encryption approach (JSON blob + separate encrypted passwords) and includes robust fallback mechanisms for hardware-backed security (StrongBox).

## Core Components
- **`SecurityStorageManager`**: Primary manager for encrypted storage of connection profiles using Android's `EncryptedSharedPreferences`.
- **`PasswordCipher`**: Provides low-level AES-GCM encryption for passwords, backed by the Android Keystore.
- **`SSHKeyGenerator`**: Handles the generation of RSA-4096 and Ed25519 SSH key pairs.

## Dependencies
- **`com.adamoutler.ssh.data`**: Operates on `ConnectionProfile` models.
- **Android Security Crypto**: Relies on `androidx.security.crypto` for `EncryptedSharedPreferences`.
- **Kotlinx Serialization**: Used for converting models to JSON before encryption.

## Dependents
- **`com.adamoutler.ssh.ui`**: Uses `SecurityStorageManager` for profile persistence (e.g., `AddEditProfileViewModel`).
- **`com.adamoutler.ssh.backup`**: Uses `SecurityStorageManager` for reading/writing profiles during export/import.
- **`com.adamoutler.ssh.network`**: Retrieves credentials and generated keys to establish SSH connections.

## Testing Context
Encryption testing must be performed under real conditions. Avoid over-reliance on mocks for cryptographic operations. Mandate robust testing of Keystore fallbacks to ensure security invariants are maintained even when hardware-backed keystores fail.
