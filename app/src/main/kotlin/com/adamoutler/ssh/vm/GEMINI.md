# Android Virtualization Framework (AVF) Module Context

## Logical Purpose
The `com.adamoutler.ssh.vm` package encapsulates hardware and operating system capability inspection for the Android Virtualization Framework (AVF) and Protected KVM (pKVM). It provides safe runtime probing to detect whether local virtual machines can be hosted on the device.

## Key Components & APIs
- **`AvfCapabilityDetector`**: Core capability inspection engine.
  - Queries system service `virtual_machine` and validates caller permissions (`android.permission.MANAGE_VIRTUAL_MACHINE`).
  - Evaluates platform API level requirements (Android 14 / API 34+).
- **`AvfStatus`**: State enumeration reflecting AVF availability:
  - `AVAILABLE_AND_PERMITTED` — Hypervisor present and calling process possesses required VM management permissions.
  - `PERMISSION_REQUIRED` — Hypervisor present but app lacks permission.
  - `HYPERVISOR_UNSUPPORTED` — System does not provide the `virtual_machine` service or lacks pKVM support.
  - `API_LEVEL_UNSUPPORTED` — Device is running Android 13 or lower (SDK < 34).
- **`getAvfStatus(context, sdkInt)`**: Top-level entry function for runtime querying.

## Behavioral Contracts & Design Patterns
- **Fail-Safe Runtime Inspection**: Service retrieval and permission checks are guarded against class loading or security exceptions to ensure compatibility across diverse OEM builds.
- **Strict API Gating**: Early-exits on unsupported API levels without querying absent system services.

## Dependencies
- Android Framework (`android.content.Context`, `android.content.pm.PackageManager`, `android.os.Build`)
