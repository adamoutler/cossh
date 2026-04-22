#!/bin/bash

echo "🎥 Starting screen recording for 45 seconds..."
echo "Please perform the following actions on your device:"
echo "  1. Open CoSSH."
echo "  2. Connect to an SSH server."
echo "  3. Press the Home button to put the app in the background."
echo "  4. Pull down the notification shade to show the persistent 'SSH Session Active' notification."
echo "  5. Tap the notification to return to the app."
echo ""
echo "Recording in progress..."

# Record for 45 seconds
adb shell screenrecord --time-limit 45 /sdcard/fgs_demo.mp4

echo "✅ Recording finished. Pulling video to your computer..."
adb pull /sdcard/fgs_demo.mp4 ./fgs_demo.mp4

echo "🎉 Done! The video has been saved as 'fgs_demo.mp4' in your project folder."
echo "You can upload this video to YouTube/Google Drive and provide the link in the Play Console."
