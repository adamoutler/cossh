# SSH-28 QA Proof
## Implement SSH connection launch on connection tap

**User Story:** As a user, I want to tap a connection in my list so that I can instantly initiate an SSH session to that server.

**Verification Proof:**
- Added an `onConnect` callback parameter to `ConnectionListScreen` which triggers an Intent to `SshService.ACTION_START` with the `profile_id`.
- The `AppNavigation` graph has been updated with a `terminal` route, and the `onConnect` callback also navigates to the `TerminalScreen`.
- Logcat trace proof (from instrumentation tests): Tapping the connection item correctly calls `onConnect`, dispatching the `ACTION_START` intent to initiate the SSH connection.
- The `ConnectionListScreenInstrumentedTest.kt` UI test `tapTriggersConnect()` successfully passes, proving the tap action triggers the connection logic.