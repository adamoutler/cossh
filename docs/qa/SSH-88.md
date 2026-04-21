# QA Proof: SSH-88 - Fix Soft Keyboard Bleed-Through Sending 'Enter' on Connection

**User Story:** *As a user, I expect the terminal not to automatically send an 'Enter' keystroke when I connect, so that my server doesn't execute unintended commands or line breaks.*

**Verification Proof:**
- [x] UI test logic `isBleedThroughEvent` verified to ignore events with `downTime` before `connectionStartTime`.
- [x] `onCodePoint` debounce logic verified to filter out input within the first 500ms of connection.
- [x] Unit test `TerminalScreenNewlineBleedThroughTest` passing.

## Test Execution Log
```
> Task :app:testDebugUnitTest
⏱️ TEST-METRIC: com.adamoutler.ssh.ui.components.TerminalScreenNewlineBleedThroughTest.testBleedThroughKeyEventIsIgnored took 1639ms

TerminalScreenNewlineBleedThroughTest > testBleedThroughKeyEventIsIgnored PASSED
```

## Implementation Details
- Updated `TerminalScreen.kt` to use `android.os.SystemClock.uptimeMillis()` for `connectionStartTime`.
- Added check in `onCodePoint` to ignore input if `SystemClock.uptimeMillis() < connectionStartTime + 500`.
- `isBleedThroughEvent` uses `downTime` to accurately identify events that were queued before the terminal was active.
