# SSH-40 QA Verification

**User Story:** As a user, I need the back button to navigate to the home screen when the terminal keyboard is hidden so that I can easily exit the terminal session.

## Visual/UI Changes
An emulator test script was used to launch the `TerminalScreenInstrumentedTest` UI test, sleep for 15 seconds, and capture a screenshot using `adb exec-out screencap -p`. 
The resulting visual proof is saved as `docs/qa/SSH-40-keyboard-hidden.png`, clearly demonstrating the terminal screen state when the keyboard is hidden (terminalInputState = 0).

## Logic/Backend Changes
Added `TerminalScreenInstrumentedTest` inside `app/src/androidTest/kotlin/com/adamoutler/ssh/ui/components/`. The instrumented UI test verifies that when `terminalInputState` is 0 (keyboard hidden), firing the back button explicitly invokes the `onNavigateBack` callback. 

The test successfully passed on the emulator during UI testing:
```
> Task :app:connectedDebugAndroidTest
Starting 1 tests on Agent_Device(AVD) - 14

Finished 1 tests on Agent_Device(AVD) - 14

BUILD SUCCESSFUL in 16s
69 actionable tasks: 6 executed, 63 up-to-date
```
The raw instrumented test execution log showing the tests actually running is captured in `docs/qa/SSH-40.log`.

## File/System Changes
`TerminalScreen.kt` was updated to:
1. Accept `onNavigateBack: () -> Unit`.
2. Enable `BackHandler(enabled = true)` unconditionally.
3. If `terminalInputState != 0`, it behaves as before (hiding the keyboard and resetting the state to 0). If `terminalInputState == 0`, it calls `onNavigateBack()`.

`AppNavigation.kt` was updated to pass `onNavigateBack = { navController.popBackStack() }` into `TerminalScreen`.
