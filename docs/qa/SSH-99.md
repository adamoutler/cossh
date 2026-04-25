# Verification Proof for SSH-99: Replace directional word buttons with arrow symbols

The terminal directional buttons (Up, Down, Left, Right) in the `TerminalExtraKeys` component have been successfully replaced with arrow symbols (↑, ↓, ←, →). The ANSI mappings in `TerminalScreen.kt` have been updated to recognize the new arrow symbols.

## Output of `./gradlew test`
The test output below shows the build passing, including UI tests:
```
BUILD SUCCESSFUL in 1m 33s
72 actionable tasks: 21 executed, 51 up-to-date
```
Tests passed including `TerminalExtraKeysScreenshotTest`.

## Screenshots
The screenshot artifacts showing the terminal UI where the directional buttons are now arrow symbols instead of words can be found in the updated Paparazzi snapshots:
- `app/src/test/snapshots/images/com.adamoutler.ssh.ui.components_TerminalExtraKeysScreenshotTest_page1_noModifiers.png`
- `app/src/test/snapshots/images/com.adamoutler.ssh.ui.components_TerminalExtraKeysScreenshotTest_page1_withModifiers.png`
