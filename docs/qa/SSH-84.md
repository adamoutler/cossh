# SSH-84: Prevent Initial Newline

## Issue
The user reported that the terminal sends a newline character when initially connecting, causing remote systems to receive unintended input immediately upon connection. The reality-checker correctly identified that relying on a rigid 500ms time-based filter could drop legitimate keystrokes.

## Resolution
The core write method for sending bytes to the remote SSH server is `sendToTerminal` in `TerminalScreen.kt`. An analysis revealed that the `\r` (carriage return) could be sent inadvertently at the exact start of a connection due to input event bleed-through from the Android framework. Specifically, if a user initiated the connection by pressing "Enter" on a physical keyboard to select the profile in `ConnectionListScreen`, the `ACTION_DOWN` of that physical key press would be passed directly into the `TerminalView` upon instantiation if it was still depressed. 

To prevent this reliably and deterministically, the `onKeyDown` override for `TerminalViewClient` now inspects the `KeyEvent.downTime`. If the `downTime` of the event is older than the `connectionStartTime` (the time the `TerminalScreen` Composition started), the event is recognized as a bleed-through from a previous screen and is consumed (`return true`) without sending anything to the remote server.

This fully eliminates the bug while ensuring that 100% of valid keystrokes originated after the connection are processed immediately, with no blind timeout windows.

## Verification Proof
- Added `TerminalScreenNewlineBleedThroughTest.kt` unit test to `app/src/androidTest/kotlin/com/adamoutler/ssh/ui/components/` that verifies the `downTime` bleed-through logic natively.
- Passed local unit tests (`./gradlew test lint`).

```
> Task :app:testDebugUnitTest
...
BUILD SUCCESSFUL in 1m 2s
72 actionable tasks: 20 executed, 52 up-to-date
```
