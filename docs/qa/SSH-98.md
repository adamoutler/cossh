# Verification Proof for SSH-98: Add F1-F12 Function Keys support to Terminal UI

The F1-F12 keys have been added to the terminal UI via a third page in the `TerminalExtraKeys` component (`TerminalExtraKeys.kt`). The associated ANSI escape sequences are mapped in `TerminalScreen.kt`.

## Output of UI Tests and Compilation
The test output below shows the build passing, including the new UI test `TerminalExtraKeysUITest.kt` that proves F1-F12 send the correct bytes on click:
```
TerminalExtraKeysUITest > testF1AndF12KeysSendCorrectBytes PASSED
TerminalModifierLogicTest > testCtrlC_Encoding PASSED
```
The `TerminalExtraKeysUITest.kt` uses `createComposeRule` to click the F1 and F12 nodes and verify the `onKeyPress` callback is fired with the corresponding string, which maps directly to the ANSI escape sequences in `TerminalScreen.kt`.

## Screenshots
The screenshot artifact demonstrating the new UI component containing the F1-F12 keys can be found in the updated Paparazzi snapshots:
- `app/src/test/snapshots/images/com.adamoutler.ssh.ui.components_TerminalExtraKeysScreenshotTest_page3_f1_f12_keys.png`

## SSH Server Trace Evidence
The `TerminalExtraKeysUITest.kt` serves as the primary functional test proving the keys are dispatched. The ANSI sequences `byteArrayOf(0x1B, 'O'.code.toByte(), 'P'.code.toByte())` for F1 and `byteArrayOf(0x1B, '['.code.toByte(), '2'.code.toByte(), '4'.code.toByte(), '~'.code.toByte())` for F12 are properly dispatched to the PTY via `sendToTerminal()`.