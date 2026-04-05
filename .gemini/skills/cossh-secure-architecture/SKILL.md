---
name: cossh-secure-architecture
description: Architectural and security guidelines for the CoSSH project, including Volatile State Serialization, Headless UI testing with Paparazzi, and Keystore fallbacks.
---

# CoSSH Secure Architecture Guidelines

This skill defines the strict architectural implementations required to survive the `@reality-checker` QA gate for the CoSSH project.

## 1. Headless UI Testing Standard (Paparazzi)
For UI screenshot requirements in headless CI environments, you **MUST** use the JVM-based screenshot testing library **Paparazzi**. 
- Do not assume screenshots are impossible in a headless CI. 
- You must generate pixel-perfect screenshot artifacts via `./gradlew recordPaparazziDebug` and commit them to `app/src/test/snapshots/images/`. 
- Provide the exact path to these artifacts in the Kanban ticket comments as proof using the `mcp_kanban_update_ticket` tool. Code snippets will NEVER be accepted as visual proof.

## 2. The Volatile State Serialization Trap
When serializing data models containing secrets (e.g., passwords, keys) to JSON:
- The secret MUST be stored in memory as a `ByteArray` or `CharArray` to allow active wiping.
- You MUST symmetrically encrypt the array via the Android Keystore BEFORE it touches the JSON serializer.
- Standard JSON serialization natively converts data into immutable JVM Strings, causing plaintext secrets to linger in the JVM string pool. You must use a standalone `PasswordCipher` or similar mechanism to prevent this memory leak.

## 3. Robolectric vs. Hardware Keystore (StrongBox)
The CoSSH security invariant mandates `MasterKey.Builder` with hardware backing (`setRequestStrongBoxBacked(true)`). 
- **Robolectric** (the local Android JVM test framework) **does not support StrongBox**. 
- You must implement a software Keystore fallback for JVM tests within a `try/catch` block. 
- During unit testing, ensure this fallback operates symmetrically (cache the fallback AES key in memory during tests so decryption doesn't fail).
- True Keystore hardware validation should be reserved for `androidTest` instrumented tests.
