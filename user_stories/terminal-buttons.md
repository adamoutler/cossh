---
name: Terminal Extra Keys
description: Verify tapping toggles keyboard and extra button rows, and modifiers work.
purpose: To act as a user and verify the terminal screen state transitions (None -> Keyboard+Buttons -> None) and that sticky modifiers (Ctrl, Alt) correctly modify inputs.
limitations: Validate UI elements on-screen.
python_dependencies:
  - "appium-python-client"
setup_commands:
  - "adb connect ${DEVICE_IP}"
environmental_variables:
  APP_PACKAGE: com.cossh.app
assertions:
  - "QA PASSED"
---
# Verification of Terminal Extra Keys:
Your task is to act as a user and verify the following:

- "Launch the app $APP_PACKAGE"
- "Tap the default SSH profile to launch the terminal."
- "Verify keyboard is NOT visible and extra buttons are NOT visible."
- "Tap the TerminalView area."
- "Verify the on-screen soft keyboard IS displayed AND the extra buttons (Esc, Ctrl, etc.) are visible."
- "Tap the TerminalView area again."
- "Verify the keyboard and buttons are hidden."
- "Tap the TerminalView area to show buttons."
- "Tap 'ctrl' button."
- "Verify 'ctrl' button is highlighted (sticky)."
- "Tap 'c' on the soft keyboard."
- "Verify 'ctrl' becomes un-highlighted and Ctrl+C (0x03) was sent to the terminal."
- "Swipe left on the extra buttons row."
- "Verify page 2 of buttons (PgUp, PgDn, etc.) is displayed."
