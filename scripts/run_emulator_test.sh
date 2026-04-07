#!/bin/bash
set -e
echo "Starting emulator..."
$HOME/Android/Sdk/emulator/emulator -avd Agent_Device -no-window -no-snapshot -no-audio -no-boot-anim -accel on > /dev/null 2>&1 &
EMULATOR_PID=$!
echo "Waiting for emulator to boot..."
adb wait-for-device
while [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" != "1" ]; do sleep 1; done
echo "Emulator booted."

echo "Clearing logcat..."
adb logcat -c

echo "Running test..."
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.adamoutler.ssh.ConnectionCrashTest || echo "Tests failed!"

echo "Dumping logcat for FATAL exceptions..."
adb logcat -d | grep -A 30 "FATAL EXCEPTION" > emulator_crash.log

echo "Killing emulator..."
kill $EMULATOR_PID || true
