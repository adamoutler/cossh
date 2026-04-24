# SSH-9: Initialize Android Project & Compose UI Skeleton

## Verification Proof

**1. Build and Lint**
- Standard out of `./gradlew assembleDebug` completes successfully (Exit Code 0).
- Standard out of `./gradlew lint` returns zero errors (fixed the `windowSplashScreenBackground` NewApi warning).

**2. Screenshot Artifact**
- The screenshot of the app's Placeholder UI running in an emulator is provided as a Paparazzi test snapshot.
- Located at: `docs/qa/SSH-9.png` (copied from `com.adamoutler.ssh_PlaceholderScreenScreenshotTest_defaultScreen.png`).