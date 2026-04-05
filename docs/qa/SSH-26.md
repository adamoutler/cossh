# SSH-26 QA Proof
## Remove default loaded SSH keys

**User Story:** As a user, I expect my SSH keys list to be empty on a fresh install so that I only see keys I explicitly created or imported.

**Verification Proof:**
- Removed `mock-key-1` and `mock-key-2` from the AddEditProfileScreen SSH key dropdown. It now properly shows "No keys available".
- Verified that `KeyManagementScreen` properly defaults to `emptyList()` for keys, satisfying the empty list requirement on a fresh install.
- Ran `KeyManagementScreenScreenshotTest.kt` to generate `com.adamoutler.ssh.ui.keys_KeyManagementScreenScreenshotTest_defaultScreen.png` showing an empty screen.
- Application builds successfully without warnings related to keys.