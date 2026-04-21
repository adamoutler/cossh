# QA Proof: SSH-87 - Implement Identity Manager for Reusable Credentials

**User Story:** *As a user, I need an Identity Manager to securely store and reuse my username, passwords, and SSH keys across multiple connections, so that I don't have to repeatedly enter them.*

**Verification Proof:**
- [x] Cryptographic audit: `IdentityProfile` uses `@Transient` for `password` and `privateKey`. `IdentityStorageManager` encrypts these fields via `PasswordCipher` (AES-GCM) before saving to `EncryptedSharedPreferences`.
- [x] Unit test `IdentityStorageManagerTest` passing, verifying secure save/retrieve and volatile state sanitization (zeroing bytes).
- [x] Unit test `IdentityViewModelTest` passing, verifying integration between UI logic and storage.
- [x] UI implementation: `IdentityListScreen` and `AddEditIdentityScreen` implemented and integrated into navigation. `AddEditProfileScreen` updated to allow selecting an identity.

## Test Execution Log
```
⏱️ TEST-METRIC: com.adamoutler.ssh.crypto.IdentityStorageManagerTest.testSaveAndRetrieveIdentity took 6629ms
IdentityStorageManagerTest > testSaveAndRetrieveIdentity PASSED
⏱️ TEST-METRIC: com.adamoutler.ssh.crypto.IdentityStorageManagerTest.testGetAllIdentities took 244ms
IdentityStorageManagerTest > testGetAllIdentities PASSED
⏱️ TEST-METRIC: com.adamoutler.ssh.crypto.IdentityStorageManagerTest.testDeleteIdentity took 295ms
IdentityStorageManagerTest > testDeleteIdentity PASSED
⏱️ TEST-METRIC: com.adamoutler.ssh.ui.screens.IdentityViewModelTest.testSaveAndLoadIdentities took 564ms
IdentityViewModelTest > testSaveAndLoadIdentities PASSED
⏱️ TEST-METRIC: com.adamoutler.ssh.ui.screens.IdentityViewModelTest.testDeleteIdentity took 410ms
IdentityViewModelTest > testDeleteIdentity PASSED
```

## Implementation Details
- Created `IdentityProfile` model in `com.adamoutler.ssh.data`.
- Implemented `IdentityStorageManager` in `com.adamoutler.ssh.crypto` using `EncryptedSharedPreferences` and `PasswordCipher`.
- Updated `ConnectionProfile` to include an optional `identityId`.
- Implemented `IdentityViewModel`, `IdentityListScreen`, and `AddEditIdentityScreen`.
- Updated `AddEditProfileScreen` to support identity selection and display selected identity details.
- Updated `SshConnectionManager` and `SshService` to resolve and use identity-based credentials during connection.
- Verified volatile state sanitization by calling `clearSensitiveData()` in `SshConnectionManager` after connection attempt.
