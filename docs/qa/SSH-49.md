# SSH-49 Verification Proof

## Standard CI/CD Test Run (Skipping @FullTest)
When running tests normally, the suite completes and displays a clear recommendation.

```text
> Task :app:testDebugUnitTest
⏱️ TEST-METRIC: com.adamoutler.ssh.PlaceholderScreenScreenshotTest.defaultScreen took 4281ms

PlaceholderScreenScreenshotTest > defaultScreen PASSED
ℹ️  Standard test suite completed. Note: Long-running @FullTest tests were SKIPPED.
ℹ️  Recommendation: Run './gradlew test connectedAndroidTest -PfullTestRun' for a complete overview.

BUILD SUCCESSFUL
```

## Full Test Run (Executing @FullTest)
When running with `-PfullTestRun`, the tests marked with `@FullTest` are executed, and the final message confirms execution.

```text
> Task :app:testDebugUnitTest
⏱️ TEST-METRIC: com.adamoutler.ssh.network.DeterministicMultiTurnTest.testDeterministicMultiTurnSshSession took 23041ms

DeterministicMultiTurnTest > testDeterministicMultiTurnSshSession PASSED
✅ FULL TEST SUITE EXECUTED.

BUILD SUCCESSFUL
```

Documentation has been added to `CoSSH_Project_Specification.md`.