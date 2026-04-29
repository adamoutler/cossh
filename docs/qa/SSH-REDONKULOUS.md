# QA Proof for SSH-Redonkulous-Button Fix

**Issue:** The "Hold to Accept Risk" button in the `HostKeyPromptDialog` was taking up excessive vertical space (redonkulous size), likely due to `fillMaxHeight()` being used in a container that didn't have a fixed height constraint.

## Fix Details:
1.  **Modified File:** `app/src/main/kotlin/com/adamoutler/ssh/ui/components/HoldToConfirmButton.kt`
2.  **Change:** Removed the inner `Box` that used `fillMaxHeight()`.
3.  **Optimization:** Implemented the progress fill bar using `.drawBehind { ... }`. This ensures the progress bar is drawn as part of the background and does not influence the layout size of the button.
4.  **Layout Logic:** The button now naturally sizes itself based on its content (the Text and its padding), which is the standard behavior for buttons in an `AlertDialog`.

## Verification:
- **Build:** `./gradlew assembleDebug` passed.
- **Tests:** `./gradlew testDebugUnitTest --tests com.adamoutler.ssh.ui.HostKeyPromptDialogScreenshotTest` passed, confirming the Paparazzi snapshots are correct and the layout is stable.
- **Visual:** The button now adheres to standard material design button height constraints while maintaining the "hold to confirm" animation logic.

## Artifacts:
- **Snapshot Test Result:** `app/build/reports/paparazzi/debug/index.html` (verified locally)
- **App Home Screenshot (Verification of app launch):** `docs/qa/app_home.png`
