# Crypto Module Context

## Logical Purpose
The `com.adamoutler.ssh.crypto` package is the security core of CoSSH, providing hardened mechanisms for credential storage and cryptographic operations. It ensures that sensitive data (passwords, private keys) is protected at rest using hardware-backed Android Keystore and is handled in memory with a focus on 'Volatile State Sanitization' (preventing leaks via process death serialization or clipboard).

## Key Components & APIs
- **`SecurityStorageManager` & `IdentityStorageManager`**: The core persistence engines for Connection Profiles and Identities.
  - They implement a **Layered Encryption** strategy, separating non-sensitive metadata (JSON) from secrets (passwords/keys) in storage. This allows for 'metadata-only' loading, enabling the UI to list profiles without decrypting sensitive fields.
  - They utilize AndroidX `EncryptedSharedPreferences` for general metadata and separate hardware-backed encryption for secrets.
  - They implement a **Hardware Fallback Strategy**, proactively attempting to use `StrongBox` but gracefully falling back to standard hardware-backed Keystore.
- **`PasswordCipher`**: Provides authentication-gated AES-GCM encryption. It implements a 5-minute **Biometric Gating** window for the most sensitive data, requiring the UI to handle `AuthenticationRequiredException`.
- **`PemUtils`**: A format-agnostic parser for various PEM structures. Crucially, it implements **Memory Safety**, explicitly clearing char/byte arrays in memory after parsing to prevent credential leakage.
- **`SSHKeyGenerator`**: A factory for generating cryptographic keys (RSA-4096, Ed25519) and handling OpenSSH wire format encoding.
- **`CryptoExceptions.kt`**: A robust, **Unified Error Handling** utility that translates opaque hardware Keystore errors into actionable domain exceptions (e.g., `KeystoreInvalidatedException`, `UserNotAuthenticatedException`).

## Behavioral Contracts & Design Patterns
- **Layered Encryption & Metadata Isolation**: Secrets must be stored separately from basic profile data to support metadata-only extraction.
- **Volatile State Sanitization**: Operations dealing with private keys or passwords must zero out arrays after use. Avoid `String` allocations for secrets and explicitly zero out `ByteArray` buffers.
- **Exception Recovery**: Consumers of these managers must anticipate and gracefully handle `CryptoException` variants, particularly regarding user authentication flows.

## Dependencies
- AndroidX Security Crypto library (`EncryptedSharedPreferences`, `MasterKey`)
- BouncyCastle (`org.bouncycastle.openssl`, `org.bouncycastle.jcajce`)
- Android Keystore System (`AndroidKeyStore`)