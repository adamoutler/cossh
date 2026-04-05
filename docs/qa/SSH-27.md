# SSH-27 QA Proof
## Move Save Profile action to top-right on Add Connection screen

**User Story:** As a user, I want the 'Save Profile' action to be easily accessible at the top of the screen so that it is not blocked by the on-screen keyboard when typing.

**Verification Proof:**
- Removed bottom "Save Profile" button.
- Added a Check icon to the `actions` area in the `TopAppBar` of `AddEditProfileScreen`.
- Created an instrumented UI test `AddEditProfileScreenInstrumentedTest.kt` that clicks the new TopAppBar save button and verifies that `onSave` is successfully triggered and form data is captured.
- Ran `./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.adamoutler.ssh.ui.screens.AddEditProfileScreenInstrumentedTest` and the test passed on the JVM.
- Re-recorded Paparazzi screenshot tests using `./gradlew recordPaparazziDebug` showing the Check icon in the Top App Bar.