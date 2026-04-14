# SSH-37: Terminal Socket Death Detection (Terminal/UI Layer)

## User Story
*As a user, I need to be immediately notified if the SSH session disconnects or the PTY pipe breaks so that I don't type into a dead terminal.*

## Verification Proof
- **Visual/UI Changes:** Created a "Session Disconnected" dialog overlay.
  - **Artifact:** A Paparazzi snapshot test `TerminalScreenDialogScreenshotTest.sessionDisconnectedDialogScreen` generates `app/src/test/snapshots/images/com.adamoutler.ssh.ui.components_TerminalScreenDialogScreenshotTest_sessionDisconnectedDialogScreen.png` to guarantee the dialog renders correctly.
- **Logic/Backend Changes:** The `isConnectionActive` boolean is observed by a `LaunchedEffect`. The state `showDisconnectedOverlay` tracks disconnects. When true, keyboard input is blocked.
  - **Artifact:** A unit test `TerminalModifierLogicTest.testTerminalInputLockout` explicitly tests the lockout logic, asserting that bytes are not sent to the terminal when the dialog is visible, and the `Log.d` event is fired.
- **Validation:** Standard out of `./gradlew testDebugUnitTest` and `./gradlew recordPaparazziDebug` executed successfully and exited with code 0.
- **Log trace:** Test output log containing the lockout simulation is attached as `SSH-37.log`.