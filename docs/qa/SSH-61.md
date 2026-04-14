# SSH-61: Auto-hide terminal overlay buttons after inactivity delay

## User Story
*As a user, I want the terminal overlay buttons to fade out automatically after a period of inactivity, so that they don't waste screen space indefinitely.*

## Verification Proof
- **Visual/UI Changes:** Terminal overlay buttons (Background & Terminate) are now wrapped in an `AnimatedVisibility` with fade transitions.
- **Logic/Backend Changes:** The state `showOverlayButtons` is now completely decoupled from `terminalInputState`. A `LaunchedEffect` coroutine hides the buttons after 3000ms. Tapping the terminal resets the visibility to `true`.
- **File/System Changes:** Standard out of `./gradlew testDebugUnitTest` and `./gradlew recordPaparazziDebug` executed successfully and exited with code 0.
- **Log trace:** Attached as `SSH-61.log`.