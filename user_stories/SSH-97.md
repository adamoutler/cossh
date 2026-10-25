# User Story: SSH-97 - Local Unprivileged Shell & AVF Micro-VM Architecture

**As a mobile developer and network administrator operating in offline environments,**
I want CoSSH to provide local unprivileged shell execution and detect Android Virtualization Framework (AVF) capabilities,
**so that** I can execute diagnostic and administrative commands directly on-device without requiring an active remote SSH network connection.

## Acceptance Criteria
1. **Architectural & Feasibility Study (`docs/architecture/local-vm-feasibility-study.md`):**
   - Provide comprehensive architectural and feasibility study analyzing AVF (`android.system.virtualmachine`), the `@SystemApi` `MANAGE_VIRTUAL_MACHINE` permission barrier on Google Play Store distribution, pKVM EL2 hypervisor isolation, `virtio-vsock` IPC, and resource footprints.
   - Formulate CoSSH's dual-track architecture: (a) runtime capability detection, (b) embedded unprivileged local shell execution (`Protocol.LOCAL`), and (c) loopback micro-VM SSH bridging.
2. **AVF Runtime Capability Detection (`AvfCapabilityDetector` & `AvfStatus`):**
   - Provide `AvfCapabilityDetector` returning structured statuses: `AVAILABLE_AND_PERMITTED`, `PERMISSION_REQUIRED`, `HYPERVISOR_UNSUPPORTED`, and `API_LEVEL_UNSUPPORTED`.
   - Safely handle system service and permission checks (`Build.VERSION.SDK_INT >= 34`, `context.getSystemService("virtual_machine")`, `android.permission.MANAGE_VIRTUAL_MACHINE`) with defensive exception handling.
3. **Embedded Sandboxed Local Shell Handler (`LocalShellConnectionHandler`):**
   - Implement `ConnectionProtocol` contract.
   - Execute `/system/bin/sh` (with `/bin/sh` host fallback for unit testing) via `ProcessBuilder`.
   - Merge error streams (`redirectErrorStream(true)`), sanitize environment (`TERM=xterm-256color`, strip `LD_PRELOAD`), and support optional valid `initialDirectory`.
   - Pipe I/O via `PtyStreamBridge` and guarantee clean process termination and stream closure on disconnect or coroutine cancellation.
4. **Data Models & Factory Wiring (`ConnectionProfile` & `ConnectionProtocolFactory`):**
   - Add `@SerialName("LOCAL") LOCAL` to `Protocol` enum.
   - Add companion helpers `createLocalShellProfile()` and `createLocalVmProfile(port: Int = 2222)` to `ConnectionProfile`.
   - Wire `Protocol.LOCAL -> LocalShellConnectionHandler()` in `ConnectionProtocolFactory`.
5. **Zero-Tolerance Quality:**
   - Zero warnings, no unhandled exceptions, strictly safe typing, comprehensive automated unit test suite.

## Verification Proof
* **Architecture Documentation:**
  - `docs/architecture/local-vm-feasibility-study.md`
* **Core Implementation:**
  - `app/src/main/kotlin/com/adamoutler/ssh/vm/AvfCapabilityDetector.kt`
  - `app/src/main/kotlin/com/adamoutler/ssh/network/LocalShellConnectionHandler.kt`
  - `app/src/main/kotlin/com/adamoutler/ssh/data/ConnectionProfile.kt`
  - `app/src/main/kotlin/com/adamoutler/ssh/network/ConnectionProtocolFactory.kt`
* **Automated Unit Tests:**
  - `app/src/test/kotlin/com/adamoutler/ssh/network/LocalShellConnectionHandlerTest.kt`
  - `app/src/test/kotlin/com/adamoutler/ssh/vm/AvfCapabilityDetectorTest.kt`
* **Committed QA Proof:** `docs/qa/SSH-97.md`
