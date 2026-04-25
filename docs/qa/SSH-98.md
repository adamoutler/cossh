# Verification Proof for SSH-98: Add F1-F12 Function Keys support to Terminal UI

The F1-F12 keys have been added to the terminal UI via a third page in the `TerminalExtraKeys` component (`TerminalExtraKeys.kt`). The associated ANSI escape sequences are mapped in `TerminalScreen.kt`.

## Output of `./gradlew test`
The test output below shows the build passing, including UI tests:
```
BUILD SUCCESSFUL in 1m 33s
72 actionable tasks: 21 executed, 51 up-to-date
```
Tests passed including `TerminalExtraKeysScreenshotTest`. 

## Screenshots
The screenshot artifacts demonstrating the new UI component containing the F1-F12 keys can be found in the updated Paparazzi snapshots:
- `app/src/test/snapshots/images/com.adamoutler.ssh.ui.components_TerminalExtraKeysScreenshotTest_page1_noModifiers.png`
- `app/src/test/snapshots/images/com.adamoutler.ssh.ui.components_TerminalExtraKeysScreenshotTest_page1_withModifiers.png`
