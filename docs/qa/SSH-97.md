# QA Proof & Architecture Certification: SSH-97

**Ticket:** SSH-97  
**Title:** Local Unprivileged Shell & AVF Micro-VM Architecture  
**Status:** Verification Complete  

---

## 1. User Story

*As a mobile developer and network administrator operating in offline environments, I want CoSSH to provide local unprivileged shell execution and detect Android Virtualization Framework (AVF) capabilities, so that I can execute diagnostic and administrative commands directly on-device without requiring an active remote SSH network connection.*

---

## 2. Implementation Summary

1. **AVF Architectural & Feasibility Study (`docs/architecture/local-vm-feasibility-study.md`):**
   - Conducted deep-dive technical evaluation into Android Virtualization Framework (`android.system.virtualmachine`), the `@SystemApi` `MANAGE_VIRTUAL_MACHINE` permission barrier on Google Play Store distribution, pKVM EL2 hypervisor isolation, `virtio-vsock` IPC, and resource footprints.
   - Designed CoSSH's dual-track architecture:
     - Track 1: Runtime hypervisor & permission capability detection (`AvfCapabilityDetector`).
     - Track 2: Embedded unprivileged local shell execution (`LocalShellConnectionHandler` / `Protocol.LOCAL`).
     - Track 3: Loopback Micro-VM SSH Bridge (`ConnectionProfile.createLocalVmProfile(port = 2222)`).

2. **AVF Runtime Capability Detection (`com.adamoutler.ssh.vm.AvfCapabilityDetector`):**
   - Implemented `AvfStatus` enum: `AVAILABLE_AND_PERMITTED`, `PERMISSION_REQUIRED`, `HYPERVISOR_UNSUPPORTED`, `API_LEVEL_UNSUPPORTED`.
   - Built defensive inspection method `getAvfStatus(context: Context, sdkInt: Int = Build.VERSION.SDK_INT)` auditing:
     - `sdkInt >= 34` (Android 14+).
     - `context.getSystemService("virtual_machine")` existence and accessibility.
     - `context.checkSelfPermission("android.permission.MANAGE_VIRTUAL_MACHINE") == PackageManager.PERMISSION_GRANTED`.
     - Defensive exception wrappers ensuring no `SecurityException` crashes can escape.

3. **Embedded Sandboxed Local Shell Handler (`com.adamoutler.ssh.network.LocalShellConnectionHandler`):**
   - Implements `ConnectionProtocol`.
   - Spawns `/system/bin/sh` (with automatic fallback to `/bin/sh` on non-Android test environments) via `ProcessBuilder`.
   - Configures merged standard error (`redirectErrorStream(true)`), sanitizes environment (`TERM=xterm-256color`, removes `LD_PRELOAD`), and navigates to optional `initialDirectory`.
   - Pipes standard I/O via `PtyStreamBridge` to `onOutput` and exposes standard input via `onConnect(process.outputStream, null)`.
   - Safe process lifecycle: cleanly destroys process (`destroy()`, `destroyForcibly()`) and closes streams on disconnection or coroutine cancellation while re-throwing `CancellationException` for structured concurrency.

4. **Protocol Model & Factory Wiring:**
   - Added `@SerialName("LOCAL") LOCAL` to `enum class Protocol` in `ConnectionProfile.kt`.
   - Added companion helpers `ConnectionProfile.createLocalShellProfile()` and `ConnectionProfile.createLocalVmProfile(port: Int = 2222)`.
   - Updated `ConnectionProtocolFactory.create(protocol: Protocol)` to route `Protocol.LOCAL -> LocalShellConnectionHandler()`.

---

## 3. Automated Verification Proof

### Targeted Unit Test Execution
Command executed:
```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64 QEMU_LD_PREFIX=/home/adamoutler/Android/x86_64-rootfs ./gradlew :app:testDebugUnitTest --tests "com.adamoutler.ssh.network.LocalShellConnectionHandlerTest" --tests "com.adamoutler.ssh.vm.AvfCapabilityDetectorTest"
```

Output:
```text
> Task :app:testDebugUnitTest
LocalShellConnectionHandlerTest > testUpdateWindowSizeNoOp PASSED
LocalShellConnectionHandlerTest > testLocalShellExecutionAndOutput PASSED
LocalShellConnectionHandlerTest > testInvalidShellBinaryHandlesErrorCleanly PASSED
LocalShellConnectionHandlerTest > testConnectionProtocolFactoryCreatesLocalHandler PASSED
LocalShellConnectionHandlerTest > testDisconnectTerminatesProcess PASSED
LocalShellConnectionHandlerTest > testConnectionProfileFactoryFunctions PASSED
LocalShellConnectionHandlerTest > testLocalShellWithInitialDirectoryAndEnvVars PASSED
AvfCapabilityDetectorTest > testSdkLessThan34ReturnsApiLevelUnsupported PASSED
AvfCapabilityDetectorTest > testServiceThrowsReturnsHypervisorUnsupported PASSED
AvfCapabilityDetectorTest > testPermissionCheckThrowsReturnsPermissionRequired PASSED
AvfCapabilityDetectorTest > testServicePresentPermissionDeniedReturnsPermissionRequired PASSED
AvfCapabilityDetectorTest > testServiceNullReturnsHypervisorUnsupported PASSED
AvfCapabilityDetectorTest > testServicePresentPermissionGrantedReturnsAvailableAndPermitted PASSED

BUILD SUCCESSFUL in 8s
31 actionable tasks: 7 executed, 24 up-to-date
```

### Full Scope Verification
All assigned files are strictly within ticket scope:
- `docs/architecture/local-vm-feasibility-study.md`
- `app/src/main/kotlin/com/adamoutler/ssh/vm/AvfCapabilityDetector.kt`
- `app/src/main/kotlin/com/adamoutler/ssh/network/LocalShellConnectionHandler.kt`
- `app/src/main/kotlin/com/adamoutler/ssh/data/ConnectionProfile.kt`
- `app/src/main/kotlin/com/adamoutler/ssh/network/ConnectionProtocolFactory.kt`
- `app/src/test/kotlin/com/adamoutler/ssh/network/LocalShellConnectionHandlerTest.kt`
- `app/src/test/kotlin/com/adamoutler/ssh/vm/AvfCapabilityDetectorTest.kt`
- `docs/qa/SSH-97.md`
- `user_stories/SSH-97.md`
