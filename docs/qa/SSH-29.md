# SSH-29 QA Proof
## Refactor Edit Connection interaction to use long-press

**User Story:** As a user, I want a clean connection list without explicit Edit buttons, utilizing a standard long-press gesture to manage a profile.

**Verification Proof:**
- Removed the explicit 'Edit' button from the `ConnectionItem` Composable in `ConnectionListScreen.kt`.
- Replaced `.clickable` with `Modifier.combinedClickable` where `onLongClick` triggers the `onEdit` callback.
- Regenerated the Paparazzi tests (via `./gradlew recordPaparazziDebug`) to visually verify the connection list UI no longer has 'Edit' text buttons.
- Created `ConnectionListScreenInstrumentedTest.kt` containing `longPressTriggersEdit()`, an automated Compose UI test demonstrating that a long-press on a profile item successfully invokes the edit action (which navigates to the Add/Edit Profile screen). This test passes.