#!/bin/bash

echo "🎥 Starting automated screen recording..."

# Start screen recording in the background
adb shell screenrecord --time-limit 20 /sdcard/fgs_demo_auto.mp4 &
RECORD_PID=$!

echo "📱 Launching CoSSH..."
adb shell monkey -p com.adamoutler.cobaltssh -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1
sleep 3

echo "👆 Tapping the first connection..."
# Tap roughly where the first connection is in the list (middle of screen horizontally, a bit down vertically)
# Since we can't perfectly parse UI automator quickly in bash without jq/xmlstarlet, we'll use a heuristic tap 
# that works for most phones (x=500, y=400)
adb shell input tap 500 400
sleep 3

echo "🏠 Pressing Home..."
adb shell input keyevent 3
sleep 2

echo "🔔 Opening notifications..."
adb shell cmd statusbar expand-notifications
sleep 2

echo "👆 Tapping the CoSSH notification..."
# Tap roughly where the first/second notification is
adb shell input tap 500 450
sleep 3

echo "⏳ Waiting for recording to finish..."
wait $RECORD_PID

echo "📥 Pulling video..."
adb pull /sdcard/fgs_demo_auto.mp4 ./fgs_demo_auto.mp4
echo "✅ Done! Video saved as fgs_demo_auto.mp4"
