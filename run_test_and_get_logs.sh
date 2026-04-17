#!/bin/bash
adb reverse tcp:2222 tcp:2222
adb logcat -c
python3 mock_sshd.py > mock_sshd_log.txt 2>&1 &
MOCK_PID=$!

./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.adamoutler.ssh.ConnectionCrashTest > test_out.txt 2>&1
TEST_RES=$?

kill $MOCK_PID || true
adb reverse --remove tcp:2222

echo "---- TEST OUTPUT ----"
cat test_out.txt
echo "---- MOCK SSHD LOGS ----"
cat mock_sshd_log.txt
exit $TEST_RES
