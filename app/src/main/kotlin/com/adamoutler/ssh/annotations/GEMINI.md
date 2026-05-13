# Annotations Module Context

## Logical Purpose
The `com.adamoutler.ssh.annotations` package serves as a centralized metadata layer for CoSSH, primarily focused on Test Orchestration and CI/CD Optimization. It provides semantic tags that control how and when specific parts of the codebase are executed during the build pipeline.

## Key APIs & Interfaces
- **`@FullTest`**: A marker annotation (Runtime retention) used to tag test classes or functions that represent long-running, resource-intensive, or environmentally-dependent tests (e.g., end-to-end network integration tests).

## Behavioral Contracts & Design Patterns
- **Marker Annotation Pattern**: Decouples the categorization of a test from the test logic itself, providing actionable metadata to the build system.
- **Fast Feedback Mandate**: The primary behavioral contract enforced by this package is **Exclusion by Default**. Tests annotated with `@FullTest` are automatically skipped in standard `./gradlew test` and `./gradlew connectedAndroidTest` runs to ensure rapid local development feedback.
- **Explicit Opt-In (Shift-Left Execution)**: To run these heavy tests, developers must explicitly provide the `-PfullTestRun` property to Gradle.
- **Build System Integration**: The actual logic for filtering these annotations lives in `app/build.gradle.kts`, which uses JUnit categories for JVM tests and the `notAnnotation` argument for Android instrumentation tests.

## Dependencies
- **Build System**: Tightly coupled with `app/build.gradle.kts` which acts upon these annotations.
- **Kotlin Standard Library**: For annotation definitions.
- **JUnit 4 / AndroidX Test**: The underlying frameworks that interpret the filtering arguments provided by Gradle.