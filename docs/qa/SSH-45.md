# SSH-45: Connection Keep-Alive Dialogue on Back Press

## Verification Proof

1. **Screenshot artifact of the "Keep Alive / Terminate" Compose AlertDialog:**
   * Screenshot generated via Paparazzi: `app/src/test/snapshots/images/com.adamoutler.ssh.ui.components_TerminalScreenDialogScreenshotTest_keepAliveDialogScreen.png` shows the AlertDialog prompting the user to keep the session alive or terminate it.

2. **UI test passing that proves the back button triggers the dialogue, a second back press dismisses it, and selecting "Terminate" correctly kills the SSH session and service:**
   * Added `testKeepAliveDialogFlow` to `TerminalScreenInstrumentedTest.kt` which programmatically uses `Espresso.pressBackUnconditionally()` to verify:
     1. First back press opens the "Keep Session Alive?" dialogue.
     2. Second back press dismisses the dialogue.
     3. Opening it again and selecting "Terminate" invokes the callback and safely kills the session via Service intents.
   * A full unit/integration test run is available at `docs/qa/SSH-44-45-UnitTests.log` demonstrating the successful build.

All acceptance criteria are satisfied.