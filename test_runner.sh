#!/bin/bash
set -e
echo "Starting emulator..."
$HOME/Android/Sdk/emulator/emulator -avd Agent_Device -no-window -no-audio -no-boot-anim -accel on > /dev/null 2>&1 &
EMULATOR_PID=$!
adb wait-for-device
while [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" != "1" ]; do sleep 1; done
echo "Emulator booted."
export ANDROID_SERIAL=emulator-5554

echo "Starting mock_sshd.py..."
python3 mock_sshd.py 41111 &
MOCK_SSH_PID=$!
sleep 2

echo "Running focused tests..."
./gradlew connectedDebugAndroidTest -PfullTestRun --info -Pandroid.testInstrumentationRunnerArguments.class=com.adamoutler.ssh.ConnectionCrashTest > docs/qa/SSH-49.log 2>&1 || true

echo "Capturing secure crashes..."
adb shell 'run-as com.adamoutler.cobaltssh.debug sh -c "cat /data/user/0/com.adamoutler.cobaltssh.debug/files/secure_crashes/*"' > docs/qa/crash.txt 2>&1 || true

echo "Killing emulator..."
adb logcat -d > app/docs/qa/logcat.txt
kill $EMULATOR_PID || true
kill $MOCK_SSH_PID || true
