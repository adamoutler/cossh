# Verification Proof for SSH-58

## Summary
The goal of this ticket was to fix the `Android App Compatibility` warning shown on Android 15/16 devices that strictly enforce 16 KB page sizing. The error was `libtermux.so: LOAD segment not aligned`.

## Implementation
1. **Diagnosis:** Previous workarounds (`android:extractNativeLibs="true"` and AGP `useLegacyPackaging = true`) were discovered to only solve the APK zip alignment. Modern 16KB kernels strictly check the ELF LOAD segments of the underlying binaries themselves.
2. **Recompilation:** We identified that `libtermux.so` from `com.github.termux.termux-app:v0.118.3` was compiled with an older NDK resulting in 4KB ELF alignments. We downloaded the NDK 29 toolchain (`sdkmanager "ndk;29.0.14206865"`) which defaults to 16KB ELF alignment (`-Wl,-z,max-page-size=16384`).
3. **Integration:** We cloned the `termux-app` source code at tag `v0.118.3`, recompiled `libtermux.so` for `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`, and placed the newly built binaries into `app/src/main/jniLibs/` to override the dependencies packaged by JitPack.
4. **Verification:** `readelf -l` verified that the ELF LOAD segments are now correctly aligned to `0x4000` (16384 bytes). The application was successfully installed and launched on an IP-connected physical device running a strict 16KB kernel configuration.

## Artifacts
- `docs/qa/SSH-58-screenshot.png`: A screenshot taken directly from the strict 16KB device showing the application launching normally without throwing the "Android App Compatibility" dialog.
- The `libtermux.so` files committed to `app/src/main/jniLibs/` natively support 16KB page size.