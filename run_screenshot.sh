#!/bin/bash
set -e
echo "Starting emulator..."
$HOME/Android/Sdk/emulator/emulator -avd Agent_Device -no-window -no-snapshot -no-audio -no-boot-anim -accel on > /dev/null 2>&1 &
EMULATOR_PID=$!
echo "Waiting for emulator to boot..."
adb wait-for-device
while [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" != "1" ]; do sleep 1; done
echo "Emulator booted."

echo "Starting test..."
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.adamoutler.ssh.ui.components.TerminalScreenInstrumentedTest > docs/qa/SSH-40.log 2>&1 &
TEST_PID=$!

echo "Waiting for test to render screen (15s)..."
sleep 15
echo "Taking screenshot..."
adb exec-out screencap -p > docs/qa/SSH-40-keyboard-hidden.png

echo "Waiting for test to complete..."
wait $TEST_PID || true

echo "Killing emulator..."
kill $EMULATOR_PID || true
