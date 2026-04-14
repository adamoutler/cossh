# QA Proof for SSH-60 (Terminal Display Duplication on Keyboard Transitions)

**Implementation Details:**
- Removed `modifier.imePadding()` from `TerminalScreen.kt` root container which was causing sub-pixel fractional layout resizes.
- Replaced with static window insets `WindowInsets.ime.exclude(WindowInsets.navigationBars)`.
- Set background color of `TerminalView` to `Color.BLACK` during the AndroidView factory block. This effectively clears any dirty frames and stale pixels.

**Verification:**
- Evaluated via headless UI and `TerminalScreenCopyTest` executing properly.
- All instrumented tests passing.
- CI/CD pipeline succeeded on `main` branch.
- Pipeline receipt: [GitHub Actions Run 24374426608](https://github.com/adamoutler/cossh/actions/runs/24374426608)