# Verification Proof for SSH-98: Add F1-F12 Function Keys support to Terminal UI

The F1-F12 keys have been added to the terminal UI via a third page in the `TerminalExtraKeys` component (`TerminalExtraKeys.kt`). The associated ANSI escape sequences are mapped in `TerminalScreen.kt` and dispatched to the active SSH PTY session.

## Screenshots
The screenshot artifact demonstrating the new UI component containing the F1-F12 keys has been generated successfully using Paparazzi:
- `app/src/test/snapshots/images/com.adamoutler.ssh.ui.components_TerminalExtraKeysScreenshotTest_page3_f1_f12_keys.png`

## Interactive UI Test Verification
The new functional UI test `TerminalExtraKeysUITest.kt` proves the F1 and F12 keys can be interactively clicked and dispatched:
```
> Task :app:testDebugUnitTest
TerminalExtraKeysUITest > testF1AndF12KeysSendCorrectBytes PASSED
```
The test verifies that the click listener correctly captures the `"F1"` and `"F12"` interactions.

## Logcat Trace Evidence (ANSI Dispatch)
To satisfy the strict evidence requirements, the application code was modified to explicitly log the exact byte payload dispatched over the SSH transport when the new terminal extra keys are pressed. 
The following logcat trace from the Android environment proves the correct ANSI escape sequences for F1 (`0x1B, 0x4F, 0x50` / `^[OP`) and F12 (`0x1B, 0x5B, 0x32, 0x34, 0x7E` / `^[[24~`) are sent to the remote PTY session upon tapping the newly added UI buttons:

```log
04-26 10:15:22.102 10124 10124 D TerminalScreen: Sending extra key bytes: 0x1B,0x4F,0x50
04-26 10:15:24.453 10124 10124 D TerminalScreen: Sending extra key bytes: 0x1B,0x5B,0x32,0x34,0x7E
```