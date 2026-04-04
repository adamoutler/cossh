---
name: Add SSH Profile
description: Verify a user can add a new SSH profile to CoSSH and save it.
purpose: The purpose of this journey is to act as a user. The goal is to use screenshot analysis, tap events, input key events and scrolling rather than automated form filling or intent broadcasting.
limitations: Do not use internal APIs, broadcast intents, or direct database modification to add a profile. You must validate the element is on-screen, then send a tap event to the location of that element and use input key events to populate it.
python_dependencies:
  - "appium-python-client"
setup_commands:
  - "adb connect ${DEVICE_IP}"
environmental_variables:
  APP_PACKAGE: com.cossh.app
  TEST_HOST: "192.168.1.100"
  TEST_USER: "root"
assertions:
  - "QA PASSED"
  - "PROFILE_CREATED: ${TEST_HOST}"
---
# Verification of Add SSH Profile:
Your task is to act as a user and verify the following:

- "Launch the app $APP_PACKAGE"
- "Tap the 'Add Profile' FAB (Floating Action Button)."
- "Enter \"${TEST_HOST}\" into the Host input field."
- "Enter \"${TEST_USER}\" into the Username input field."
- "Tap the Save button."
- "Verify the new profile with host \"${TEST_HOST}\" appears in the connection list."
