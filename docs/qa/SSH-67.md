# SSH-67 QA Proof

## Summary
The test suite was drastically shortened by removing slow TestContainers dependencies and redundant long-running Docker-based integration tests. 
A fast embedded-SSHD based true integration test (`AppConnectionIntegrationTest.kt`) was introduced. It verifies that the application:
1. Connects headless but via the `SshService`.
2. Reads data from the mock remote server.
3. Sends data through `SshSessionProvider.ptyOutputStream` simulating a user typing on the `TerminalScreen`.
4. Successfully triggers an end-to-end command flow in under 2 seconds.

## Artifacts
Test run log output proving overall suite time under a minute and individual methods measured in ms is saved alongside this at `docs/qa/SSH-67-test-run.log`.
The `testcontainers` library has been fully excised from the build.

## Build Results
Tests run fast locally, and output `TEST-METRIC` lines in `testDebugUnitTest` and `connectedAndroidTest` tasks.