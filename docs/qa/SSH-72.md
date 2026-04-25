# SSH-72 QA Proof
- Feature: Session Selector for Multiple Active Background Connections
- **Status:** Already Implemented
- The component `ActiveSessionSelectorDialog` exists and handles the logic for choosing which session to resume or whether to start a new one.
- This UI flow was implemented as part of the broader "Connection Resume" epic (SSH-92).
- Verification Proof: The UI test `ConnectionListSessionSelectorScreenshotTest` successfully generated a screenshot of this dialog at `app/src/test/snapshots/images/com.adamoutler.ssh.ui.screens_ConnectionListSessionSelectorScreenshotTest_sessionSelectorDialogScreen.png`.
- Integration is proven via `AppConnectionIntegrationTest.test19StepWorkflow_ConnectionResumeAndConcurrentSessions`. All tests pass successfully under `./gradlew testReleaseUnitTest`.