# CoSSH Privacy Policy

**Last Updated:** November 8, 2026

Welcome to CoSSH (Cobalt SSH). This Privacy Policy explains how we handle your data when you use the CoSSH Android application.

## 1. Data Collection and Analytics
**We do not collect any personal data, usage analytics, telemetry, or crash reports.** CoSSH is engineered with a strict "security-first" and "zero-trust" architecture. Everything you do within the app stays locally on your device or within your private, authenticated cloud storage. We do not incorporate third-party tracking frameworks, telemetry SDKs, or external crash reporters (e.g., Google Analytics, Firebase Crashlytics).

## 2. Local Data Storage, Hardware Security Boundaries, and Encryption
All your SSH connection profiles, hostnames, usernames, passwords, and cryptographic keys are aggressively encrypted at rest on your device.
* **Hardware Security Boundaries:** CoSSH utilizes the Android Keystore system with hardware-backed security modules:
  * **StrongBox Keymaster:** Dedicated tamper-resistant hardware security chip (HSM) with isolated CPU and secure storage for hardware-bound cryptographic operations where supported by hardware.
  * **Trusted Execution Environment (TEE):** Hardware-isolated cryptographic execution boundary protecting key material from the Android OS kernel and user space.
  * **FIDO2 / WebAuthn & Hardware Security Keys:** Native cryptographic architecture supporting hardware-bound authentication tokens (`sk-ssh-ed25519@openssh.com`, `sk-ecdsa-sha2-nistp256@openssh.com`).
* **Encrypted Storage:** Configuration metadata and non-sensitive attributes are stored using `EncryptedSharedPreferences` with AES-256-SIV and AES-256-GCM encryption.
* **Volatile State Sanitization:** Sensitive credentials (such as cleartext passwords and private key byte arrays) reside only ephemerally in mutable memory (`ByteArray` / `CharArray`) for the absolute minimum duration required to negotiate SSH authentication, and are unconditionally zeroed out (`clearSensitiveData()`) immediately after use, even in the event of an unexpected exception.

## 3. Cloud Sync (Google Drive Integration) & Remote Data Deletion
CoSSH offers an optional Cloud Sync feature that backs up your encrypted connection profiles and keys to your personal Google Drive.
* **Scope:** The app requests the `https://www.googleapis.com/auth/drive.appdata` scope. This grants CoSSH access *only* to a hidden, application-specific folder within your personal Google Drive. CoSSH cannot view, read, modify, or delete any personal files, photos, or documents elsewhere in your Google Drive.
* **End-to-End Encryption:** Before any backup blob leaves your device, the payload is symmetrically encrypted using AES-256-GCM. The encryption key is derived using PBKDF2WithHmacSHA256 with 250,000 iterations and a cryptographically secure 256-bit salt.
* **Zero Knowledge:** Because cryptographic derivation and encryption execute exclusively on your local device using a user-supplied Master Passphrase, neither the developers of CoSSH nor Google can decrypt or inspect your SSH credentials. If you lose your Sync Passphrase, your backup cannot be decrypted or recovered.
* **Remote Data Deletion:** Users retain complete sovereignty over their data. You can delete and purge your remote synchronized backup files from Google Drive at any time directly through the CoSSH Settings screen, or by managing third-party app access in your Google Account security settings (`Connected apps & services`). Revoking access removes the app's ability to communicate with your Google Drive.

## 4. Android Device Permissions
CoSSH requests only the minimal set of Android permissions strictly required to execute its core terminal and networking functions:
* **`android.permission.INTERNET`:** Required to initiate direct outbound SSH and Telnet socket connections to remote servers configured by the user, and to interact with Google Play Billing and Google Drive APIs exclusively when Cloud Sync is initiated.
* **`android.permission.USE_BIOMETRIC`:** Required to prompt for biometric verification (fingerprint or face unlock) before unlocking hardware-bound keys stored in Android Keystore / StrongBox.
* **`android.permission.POST_NOTIFICATIONS`:** Required on Android 13+ (API 33+) to display non-intrusive ongoing notifications reflecting the foreground terminal service status and active background SSH sessions.
* **`android.permission.WAKE_LOCK`:** Used optionally when keep-screen-on mode or background keep-alive is active to prevent CPU sleep from abruptly dropping active SSH socket connections.
* **`android.permission.FOREGROUND_SERVICE`:** Required to maintain long-lived interactive SSH sessions in the background while users switch between Android applications.
* **`android.permission.FOREGROUND_SERVICE_SPECIAL_USE`:** Declared for Android 14+ (API 34+) to classify the persistent interactive SSH terminal connection service under system special-use guidelines.

## 5. Network Communications
The only network communications CoSSH makes are:
1. Directly to the SSH or Telnet servers you explicitly configure and connect to.
2. To Google's servers (Play Billing API and Google Drive REST API) strictly for processing the Cloud Sync entitlement and transferring your end-to-end encrypted backup blob, provided you have explicitly enabled the feature.

## 6. Changes to This Policy
We may update our Privacy Policy periodically to reflect architectural or regulatory updates. We will notify you of any changes by updating the "Last Updated" date at the top of this document.

## 7. Contact
If you have questions, feedback, or security inquiries regarding this Privacy Policy, please open an issue on the CoSSH GitHub repository.
