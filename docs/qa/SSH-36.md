# SSH-36 QA Proof and Evidence

## Hardware Keystore & Storage Resilience

### Feature Description
The application now gracefully handles Android Keystore failures (such as `KeyPermanentlyInvalidatedException` and `UserNotAuthenticatedException`). When the Keystore fails, the app intercepts the exception and surfaces a user-friendly UI prompt rather than crashing or hanging silently. This prompt informs the user of the security change and allows them to reset their secure storage (wiping data but regaining access to the app) or close the app.

### Evidence Provided
1. **Unit/Snapshot Test:** 
   The `KeystoreInvalidatedDialogScreenshotTest.kt` unit test verifies that the Compose UI component (`KeystoreInvalidatedDialog`) renders correctly.
2. **Log/Test Output:**
   ```
   > Task :app:testDebugUnitTest
   ⏱️ TEST-METRIC: com.adamoutler.ssh.ui.components.KeystoreInvalidatedDialogScreenshotTest.keystoreInvalidatedDialogScreen took 3036ms

   KeystoreInvalidatedDialogScreenshotTest > keystoreInvalidatedDialogScreen PASSED
   ```
3. **Artifact:**
   The `docs/qa/SSH-36-dialog.png` screenshot artifact was generated from Paparazzi, demonstrating the UI prompt.

### Implementation Details
- Modified `SecurityStorageManager` and `IdentityStorageManager` to make `encryptedPrefs` lazy so Keystore initialization happens on access instead of class instantiation.
- Created `CryptoExceptions.kt` with `handleKeystoreExceptions` to catch specific exceptions and re-throw custom domain exceptions (`KeyInvalidatedException`, `AuthenticationRequiredException`, `SecureStorageUnavailableException`).
- Updated `BaseAndroidViewModel`'s `exceptionHandler` to intercept `KeyInvalidatedException` and publish a global `UiEvent.ShowKeystoreInvalidatedDialog`.
- Added `KeystoreInvalidatedDialog` to `AppNavigation` to ensure the dialog can be presented globally regardless of the current screen.
- Added `resetInvalidatedKeys()` logic to clear the compromised key alias from `AndroidKeyStore` and wipe the corresponding `SharedPreferences` files.