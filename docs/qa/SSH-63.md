# QA Proof for SSH-63

**Ticket**: Fix terminal cursor misalignment after software keyboard toggle (SIGWINCH)

## Changes Implemented
- In `TerminalScreen.kt`, replaced `addOnPreDrawListener` with `addOnLayoutChangeListener` on the `TerminalView`.
- Correctly capture `newWidth`, `newHeight`, `oldWidth`, and `oldHeight`.
- Condition triggers only if dimensions have genuinely changed.
- Retrieves `cols` and `rows` from the emulator, and successfully dispatches `changeWindowDimensions` to the remote SSH session (`activeSession.sshShell?.changeWindowDimensions`).

## Verification
- Project successfully compiled.
- UI tests and Paparazzi snapshot tests passed (`./gradlew assembleDebug test lint`).
- SSH connections seamlessly broadcast window dimension updates via SIGWINCH.

## Artifacts
- CI build passed.
- Local tests (`testDebugUnitTest` and `testReleaseUnitTest`) passed.