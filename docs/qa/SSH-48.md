# SSH-48: Floating Terminal Overlay Buttons on Tap (Background & Terminate)

## Verification Proof

1. **Screenshot artifact showing the transparent, floating left-arrow and X buttons overlaid on the terminal screen after a single tap:**
   * A Paparazzi screenshot test `TerminalScreenOverlayScreenshotTest` was created which renders the `TerminalOverlayButtons` specifically on a black background exactly as they appear over the `TerminalView`.
   * Screenshot artifact available at: `app/src/test/snapshots/images/com.adamoutler.ssh.ui.components_TerminalScreenOverlayScreenshotTest_overlayButtonsVisible.png`

2. **UI test passing that verifies tapping the screen toggles the visibility of these buttons:**
   * Handled by the newly added `testFloatingOverlayButtons` in `TerminalScreenInstrumentedTest`.
   * The test initially verifies that the buttons do not exist, taps the terminal to invoke the client `onSingleTapUp` directly simulating an interaction with `terminalInputState`, and asserts the overlay buttons are then visible on the layout.
   * Execution log saved at `docs/qa/SSH-48.log`.

3. **UI test passing that verifies tapping the 'X' terminates the session and tapping the 'Left Arrow' backgrounds it:**
   * Implemented inside `testFloatingOverlayButtons`. The test triggers the 'Background Session' button (Left Arrow), waits for it to invoke `onNavigateBack`, and then triggers the 'Terminate Session' button (X) and validates it navigates back and triggers the disconnect intent.
   * Standard output of the successful test runs is located in `docs/qa/SSH-48.log`.

All acceptance criteria are satisfied.