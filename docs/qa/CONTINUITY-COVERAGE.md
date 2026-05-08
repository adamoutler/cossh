# Continuity Document: Unit Test Coverage Push (Target: 80%)

## Current Status
- **Goal:** Reach 80% overall unit test instruction coverage.
- **Starting Coverage:** 68.06%
- **Current Coverage:** 68.81%
- **Status:** In Progress. We've made incremental gains by testing previously uncovered data serialization and background/service components.

## Completed Work
1. **`SecureCrashHandler`**: Wrote `SecureCrashHandlerTest` to verify it correctly redacts sensitive information (IPs, Base64) from crash logs. Tests pass.
2. **Data Serializers**: Created `DataSerializersTest` covering `ConnectionProfile` and `IdentityProfile` to trigger `kotlinx.serialization` paths.
3. **Backup & Crypto**: Created `BackupCryptoManagerTest` to verify `BackupPayload` serialization and basic zip export/import mechanics.
4. **ViewModels**: Updated `ConnectionListViewModelTest`. Replaced flaky `Thread.sleep` calls with `ShadowLooper.idleMainLooper()` and `ShadowLooper.runUiThreadTasksIncludingDelayedTasks()`. Fixed tests for drag-and-drop, state updates, folder moves, and backup flows.
5. **Services & Workers**: 
   - Expanded `SshServiceTest` and `SshServiceForegroundTest` to cover intent handling (`ACTION_START`, `ACTION_DISCONNECT`) and error mapping.
   - Created `SyncWorkerTest` to cover the `DriveSyncManager` integration logic.

## Blockers / Flaky Tests Resolved
- We encountered a `Gradle Test Executor` crash (exit value 10) caused by unexecuted MainLooper runnables. This was resolved by properly using `ShadowLooper` and coroutine synchronization instead of `Thread.sleep()`.

## Next Steps for the Next Agent/Session
To reach the 80% coverage goal, focus on the following areas which currently hold the most missed instructions:

1. **Compose UI Components (High Impact but Harder to Unit Test):**
   - `TerminalScreenKt` (70.16% missed, ~725 instructions)
   - `ConnectionListScreenKt` (59.98% missed, ~501 instructions)
   - `AddEditProfileScreenKt` (83.64% missed, ~178 instructions)
   *Strategy:* Use Paparazzi screenshot tests (like `AddEditProfileScreenScreenshotTest`) to trigger rendering paths, or use Robolectric Compose UI testing (`createComposeRule()`) to interact with the UI elements.

2. **Generated Serializers (Low Hanging Fruit):**
   - `ConnectionProfile$$serializer` (48.53% missed, ~314 instructions)
   *Strategy:* Generated serializers have huge bytecode for default values and edge cases. We may need to test deserializing incomplete JSON to hit the fallback paths, or add exclusions to `sonar.coverage.exclusions` in `build.gradle.kts` if they shouldn't be counted.

3. **Service Coroutines:**
   - `SshService$startSshConnection$1` (54.49% missed, ~182 instructions)
   *Strategy:* We need to mock a successful SSH connection and simulate `onOutput` being called. Consider using `org.apache.sshd.server.SshServer` (like in `PortForwardingOrchestratorTest`) to actually establish a local loopback connection during the test to hit the connection success and data transfer blocks.

## How to Resume
1. Run `./gradlew app:testDebugUnitTest app:jacocoTestReport` to verify the current 68.81% coverage.
2. Review the HTML report in `app/build/reports/jacoco/jacocoTestReport/html/index.html`.
3. Pick one of the "Next Steps" targets above and implement the test. Ensure no new `Thread.sleep` calls are introduced in Robolectric tests.