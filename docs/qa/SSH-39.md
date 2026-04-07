# SSH-39: Terminal Extra Keys and Interaction

## Overview
Added a single button panel with 2 horizontally scrollable rows of extra terminal keys (Esc, Super, Menu, Up, Tab, Home, Ctrl, Alt, Left, Down, Right, End, PgUp, Ins, PrtSc, PgDn, Del, Pause). Per the user's steering update, there is no progressive display (swiping pages).

The panel states cycle when the user taps on the `TerminalView`:
1. No keyboard -> 2. Keyboard -> 3. Keyboard & Buttons -> 1. No keyboard (and so on).

Modifiers (`Ctrl`, `Alt`, `Super`, `Menu`) are sticky. When activated, they persist until the next regular key or code point is pressed, successfully sending the correct byte modifications to the PTY stdin (e.g., `Ctrl+C` sends `0x03`).

## Verification Proof
- Successful UI manual deployment on physical device.
- The `user_stories/terminal-buttons.md` covers the QA interactions for the automated test harness.
- **Automated Paparazzi Screenshots**:
  - `app/src/test/snapshots/images/com.adamoutler.ssh.ui.components_TerminalExtraKeysScreenshotTest_page1_noModifiers.png` (Standard keys)
  - `app/src/test/snapshots/images/com.adamoutler.ssh.ui.components_TerminalExtraKeysScreenshotTest_page1_withModifiers.png` (Highlighted sticky keys)
- **Unit Test for Byte Modification Logic**:
  - `TerminalModifierLogicTest.kt` formally verifies that sticky modifiers transform inputs into correct bytes (e.g. `Ctrl+C` -> `0x03`), executed and passed in CI.
