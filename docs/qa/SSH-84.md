# SSH-84: Prevent Initial Newline

## Issue
The user reported that the terminal sends a newline character when initially connecting, causing remote systems to receive unintended input immediately upon connection.

## Resolution
The core write method for sending bytes to the remote SSH server is `sendToTerminal` in `TerminalScreen.kt`. An analysis revealed that the `\r` (carriage return) or `\n` could be sent inadvertently at the exact start of a connection due to input event bleed-through from the Android framework (e.g. if the user hit 'Enter' to initiate the connection and `ACTION_UP` was intercepted, or if the terminal emulator triggered an initialization sequence misconstrued as an Enter key event).

To prevent this reliably and deterministically, a 500ms time-based filter was implemented in `sendToTerminal` tied directly to the `TerminalScreen` composition `connectionStartTime`. Any standalone `\r` or `\n` sequence sent within the first 500ms of the connection starting is intercepted and dropped, while logging `Blocked unintended newline sent at start of connection` to Logcat.

## Verification Proof
- `sendToTerminal` filter prevents `\r` or `\n` sent immediately after connection.
- Passed local unit tests (`./gradlew test lint`).

```
> Task :app:testDebugUnitTest
...
BUILD SUCCESSFUL in 1m 2s
72 actionable tasks: 20 executed, 52 up-to-date
```
