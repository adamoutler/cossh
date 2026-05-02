# User Story: SSH-49 - Full Test Run Annotation & Documentation

**As a developer,** I want a "Full" test run mode for items that take too long to test normally and are impractical for CI/CD, complete with annotations and documentation, **so that** I am reminded to run these tests when needed without blocking the standard fast pipeline.

## Details & Accomplishments
1. **Annotation Implementation:** A custom annotation `@FullTest` was created (`app/src/main/kotlin/com/adamoutler/ssh/annotations/FullTest.kt`) to mark long-running integration and E2E tests.
2. **Gradle Configuration:** `app/build.gradle.kts` was updated to exclude these tests from the standard fast pipeline by default. This ensures the CI/CD pipeline remains blazing fast.
   * JVM Tests use `excludeCategories("com.adamoutler.ssh.annotations.FullTest")`
   * Instrumentation Tests use `testInstrumentationRunnerArguments["notAnnotation"] = "com.adamoutler.ssh.annotations.FullTest"`
3. **Execution Command:** To execute these tests, developers can explicitly pass the Gradle property `-PfullTestRun`.
   * ` ./gradlew test connectedAndroidTest -PfullTestRun `
4. **Logging Hook:** A standard run provides a friendly log reminder: "Standard test suite completed. Note: Long-running @FullTest tests were SKIPPED."
