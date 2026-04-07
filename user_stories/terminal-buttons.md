---
name: Terminal Extra Keys
description: Verify tapping toggles keyboard and extra button rows, and modifiers work.
purpose: To act as a user and verify the terminal screen state transitions (0:None -> 1:Keyboard -> 2:Keyboard+Buttons -> 0:None) and that sticky modifiers (Ctrl, Alt) correctly modify inputs.
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
- "Verify the on-screen soft keyboard IS displayed but extra buttons are NOT visible."
- "Tap the TerminalView area again."
- "Verify the on-screen soft keyboard IS displayed AND the extra buttons (Esc, Ctrl, etc.) are visible."
- "Tap the TerminalView area again."
- "Verify the keyboard and buttons are hidden."
- "Tap the TerminalView area twice to show buttons."
- "Tap 'Ctrl' button."
- "Verify 'Ctrl' button is highlighted (sticky)."
- "Tap 'c' on the soft keyboard."
- "Verify 'Ctrl' becomes un-highlighted and Ctrl+C (0x03) was sent to the terminal."
