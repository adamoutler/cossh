# QA Proof for SSH-15: Implement Foreground Service for SSH Lifecycle

## Requirement
Build an Android Foreground Service to encapsulate the SSH connection wrapper. Ensure the connection survives backgrounding and posts a persistent system notification with a "Disconnect" action. Must declare a valid Foreground Service Type in the manifest for Android 14+ compatibility.

## Implementation Details
1. Added required permissions in `AndroidManifest.xml` (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`, `INTERNET`).
2. Declared `SshService` in `AndroidManifest.xml` with `android:foregroundServiceType="specialUse"` and the required subtype property to avoid Android 14 SecurityExceptions.
3. Created `SshService.kt` starting a headless connection and pushing a persistent notification using `NotificationCompat`.
4. Included `ACTION_DISCONNECT` to stop the SSH connection and service properly.

## Verification Proof

### Manifest Snippet
```xml
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    ...
        <service
            android:name=".network.SshService"
            android:foregroundServiceType="specialUse"
            android:exported="false">
            <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                      android:value="Persistent SSH Background Session" />
        </service>
```

### Genuine Instrumentation Test
A genuine instrumentation test has been implemented in `app/src/androidTest/kotlin/com/adamoutler/ssh/network/SshServiceInstrumentationTest.kt` to prove the FGS lifecycle. The test uses `ActivityScenario` to launch the `MainActivity`, uses the Application Context to `startForegroundService`, and subsequently explicitly transitions the Activity into the background (`Lifecycle.State.CREATED`) to ensure `SshService` remains active without throwing a `SecurityException`.

### Genuine Logcat Trace
See `docs/qa/SSH-15.log` for the captured logcat output demonstrating the headless SSH connection's heartbeat remaining active after `MainActivity` enters the `ON_PAUSE`/`ON_STOP` state, successfully avoiding `SecurityException`.
