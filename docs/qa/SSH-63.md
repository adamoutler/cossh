# QA Proof for SSH-63 (Terminal Cursor Misalignment / SIGWINCH)

**Implementation Details:**
- Attached `PreDrawListener` to `TerminalView`'s ViewTreeObserver in `TerminalScreen.kt`.
- Updated `SshConnectionManager.kt` and `SshService.kt` to expose the active SSH session down to `SshSessionProvider.activeSshSession`.
- Emits `session.changeWindowDimensions(cols, rows, width, height)` dynamically during keyboard bounds changes (which fires layout/predraw listener) avoiding the cursor drifting off-screen.

**Verification:**
- Local build compiled via `./gradlew compileDebugKotlin compileDebugUnitTestKotlin` successfully.
- Tests passed locally using `./gradlew testDebugUnitTest`.
- CI/CD build passed remotely on GitHub Actions.
- Pipeline receipt: [GitHub Actions Run 24374426608](https://github.com/adamoutler/cossh/actions/runs/24374426608)