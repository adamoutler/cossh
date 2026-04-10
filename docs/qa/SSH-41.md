# SSH-41 QA Proof: Terminal Extra Keys 2-Pane Swipeable Layout

**User Story:** As a terminal user, I need a 2-pane swipeable row of extra keys so that I can easily access common terminal keys like a real keyboard.

## Implementation
The `TerminalExtraKeys` composable was rewritten to use Jetpack Compose's `HorizontalPager` instead of two horizontally scrolling `Row`s. This gives it the "2-pane swipeable" behavior. 
Additionally, the buttons were updated to use `Modifier.weight(1f)` across the row with a fixed height (`48.dp`). This ensures all buttons are evenly distributed and fixed-size, resembling a real keyboard, fulfilling the acceptance criteria.

## Visual Verification
The Paparazzi visual UI tests were re-recorded to prove the structural changes:
* `app/src/test/snapshots/images/com.adamoutler.ssh.ui.components_TerminalExtraKeysScreenshotTest_page1_noModifiers.png`
* `app/src/test/snapshots/images/com.adamoutler.ssh.ui.components_TerminalExtraKeysScreenshotTest_page1_withModifiers.png`

The CI pipeline runs successfully (PASS ✅).
