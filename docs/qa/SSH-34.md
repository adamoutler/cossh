# QA Proof for SSH-34: Global Coroutine Exception Architecture

**User Story:** As a developer, I need a standard CoroutineExceptionHandler injected into all ViewModels so that unhandled coroutine exceptions are routed to a centralized UI event bus without crashing the app.

## Verification Proof

### 1. Test Execution
The `BaseViewModelTest` successfully proves that a coroutine exception inside a ViewModel using `launchWithHandler` is intercepted and triggers a generic `UiEvent.ShowSnackbar` to the UI event bus.

```
./gradlew testDebugUnitTest --tests "com.adamoutler.ssh.ui.base.BaseViewModelTest"

> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 3s
```

### 2. File Changes
- `app/src/main/kotlin/com/adamoutler/ssh/ui/base/BaseViewModel.kt` and `BaseAndroidViewModel.kt` implement the base `exceptionHandler` and `launchWithHandler`.
- `app/src/test/kotlin/com/adamoutler/ssh/ui/base/BaseViewModelTest.kt` runs within Robolectric and collects flow emissions from the `UiEventBus` successfully.
