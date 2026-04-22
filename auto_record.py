import subprocess
import time
import xml.etree.ElementTree as ET
import re

def run(cmd, bg=False):
    if bg:
        return subprocess.Popen(cmd, shell=True)
    return subprocess.check_output(cmd, shell=True, stderr=subprocess.STDOUT).decode('utf-8')

def find_target(dump_name="dump.xml", text_contains=None):
    run(f"adb shell uiautomator dump /sdcard/{dump_name}")
    run(f"adb pull /sdcard/{dump_name} ./{dump_name}")
    
    screen_width = 1080 # Default fallback
    try:
        tree = ET.parse(dump_name)
        root = tree.getroot()
        
        # Get screen width from the first node
        for node in root.iter('node'):
            bounds = node.attrib.get('bounds', '')
            match = re.match(r'\[\d+,\d+\]\[(\d+),\d+\]', bounds)
            if match:
                screen_width = int(match.group(1))
                break

        for node in root.iter('node'):
            text = node.attrib.get('text', '')
            if text_contains and any(tc.lower() in text.lower() for tc in text_contains):
                bounds = node.attrib.get('bounds', '')
                match = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', bounds)
                if match:
                    x1, y1, x2, y2 = map(int, match.groups())
                    cx, cy = (x1+x2)//2, (y1+y2)//2
                    return cx, cy, screen_width
    except Exception as e:
        print("Error parsing XML:", e)
    return None, None, screen_width

print("🎥 Starting automated screen recording...")
record_proc = run("adb shell screenrecord --time-limit 25 /sdcard/fgs_demo_auto.mp4", bg=True)

print("📱 Launching CoSSH...")
run("adb shell am force-stop com.adamoutler.cobaltssh")
time.sleep(1)
run("adb shell monkey -p com.adamoutler.cobaltssh -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1")
time.sleep(5)

print("👆 Finding a connection profile to tap...")
cx, cy, screen_width = find_target("dump_main.xml", ["192.168", "@", "Troubleshooting"])

if cx is not None and cy is not None:
    # Tap the right side of the screen on the same Y-level to hit the Connect button
    target_x = screen_width - 80
    print(f"✅ Found connection at Y={cy}. Tapping connect button at X={target_x}, Y={cy}...")
    run(f"adb shell input tap {target_x} {cy}")
else:
    print("⚠️ Could not find a connection string. Using fallback coordinates.")
    run("adb shell input tap 900 600")

print("⏳ Waiting for connection to establish...")
time.sleep(5)

print("🏠 Pressing Home to background the app...")
run("adb shell input keyevent 3")
time.sleep(2)

print("🔔 Opening notification shade...")
run("adb shell cmd statusbar expand-notifications")
time.sleep(3)

print("🔍 Finding CoSSH notification...")
cx, cy, _ = find_target("dump_notif.xml", ["CoSSH", "Session", "Terminal", "Active"])

if cx is not None and cy is not None:
    print(f"✅ Found notification! Tapping at {cx}, {cy}...")
    run(f"adb shell input tap {cx} {cy}")
else:
    print("⚠️ Could not find notification via UI Automator. Tapping fallback coordinates.")
    run("adb shell input tap 500 500")

time.sleep(3)

print("⏳ Waiting for recording to finish...")
record_proc.wait()

print("📥 Pulling video...")
run("adb pull /sdcard/fgs_demo_auto.mp4 ./fgs_demo_auto_final.mp4")
print("🎉 Done! Video saved as fgs_demo_auto_final.mp4")
