# QA Proof for SSH Known Hosts Port Parsing Bug

**Issue:** Connections to a host on an alternate port (e.g. `[192.168.1.115]:32222`) were improperly parsed during the TOFU trust atomic update phase. This caused the app to delete the standard `192.168.1.115` (port 22) key entries from the `known_hosts` file. As a result, the TOFU host key dialog would continuously loop and re-prompt the user upon reconnecting to the standard port.

## Fix Details:
1.  **Modified File:** `app/src/main/kotlin/com/adamoutler/ssh/network/SshConnectionManager.kt`
2.  **Change:** Updated the matching logic in the `knownHostsFile.useLines` loop and the atomic file re-writer loop. Previously, the logic loosely matched `hostname` *or* `[hostname]:$port`. It now strictly matches the exactly formatted host string (`formattedHost`), preventing alternate port connections from erasing standard port keys.
3.  **Test Modification:** Fixed race conditions in `TofuHostKeyVerifierPortBugTest.kt` and `TofuHostKeyVerifierTest.kt` that were causing test freezes/timeouts by replacing arbitrary `Thread.sleep` statements with deterministic `while (promptRequest.value == null)` state loops.

## Verification:
- **Build:** `./gradlew test lint` executed successfully.
- **Tests:** `TofuHostKeyVerifierPortBugTest > testPort22AndOtherPortDoNotOverwriteEachOther` passes.
- **Suite:** All 94 tests completed and passed successfully.

## Artifacts:
- `docs/qa/timeout_check.png` (Screenshot showing the bug in action with continuous re-prompts for the same host).
- Full Test output logged via Gradle stdout.