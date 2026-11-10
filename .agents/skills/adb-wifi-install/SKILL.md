---
name: adb-wifi-install
description: Compiles and installs the Android app to a specified IP and port over Wi-Fi. It explicitly uses the standard debug signing key (~/.android/debug.keystore) for compilation and the standard ADB key (~/.android) for connection. Use when the user requests an installation and provides an IP address and port.
---

# ADB Wi-Fi Install

This skill provides instructions for compiling the Android application and installing it to a specific device over Wi-Fi, ensuring standard keys are used to avoid signature and authentication mismatches.

> [!CAUTION]
> **Physical & Wi-Fi Device Protection Rules:**
> - **NEVER perform a destructive overwrite on `CoSSHd` (`com.adamoutler.cobaltssh.debug`)**: Never run `adb uninstall`, `adb shell pm clear`, or wipe app data/SharedPreferences/Keystore on any physical or Wi-Fi device. If `adb install -r` fails due to a signature or downgrade error, **STOP IMMEDIATELY** and report the error to the user—never uninstall the app to force installation.
> - **ALWAYS ask permission for `CoSSH` (`com.adamoutler.cobaltssh`)**: You must ask and receive explicit permission from the user before installing, updating, or modifying `CoSSH` (`com.adamoutler.cobaltssh`).
> - **NEVER run instrumented tests (`connectedAndroidTest`) against physical or Wi-Fi devices**, as tests clear app preferences and will wipe the user's saved SSH connections.

## Workflow

When the user asks to compile and install the app to a specific IP and port, follow these exact steps:

1. **Compile the App with Standard Signing Key:**
   Run the Gradle assemble command to build the debug APK. Explicitly pass the path to the standard debug keystore in the user's home folder (`~/.android/debug.keystore`) so it doesn't generate or use a random one.
   ```bash
   JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64 QEMU_LD_PREFIX=/home/adamoutler/Android/x86_64-rootfs ./gradlew assembleDebug \
     -Pandroid.injected.signing.store.file=$HOME/.android/debug.keystore \
     -Pandroid.injected.signing.store.password=android \
     -Pandroid.injected.signing.key.alias=androiddebugkey \
     -Pandroid.injected.signing.key.password=android
   ```

2. **Connect via ADB:**
   Connect to the target device using the provided IP and port. Explicitly use the standard ADB keys located in the user's home folder (`~/.android`) to ensure authentication succeeds.
   ```bash
   ADB_VENDOR_KEYS=~/.android adb connect <IP>:<PORT>
   ```

3. **Install the APK:**
   Install the compiled debug APK directly to the connected device.
   ```bash
   adb -s <IP>:<PORT> install -r app/build/outputs/apk/debug/app-debug.apk
   ```

## Example
If the user says: "install to 192.168.1.39:37605"
You will run:
```bash
./gradlew assembleDebug -Pandroid.injected.signing.store.file=$HOME/.android/debug.keystore -Pandroid.injected.signing.store.password=android -Pandroid.injected.signing.key.alias=androiddebugkey -Pandroid.injected.signing.key.password=android && \
ADB_VENDOR_KEYS=~/.android adb connect 192.168.1.39:37605 && \
adb -s 192.168.1.39:37605 install -r app/build/outputs/apk/debug/app-debug.apk
```
