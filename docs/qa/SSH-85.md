# QA Proof: SSH-85 - Fix Missing MOTD by Implementing Queue Buffer for SSH Output

**User Story:** *As a user, I want to see the full Message of the Day (MOTD) upon initial connection, so that I have the complete context provided by the server.*

**Verification Proof:**
- [x] Headless test output where `SshService` emits 3 rapid chunks before `TerminalScreen` composes, asserting the transcript matches the concatenation without byte loss.
- [x] Unit test `ConnectionStateRepositoryTest` passing, verifying the buffering and draining logic.

## Test Execution Log
```
> Task :app:testDebugUnitTest
⏱️ TEST-METRIC: com.adamoutler.ssh.network.ConnectionStateRepositoryTest.test output is buffered before UI attaches and emitted after took 80ms

ConnectionStateRepositoryTest > test output is buffered before UI attaches and emitted after PASSED
```

## Implementation Details
- Added `outputBuffer: ConcurrentLinkedQueue<ByteArray>` and `isUiAttached: Boolean` to `ActiveSessionState`.
- Implemented `ConnectionStateRepository.emitOutput` to buffer data when `isUiAttached` is false.
- Implemented `ConnectionStateRepository.attachUiAndGetBuffer` to set `isUiAttached` to true and return the drained buffer.
- `TerminalScreen` calls `attachUiAndGetBuffer` during its initialization to retrieve any missed output.
