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
