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
- Visual Proof: `docs/qa/SSH-63-before-keyboard.png` (Terminal before keyboard)
- Visual Proof: `docs/qa/SSH-63-after-keyboard.png` (Terminal after keyboard)
- Log Evidence: `docs/qa/SSH-63.log` (Contains `SIGWINCH dispatched successfully` showing height and rows updating)
- CI build passed.
- Local tests (`testDebugUnitTest` and `testReleaseUnitTest`) passed.