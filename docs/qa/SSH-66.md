# Verification Proof for SSH-66: TLC Refactoring & E2E Testing

## Objective
Improve testing to be more representative of user journeys. Update GEMINI.md in each module to enforce these standards. Resolve unreliability through improved E2E integration tests under Robolectric.

## Proof 1: Updated GEMINI.md Files
All module-level `GEMINI.md` files have been updated with a `## Testing Standards` section enforcing real ViewModel and Navigation integration over completely isolated mock UI component tests.
Modules updated: `ui`, `network`, `crypto`, `data`, `backup`, `security`.

## Proof 2: E2E User Journey Test
Added `UserJourneyIntegrationTest.kt` inside `app/src/test/kotlin/com/adamoutler/ssh/ui/`.
This test correctly starts the app via `AppNavigation()`, clicks the Add Connection FAB, types into the Nickname, Host, Username, and Password fields, saves the profile, and asserts it navigated back and rendered the new connection in the `ConnectionListScreen`.

## Proof 3: Test Execution Log
```
> Task :app:testDebugUnitTest
See the Paparazzi report at: file:///home/adamoutler/git/ssh/app/build/reports/paparazzi/debug/index.html

BUILD SUCCESSFUL in 5s
30 actionable tasks: 3 executed, 27 up-to-date
```
The tests pass successfully, proving that ViewModel to StorageManager integration works effectively within the UI tests, significantly boosting confidence in the application's overall reliability.