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
- Verification Proof 3 (Integration): Integration is proven via `AppConnectionIntegrationTest.test19StepWorkflow_ConnectionResumeAndConcurrentSessions`. All tests pass successfully under `./gradlew testReleaseUnitTest`.