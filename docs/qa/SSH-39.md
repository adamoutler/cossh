# SSH-39: Terminal Extra Keys and Interaction

## Overview
Added a single button panel with 2 rows of extra terminal keys (Esc, Super, Menu, Up, Tab, Home, Ctrl, Alt, Left, Down, Right, End) that users can access by swiping left for page 2 (PgUp, Ins, PrtSc, PgDn, Del, Pause). The panel becomes visible (along with the soft keyboard) when the user taps on the `TerminalView`. Tapping again dismisses both the keyboard and the extra buttons.

Modifiers (`Ctrl`, `Alt`, `Super`, `Menu`) are sticky. When activated, they persist until the next regular key or code point is pressed, successfully sending the correct byte modifications to the PTY stdin (e.g., `Ctrl+C` sends `0x03`).

## Verification Proof
- `connectedAndroidTest` does not exist for this feature yet but manual logic integration succeeds.
- Successful UI manual deployment.
- The `user_stories/terminal-buttons.md` covers the manual QA interactions for the automated test harness.
