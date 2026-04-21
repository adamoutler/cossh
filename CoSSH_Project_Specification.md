# CoSSH (Cobalt SSH) Project Specification

## 1. Product Overview & Architecture

**Problem Statement:** Following the removal of JuiceSSH from the Google Play Store, there is a notable absence of modern, natively built, and actively maintained SSH clients for Android. This creates a critical friction point for systems administrators, developers, and homelab enthusiasts who rely on rapid, mobile infrastructure access. Existing alternatives lack a modern architecture, sensible monetization, or open-source transparency.

**Core Objective:** CoSSH (Cobalt SSH) is a native Android SSH terminal client designed as a modern, robust, and secure replacement for legacy SSH apps. Built with a clean, cobalt-blue thematic aesthetic representing security and stability, the application prioritizes absolute speed, modern Android design paradigms, and reliability.

**Monetization & Contributor Model:**

* **Freemium Core:** Completely free for local use, with zero monthly fees, ads, or intrusive premium subscription prompts.
* **Cloud Sync:** The automated Google Drive cloud sync feature is gated by a one-time, non-consumable $10 in-app purchase.
* **Open Source Contributor Bypass:** Code contributors bypass the $10 sync fee. Maintainers securely generate native Google Play Promo Codes via the Google Play Console and distribute them upon successful PR merges. Contributors redeem these natively in the Play Store, unlocking the feature while remaining 100% compliant with Google Play monetization policies.

**User Experience:** Optimized for one-touch connectivity. Upon opening the app, a unified search/filter bar over the connections list allows for instant server retrieval, dropping the user straight into a remote terminal without secondary navigation.

**Authentication:** Full support for generating, importing, and utilizing standard SSH keys (RSA, ED25519) within the app, alongside traditional password authentication.

**Persistence and Security:**

* **Local Storage:** Keys and passwords are encrypted locally at rest using native Android Keystore-backed solutions (androidx.security:security-crypto).
* **Cloud Sync:** Because Android Keystore keys are bound to physical hardware, cross-device syncing requires an abstraction layer. Before uploading to the user's Google Drive (hidden App Data folder), the configuration payload is symmetrically encrypted using a user-defined "Sync Passphrase" utilizing native javax.crypto (AES-256-GCM). This ensures seamless recovery across device wipes and custom ROM flashes.

**Technical Architecture Stack:**

* **UI:** Kotlin + Jetpack Compose (Material Design 3).
* **Architecture:** MVVM utilizing Kotlin Coroutines and StateFlow for reactive UI states.
* **Backgrounding:** Android Foreground Services (with strict API 34+ typing) for SSH session lifecycle management to prevent connection drops by aggressive OS task killers.
* **SSH Protocol:** Headless JVM SSH library (e.g., sshj) executing on Coroutine I/O Dispatchers.

## Automated Testing & CI/CD Pipeline

The project relies on unit tests (`app/src/test`) and instrumented UI/E2E tests (`app/src/androidTest`).

### Full Test Run Mode
Long-running tests that are impractical for the standard fast CI/CD pipeline (e.g. End-to-End network connection tests) are annotated with `@FullTest`.
- By default, these tests are skipped in normal `./gradlew test` or `./gradlew connectedAndroidTest` runs.
- To execute the Full Test suite (recommended before major releases), supply the `fullTestRun` project property:
  ```bash
  ./gradlew connectedAndroidTest -PfullTestRun
  ```