---
name: Terminal Interaction Features
description: Verify that volume buttons scale text in the terminal and tapping the screen brings up the keyboard.
purpose: The purpose of this journey is to act as a user. The goal is to verify physical volume button inputs correctly scale text size and touch events on the terminal view successfully trigger the on-screen keyboard.
limitations: Do not use internal APIs. You must validate the element is on-screen, trigger the volume keys, verify text scaling, and simulate a tap on the terminal area to verify keyboard activation.
python_dependencies:
  - "appium-python-client"
setup_commands:
  - "adb connect ${DEVICE_IP}"
environmental_variables:
  APP_PACKAGE: com.cossh.app
assertions:
  - "QA PASSED"
  - "TEXT_SCALED: true"
  - "KEYBOARD_VISIBLE: true"
---
# Verification of Terminal Interaction:
Your task is to act as a user and verify the following:

- "Launch the app $APP_PACKAGE"
- "Tap the default SSH profile to launch the terminal."
- "Press the Volume Up hardware button."
- "Verify the text size inside the TerminalView has increased."
- "Press the Volume Down hardware button."
- "Verify the text size inside the TerminalView has decreased."
- "Tap the TerminalView area."
- "Verify the on-screen soft keyboard is displayed."
