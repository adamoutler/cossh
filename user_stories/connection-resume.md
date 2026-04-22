# User Story: SSH-92 Connection Resume and Persistent Notification

## Objective
Verify that active SSH connections generate persistent notifications, allow resumption, support concurrent sessions to the same host, and accurately reflect connection counts.

## Workflow Steps
1. Launch the CoSSH application.
2. Tap the '+' button to add a new connection profile.
3. Enter '192.168.1.10' as Host.
4. Enter 'admin' as Username.
5. Select 'Password' as Auth Type and enter a valid password.
6. Tap 'Save'.
7. In the Connection List, tap the newly created profile to initiate a connection.
8. Wait for the terminal screen to appear and verify "Connected" status.
9. Tap the back button to return to the Connection List.
10. Observe a dialogue asking if you want to 'Resume' or 'Start New'.
11. Press 'Start New' and observe a fresh terminal connection.
12. Press Android Home button to background the app.
13. Open the Android Notification Tray.
14. Observe 2 silent notifications for the active connections under a single CoSSH group summary.
15. Tap one of the notifications.
16. Verify the app resumes and navigates directly to the terminal screen for that specific session.
17. Press the back button to return to the Connection List.
18. Observe the active connections badge accurately reflects the number of active connections (e.g., 2).
19. Verify that transcript data in both sessions is not lost upon resumption.