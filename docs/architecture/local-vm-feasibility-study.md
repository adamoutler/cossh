# CoSSH Architecture & Feasibility Study: Android Virtualization Framework (AVF) & Local Shell Execution

**Ticket:** SSH-97  
**Status:** Feasibility Complete / Architecture Ratified  
**Author:** Android Security & Systems Architecture  

---

## 1. Executive Summary

As a premier Android SSH and terminal client, CoSSH is evaluated for offline, local computing environments. Users require terminal capabilities directly on device without requiring remote SSH infrastructure. Two distinct technical mechanisms exist on modern Android to fulfill this demand:
1. **Local Unprivileged Shell Execution (`Protocol.LOCAL`):** Direct process execution of `/system/bin/sh` within the application sandbox UID.
2. **Hardware-Accelerated Micro-VMs via AVF (`android.system.virtualmachine`):** Launching lightweight guest Linux virtual machines via Google's Android Virtualization Framework (AVF) backed by pKVM.

This feasibility study evaluates the architectural viability, security invariants, permission constraints, and resource overhead of AVF on Android 14/15+, establishes the limitations imposed by Google Play Store distribution policies, and specifies CoSSH's production dual-track solution.

---

## 2. Android Virtualization Framework (AVF) Deep-Dive

### 2.1 AVF Architecture & pKVM Hypervisor
Introduced experimentally in Android 13 and formally standardized in Android 14 (API 34) and Android 15 (API 35), AVF leverages the **Protected KVM (pKVM)** hypervisor running at ARM Exception Level 2 (EL2).

```
+-------------------------------------------------------------+
| Non-Secure EL1 (Host Android OS)                            |
|  +---------------------+      +--------------------------+  |
|  | CoSSH Application   |      | virtualizationservice    |  |
|  | (UID app_xxxx)      | <--> | (Native Daemon)          |  |
|  +---------------------+      +-------------+------------+  |
|                                             |               |
|                                             v               |
|                                       +------------+        |
|                                       | crosvm     |        |
|                                       +-----+------+        |
+---------------------------------------------|---------------+
| EL2 (pKVM Hypervisor)                       | virtio-vsock  |
|  +------------------------------------------v------------+  |
|  | Guest Micro-VM (Isolated EL1)                         |  |
|  | +---------------------------------------------------+ |  |
|  | | Guest Linux Kernel (Microdroid / Custom Distro)   | |  |
|  | | +-----------------------------------------------+ | |  |
|  | | | Dropbear / OpenSSH Server (127.0.0.1:2222)     | | |  |
|  | | +-----------------------------------------------+ | |  |
|  | +---------------------------------------------------+ |  |
|  +-------------------------------------------------------+  |
+-------------------------------------------------------------+
```

Key AVF system components include:
- **`virtualizationservice`:** A privileged system daemon managing VM lifecycles and enforcing SELinux domains.
- **`crosvm`:** The virtual machine monitor (VMM) running in a restricted sandbox, managing guest memory allocation and virtual devices (`virtio-blk`, `virtio-vsock`, `virtio-console`).
- **Memory Isolation:** pKVM cryptographically unmaps guest memory pages from the host kernel page tables, preventing even a compromised host kernel from inspecting guest RAM.

### 2.2 IPC & Networking: `virtio-vsock` vs Loopback
Micro-VMs in AVF do not have direct TAP/TUN network interfaces bridged to the host Wi-Fi or cellular network without root or system privileges. Instead:
- Inter-VM communication is strictly mediated via **`virtio-vsock`** (AF_VSOCK CID sockets).
- For an SSH client like CoSSH to communicate with a guest micro-VM over TCP, an internal vsock-to-loopback proxy or embedded forwarding service is required to map `127.0.0.1:2222` to the guest's vsock port.

### 2.3 Resource & Memory Footprint
- **Kernel Image:** A minimal Microdroid or custom Alpine Linux kernel requires 15–30 MB storage.
- **RAM Footprint:** Each active micro-VM requires a minimum contiguous physical memory allocation of 128 MB to 512 MB. On memory-constrained devices (4 GB to 6 GB RAM), Android's `lowmemorykiller` (LMK) will aggressively terminate background processes to satisfy VM allocations.

---

## 3. Regulatory, Permission & Play Store Constraints

### 3.1 The `@SystemApi` Constraint & `MANAGE_VIRTUAL_MACHINE`
The fundamental operational barrier for commercial distribution is permission gating:
- `android.system.virtualmachine.VirtualMachineManager` is annotated with `@SystemApi`.
- Creating and managing custom virtual machines requires the signature/privileged permission:
  ```xml
  <uses-permission android:name="android.permission.MANAGE_VIRTUAL_MACHINE" />
  ```
- **Permission Protection Level:** `signature|privileged|development`.

### 3.2 Google Play Store Policy
Third-party applications distributed via Google Play Store **cannot** be granted `android.permission.MANAGE_VIRTUAL_MACHINE` automatically:
- Non-system APKs declaring this permission will fail automated Play Store submission checks unless granted via special OEM pre-installation or developer manual intervention via ADB:
  ```bash
  adb shell pm grant com.adamoutler.ssh android.permission.MANAGE_VIRTUAL_MACHINE
  ```
- Consequently, hard-coding a mandatory AVF dependency would render CoSSH unpublishable and unusable for standard consumers.

---

## 4. The CoSSH Production Dual-Track Architecture

To achieve uncompromised local execution while remaining 100% compliant with Google Play Store policies and forward-compatible with emerging Android virtualization APIs, CoSSH implements a **Dual-Track Solution**:

```
                       [Connection Request]
                                 |
           +---------------------+---------------------+
           |                                           |
    [Protocol.LOCAL]                            [Protocol.SSH]
           |                                           |
           v                                           v
[LocalShellConnectionHandler]             [Is Destination Loopback?]
  - Spawn /system/bin/sh                     /                  \
  - UID sandbox execution                   YES                  NO
  - Zero permissions needed                /                       \
  - PtyStreamBridge I/O                   v                         v
                                [AVF / Local VM]             [Remote Host]
                                - 127.0.0.1:2222             - Host:Port
                                - AvfCapabilityDetector      - SshConnectionHandler
                                - Dropbear / OpenSSH
```

### 4.1 Track 1: Runtime Capability Auditing (`AvfCapabilityDetector`)
CoSSH does not crash or assume virtualization availability. The `AvfCapabilityDetector` evaluates the host environment at runtime:
- **`API_LEVEL_UNSUPPORTED`:** Devices running Android 13 or lower (`Build.VERSION.SDK_INT < 34`).
- **`HYPERVISOR_UNSUPPORTED`:** Android 14+ devices where the OEM did not enable pKVM in the kernel or where `virtual_machine` service is absent from `ServiceManager`.
- **`PERMISSION_REQUIRED`:** AVF is present, but `MANAGE_VIRTUAL_MACHINE` has not been granted (typical Play Store consumer installation). The UI can instruct advanced users on ADB grant procedures.
- **`AVAILABLE_AND_PERMITTED`:** Fully enabled environment (development builds, rooted devices, or ADB-granted environments).

### 4.2 Track 2: Embedded Sandboxed Local Shell (`LocalShellConnectionHandler`)
For immediate, zero-configuration local terminal usage:
- Registered as `Protocol.LOCAL` in `ConnectionProfile`.
- Direct execution of `/system/bin/sh` with merged error streams (`redirectErrorStream(true)`).
- Environment sanitization: enforces `TERM=xterm-256color` and purges dangerous variables (`LD_PRELOAD`).
- Supports user-specified `initialDirectory` when navigating app-accessible storage.
- Standardized non-blocking I/O stream piping via `PtyStreamBridge` complying with the `ConnectionProtocol` contract.

### 4.3 Track 3: Loopback Micro-VM SSH Bridge (`createLocalVmProfile`)
For isolated containerized Linux environments (AVF Microdroid, QEMU, or Termux PRoot):
- Factory profile helper: `ConnectionProfile.createLocalVmProfile(port = 2222)`.
- Connects over `127.0.0.1:2222` utilizing CoSSH's existing hardened SSH stack (`SshConnectionHandler`, Keystore-backed key auth, TOFU verification).
- This decouples the terminal client from the VM lifecycle, allowing CoSSH to connect to any local VM or container transparently without requiring privileged system APIs in the core app.

---

## 5. Security & Threat Modeling

| Threat Vector | Severity | Mitigation in CoSSH |
|---|---|---|
| Sandbox Escape via Local Shell | Critical | `/system/bin/sh` executes under standard Android Linux UID isolation (`u0_aXXX`). Strict SELinux `untrusted_app` policies prevent unauthorized access to other apps' data. |
| Insecure Environment Injection | High | `LocalShellConnectionHandler` strips `LD_PRELOAD` from the process builder environment and enforces sanitized terminal variables. |
| Resource Exhaustion (Fork Bombs) | High | `cleanupProcess()` guarantees forceful termination (`destroy()` -> `destroyForcibly()`) and stream closure on session disconnect or coroutine cancellation. |
| AVF Service Unauthorized Access | Medium | Safe reflection/try-catch in `AvfCapabilityDetector` prevents `SecurityException` crashes when querying restricted system services. |

---

## 6. Conclusion & Roadmap

The combination of `AvfCapabilityDetector`, `LocalShellConnectionHandler` (`Protocol.LOCAL`), and loopback SSH bridging (`Protocol.SSH` to `127.0.0.1:2222`) provides an optimal, battle-tested architecture. It delivers immediate local shell access for all Android versions while cleanly architecting the pathway for future privileged AVF micro-VM management.
