# QA Proof: SSH-89 - Implement Per-Connection and Global Font Size Preferences

**User Story:** *As a user, I want to adjust the terminal font size and have it remembered for specific connections as well as globally, so that I have a consistent and comfortable viewing experience.*

**Verification Proof:**
- [x] Per-connection persistence: Font size saved to `ConnectionProfile` when updated via volume keys in `TerminalScreen`.
- [x] Global persistence: Font size saved to `SettingsManager` (global preferences) and used as fallback when no per-connection preference exists.
- [x] Debounce logic: Implementation in `TerminalViewModel` verified to group rapid volume key changes and save only after 500ms of inactivity.
- [x] UI logic: `TerminalScreen` correctly re-initializes `TerminalViewModel` font size on navigation.

## Implementation Details
- Created `SettingsManager` in `com.adamoutler.ssh.crypto` for global non-sensitive preferences.
- Updated `ConnectionProfile` to include an optional `fontSize` field.
- Updated `TerminalViewModel` to manage font size state, handle debounce logic using Kotlin Flows, and persist changes to both the active profile and global settings.
- Refactored `TerminalScreen` to use `TerminalViewModel` for font size management, including handling of volume key events.
- Refactored `TerminalScreen` into `TerminalScreenContent` to improve testability and allow ViewModel-free snapshot testing.
