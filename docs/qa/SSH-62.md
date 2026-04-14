# QA Proof for SSH-62 ("toybox: Unknown command" error)

**Implementation Details:**
- Refactored `SshSessionProvider.getOrCreateSession()` to invoke `/system/bin/sh -c cat` instead of directly calling `cat` with an empty argument array.
- This ensures the Android `toybox` correctly delegates the command parsing, avoiding the crash/error spew into the PTY stream.

**Verification:**
- Verified no exceptions thrown during instantiation via `./gradlew testDebugUnitTest`.
- CI/CD pipeline succeeded after merge.
- Pipeline receipt: [GitHub Actions Run 24374426608](https://github.com/adamoutler/cossh/actions/runs/24374426608)