# SSH-56 QA Proof: Fix TerminalSession dummy process syntax error crash

**User Story:** As a user, when I open the terminal, the local dummy session shouldn't crash and print a syntax error so that my screen remains clean for the actual SSH session output.

## Proof of Implementation
The `TerminalSession` dummy initialization in `SshSessionProvider.kt`, `ExceptionTest.kt`, and `TerminalScreenCopyTest.kt` was modified to remove the broken executable arguments that were causing the `sh` binary to parse an executable as a shell script (yielding a syntax error). 
* Old implementation: `TerminalSession("/system/bin/sh", "/", arrayOf("-c", "/system/bin/cat"), arrayOf(), 100, client)`
* New implementation: `TerminalSession("/system/bin/sh", "/", arrayOf(), arrayOf("TERM=xterm-256color"), 100, client)`

This runs the native Android shell (`sh`) as a standard interactive background dummy process, which successfully silently consumes local PTY inputs. 

## Verification Proof
* A full `./gradlew test lint` execution finished successfully, verifying that `ExceptionTest` and `TerminalScreenCopyTest` are fully functional with the dummy shell process running without crashing.
* `./gradlew recordPaparazziDebug` successfully executed without failure, generating accurate visuals for the reality-checker that lack the `[Process completed (code 1) - press Enter]` output.
* The CI pipeline has successfully verified the changes across all components. An explicit logcat trace showing process stability during the tests has been generated and saved to `docs/qa/SSH-56.log`.
