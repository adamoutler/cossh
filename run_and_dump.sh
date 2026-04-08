#!/bin/bash
set -e
python3 mock_sshd.py &
MOCK_PID=$!

echo "Starting emulator..."
$HOME/Android/Sdk/emulator/emulator -avd Agent_Device -no-window -no-snapshot -no-audio -no-boot-anim -accel on > /dev/null 2>&1 &
EMULATOR_PID=$!
adb wait-for-device
while [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" != "1" ]; do sleep 1; done
adb logcat -c

echo "Running test with custom host..."
sed -i 's/host = "localhost"/host = "10.0.2.2"/g' app/src/androidTest/kotlin/com/adamoutler/ssh/ConnectionCrashTest.kt
sed -i 's/port = 22/port = 2222/g' app/src/androidTest/kotlin/com/adamoutler/ssh/ConnectionCrashTest.kt

./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.adamoutler.ssh.ConnectionCrashTest > docs/qa/SSH-32.log 2>&1 &
TEST_PID=$!

sleep 15
adb exec-out screencap -p > docs/qa/SSH-32-password-auth.png

wait $TEST_PID || true

adb logcat -d > docs/qa/SSH-32_logcat.txt
kill $EMULATOR_PID || true
kill $MOCK_PID || true

git restore app/src/androidTest/kotlin/com/adamoutler/ssh/ConnectionCrashTest.kt
