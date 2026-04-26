# SSH-72 QA Proof
- Feature: Session Selector for Multiple Active Background Connections
- **Status:** Already Implemented
- The component `ActiveSessionSelectorDialog` exists and handles the logic for choosing which session to resume or whether to start a new one.
- This UI flow was implemented as part of the broader "Connection Resume" epic (SSH-92).
- Verification Proof 1 (UI Selection Logic): The UI test `ActiveSessionSelectorDialogTest` successfully verifies that clicking "Start New" triggers the new connection navigation and clicking a session triggers the resume logic. 
  ```
  > Task :app:testDebugUnitTest
  ⏱️ TEST-METRIC: com.adamoutler.ssh.ui.screens.ActiveSessionSelectorDialogTest.testSelectSession_ResumesSession took 3176ms
  ActiveSessionSelectorDialogTest > testSelectSession_ResumesSession PASSED
  ⏱️ TEST-METRIC: com.adamoutler.ssh.ui.screens.ActiveSessionSelectorDialogTest.testStartNew_StartsNewSession took 183ms
  ActiveSessionSelectorDialogTest > testStartNew_StartsNewSession PASSED
  ```
- Verification Proof 2 (Visual): The UI test `ConnectionListSessionSelectorScreenshotTest` successfully generated a screenshot of this dialog at `docs/qa/SSH-72-selector.png`.
- Verification Proof 3 (Integration): The UI integration test `ConnectionListDialogIntegrationTest` explicitly mounts the `ConnectionListScreen`, mocks multiple active sessions via `ConnectionStateRepository`, and asserts that the `AlertDialog` dynamically renders. It then explicitly tests the `onConnect` navigation parameters for both "Start New" (new UUID) and "Resume" (existing session UUID).
  ```
  > Task :app:testDebugUnitTest
  ⏱️ TEST-METRIC: com.adamoutler.ssh.ui.screens.ConnectionListDialogIntegrationTest.testConnectionList_ShowsSessionSelector_AndNavigates took 3530ms
  ConnectionListDialogIntegrationTest > testConnectionList_ShowsSessionSelector_AndNavigates PASSED
  ```