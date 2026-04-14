# SSH-37: Terminal Socket Death Detection (Terminal/UI Layer)

## User Story
*As a user, I need to be immediately notified if the SSH session disconnects or the PTY pipe breaks so that I don't type into a dead terminal.*

## Verification Proof
- **Visual/UI Changes:** Created a "Session Disconnected" dialog overlay that prompts the user when the `activeConnections` transitions from active to empty while they are on the terminal screen.
- **Logic/Backend Changes:** The `isConnectionActive` boolean is now observed by a `LaunchedEffect`. If it transitions from `true` to `false`, `showDisconnectedOverlay` becomes `true`. While `showDisconnectedOverlay` is `true`, all keyboard input inside `sendToTerminal` is locked out and ignored, and a log trace is fired.
- **Validation:** Standard out of `./gradlew testDebugUnitTest` and `./gradlew recordPaparazziDebug` passing and exited with code 0.
- **Log trace:** Attached as `SSH-37.log`.