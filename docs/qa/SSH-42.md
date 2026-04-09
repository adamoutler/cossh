# SSH-42: Back button navigation fails when SSH connection dies

## Verification Proof

1. **Integration or UI test passing that simulates a connection drop and verifies that the back button successfully navigates to the main connection list:**
   * Added `testBackPressNavigatesBackWhenConnectionDrops` inside `TerminalScreenInstrumentedTest.kt`.
   * This test creates an active connection, simulates the connection dropping by removing it from `SshSessionProvider.activeConnections`, presses the back button unconditionally, and asserts that the keep-alive dialog does not show up and instead the `onNavigateBack` callback is fired to return the user to the main connection list.
   * Execution log saved at `docs/qa/SSH-42.log`.

2. **Standard out of `./gradlew test` passing:**
   * Run with `./gradlew test` and verified successful completion.
   * Standard output is appended in `docs/qa/SSH-42.log`.

All acceptance criteria are satisfied.