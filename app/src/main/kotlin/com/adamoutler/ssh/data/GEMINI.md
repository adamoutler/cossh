# Data Module Context

## Logical Purpose
The `com.adamoutler.ssh.data` package is the foundational schema layer for CoSSH. It defines the core domain models: `ConnectionProfile` (connections) and `IdentityProfile` (credentials). It serves as the 'source of truth' for the state and serialization logic of the application's primary entities.

## Key Components & APIs
- **`ConnectionProfile`**: The comprehensive configuration model for SSH/Telnet sessions. Includes network settings, port forwarding (`PortForwardConfig`), UI preferences, and protocol selection (`Protocol`, `AuthType`).
- **`IdentityProfile`**: The model for reusable, decoupled credential sets (username, password, SSH keys). Identities can be linked to multiple connection profiles via their IDs.
- **`clearSensitiveData()`**: A critical method implemented in both profiles. It zeros out sensitive `ByteArray` fields in memory to prevent data leakage (Volatile State Sanitization).
- **`ByteArrayAsBase64Serializer`**: A custom Kotlinx Serializer used to encode non-sensitive byte arrays into Base64 strings for stable JSON storage.

## Behavioral Contracts & Design Patterns
- **Security-First Modeling (Transient Fields)**: Sensitive fields (like `password`, `privateKey`) are explicitly marked with `@Transient`. This ensures they never enter standard JSON serialization streams, logs, or non-secure storage mechanisms.
- **Component Decoupling**: Profiles refer to each other by ID rather than direct object references. This facilitates independent storage and management by the respective `StorageManagers`.
- **Metadata-Only Loading**: A key architectural contract. Fetching lists of profiles typically returns 'unhydrated' objects where the `@Transient` secrets remain null. This minimizes the exposure of decrypted data in memory (SSH-138). Secrets must be side-loaded by dedicated security managers on demand.
- **Strict Memory Management**: Manual scrubbing of sensitive buffers (`clearSensitiveData`) aligns with high-security Android standards, ensuring that memory dumps or process death do not leak credentials.
- **Equality & Hashing**: Profiles use deep content comparison for `ByteArray` fields (`contentEquals`, `contentHashCode`), ensuring correct behavior in UI collections and state management flows.

## Dependencies
- `kotlinx.serialization` (For JSON representation)
- `java.util.Base64` & `java.util.UUID` (For binary data encoding and ID generation)
- Interacts closely with `com.adamoutler.ssh.crypto` (Specifically `SecurityStorageManager` and `IdentityStorageManager` which handle the actual persistence of these models).