# SSH-43: Persistent Background Service & Notifications for Active Connections

## Verification Proof

1. **Screenshot artifact demonstrating the silent persistent notification in the Android system tray:**
   * Screenshot generated via Paparazzi: `app/src/test/snapshots/images/com.adamoutler.ssh.network_NotificationScreenshotTest_testForegroundNotification.png` demonstrates the "CoSSH Session - Connected" notification with a "Disconnect" action.

2. **Logcat trace proving `SshService` correctly runs as a Foreground Service and is not killed when the app is swiped away:**
   * Execution log saved at `docs/qa/SSH-43.log`. 
   * Additionally, `SshServiceInstrumentationTest` tests `testForegroundServiceSurvivesActivityBackgrounding` and verifies `SshService` starts a Foreground Service using `ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE` for Android 14+ compatibility and survives the Activity entering the `CREATED`/background state.

All acceptance criteria are satisfied.