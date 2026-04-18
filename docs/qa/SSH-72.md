# SSH-72 Verification Proof

## Session Selector for Multiple Active Background Connections

**Feature Verification:**
- Implemented `ActiveSessionSelectorDialog` inside `ConnectionListScreen.kt`.
- The system observes the `Lifecycle.Event.ON_START` event, checking if the background service has >1 active connections stored in `SshSessionProvider.activeConnections.value.size`.
- If true, a prompt gives the user the choice of starting a new connection or selecting from the active sessions list.

**Visual Proof:**
A Paparazzi UI Snapshot test `ConnectionListSessionSelectorScreenshotTest` was executed and generated the required UI artifact.
See `docs/qa/SSH-72-selector.png` for visual proof.

**Testing Evidence:**
The newly authored `ConnectionListSessionSelectorScreenshotTest` test passed successfully:
```
> Task :app:testDebugUnitTest
com.adamoutler.ssh.ui.screens.ConnectionListSessionSelectorScreenshotTest > sessionSelectorDialogScreen PASSED
BUILD SUCCESSFUL
```
