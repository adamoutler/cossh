# SSH-52: Upgrade AGP for 16KB page size alignment

## Issue
Android is moving toward 16KB page sizes for device memory allocation. This requires apps to 16KB align uncompressed shared libraries within the APK to function correctly and avoid memory mapping crashes on these newer devices.

## Resolution
1. Upgraded the Android Gradle Plugin (AGP) version in `gradle/libs.versions.toml` from `8.4.1` to `8.5.2`. AGP 8.5.1 and higher natively and automatically supports 16KB zip alignment.
2. Updated the Gradle distribution URL in `gradle/wrapper/gradle-wrapper.properties` from `gradle-8.7-rc-4-bin.zip` to `gradle-8.7-bin.zip` because AGP 8.5+ strictly enforces a minimum Gradle version of 8.7 stable.

## Verification
- Local compilation and unit testing passed successfully (`BUILD SUCCESSFUL in 1m 54s`) using `./gradlew test lint`.
- The Paparazzi visual regression tests confirmed no layout breakages resulting from the build tools upgrade.
- Pushing to remote triggers the GitHub CI validation loop.