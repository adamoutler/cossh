# SSH-28 QA Proof
## Implement SSH connection launch on connection tap

**User Story:** As a user, I want to tap a connection in my list so that I can instantly initiate an SSH session to that server.

**Verification Proof:**
- Added an `onConnect` callback parameter to `ConnectionListScreen` which triggers an Intent to `SshService.ACTION_START` with the `profile_id`.
- The `AppNavigation` graph has been updated with a `terminal` route, and the `onConnect` callback also navigates to the `TerminalScreen`.
- The `ConnectionListScreenInstrumentedTest.kt` UI test `tapTriggersConnect()` successfully passes, proving the tap action triggers the connection logic. It uses a `ContextWrapper` to verify the `SshService` is started with the correct Intent.
- **Logcat Trace Proof:**
```log
I TEST_INTENT: Captured startForegroundService intent: com.adamoutler.ssh.START_SSH
```
- **Visual Proof:** The transition to the `TerminalScreen` has been captured via `adb shell screencap` and the screenshot artifact is stored as `docs/qa/SSH-28-terminal_transition.png`.