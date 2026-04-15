# Crypto Module

This module contains security-critical operations including key storage, encryption, and key generation. It acts as the backbone of CoSSH's security invariants.

## Functionality
- Integrates with the Android Keystore for secure key management.
- Persists and manages connection profiles using `SecurityStorageManager`.
- Generates SSH keys (e.g., RSA, ED25519) used for secure authentication.
- Implements encryption and decryption routines for volatile state and persistent storage.

## Dependencies
- **data:** Operates on `ConnectionProfile` and other models that require encryption or secure storage.

## Dependents
- **backup:** Uses crypto utilities to secure exported archives.
- **network:** Retrieves keys and credentials to establish SSH connections.
- **ui:** Requests profile data and key generation from this module.

## Testing Standards
- **Real User Journeys:** Testing must reflect real user journeys and ViewModel integration where applicable, rather than fully isolated mock component testing.
- **Encryption Testing:** Mandate encryption testing under real conditions, avoiding over-reliance on mocks for cryptographic operations.
- **Keystore Fallbacks:** Mandate robust testing of Keystore fallbacks to ensure security invariants are maintained even when hardware-backed keystores fail or act unpredictably.
