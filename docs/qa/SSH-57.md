# SSH-57 QA Proof: Enhanced E2E Testing with Real SSH Container

**User Story:** As a developer, I want enhanced E2E testing that connects to an actual SSH container, writes a file using `echo` in one session, and then reads that file back using `cat` in a separate session, so that I can rigorously verify the terminal's multi-session command execution and output parsing capabilities.

## Proof of Implementation
The `SshE2ETest.kt` was added to leverage `org.testcontainers:testcontainers` running `linuxserver/openssh-server:latest`. 
The test establishes a multi-session workflow:
1. **Session 1:** Connects using `SshConnectionManager.connectPty`, waits for the bash prompt, and sends the payload `echo 'Hello Multi-Session' > /config/testfile.txt\n`, followed by an `exit` command. The session properly intercepts the exit text in its PTY output stream and completes cleanly.
2. **Session 2:** Creates a fresh `ConnectionProfile` (satisfying the `SshConnectionManager` volatile state wiping security invariant which destroyed the first password array), connects to the exact same server container, executes `cat /config/testfile.txt\n`, and asserts that `Hello Multi-Session` is parsed directly from the remote output stream.

## Verification Proof
* The Gradle build dependencies were updated in `gradle/libs.versions.toml` and `app/build.gradle.kts`.
* A full `./gradlew testDebugUnitTest --tests "com.adamoutler.ssh.network.SshE2ETest"` execution finished successfully in 17s.
* The test execution logs have been saved to `docs/qa/SSH-57.log`, verifying the testcontainer startup and test passage.
* The CI pipeline has successfully run and verified the commit across all components.
