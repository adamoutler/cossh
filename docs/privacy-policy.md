# CoSSH Privacy Policy

**Last Updated:** April 21, 2026

Welcome to CoSSH (Cobalt SSH). This Privacy Policy explains how we handle your data when you use the CoSSH Android application.

## 1. Data Collection and Analytics
**We do not collect any personal data, usage analytics, telemetry, or crash reports.** CoSSH is designed with a strict "security first" and "paranoiac" architecture. Everything you do within the app stays on your device or within your private, authenticated cloud storage. We do not use third-party tracking frameworks (e.g., Google Analytics, Firebase Crashlytics). 

## 2. Local Data Storage and Encryption
All your SSH connection profiles, hostnames, usernames, passwords, and cryptographic keys (RSA/ED25519) are aggressively encrypted at rest on your device.
* We utilize the Android Keystore system and `EncryptedSharedPreferences`.
* Sensitive data (such as passwords and private keys) is encrypted independently before any internal serialization occurs. 
* Sensitive data is only held in volatile memory (RAM) for the absolute minimum time required to authenticate and is actively wiped/zeroed out immediately after use.

## 3. Cloud Sync (Google Drive Integration)
CoSSH offers an optional, premium Cloud Sync feature that backs up your encrypted connection profiles and keys to your personal Google Drive.
* **Scope:** The app requests the `https://www.googleapis.com/auth/drive.appdata` scope. This grants CoSSH access *only* to a hidden, application-specific folder within your Google Drive. CoSSH cannot see, read, modify, or delete any of your personal files, photos, or documents in your Google Drive.
* **End-to-End Encryption:** Before any data leaves your device for sync, the entire payload is symmetrically encrypted using AES-256-GCM. The encryption key is derived from a custom "Sync Passphrase" that you create using PBKDF2 (with a minimum of 65,536 iterations and a random salt). 
* **Zero Knowledge:** Because the encryption happens on your device using a passphrase only you know, neither the developers of CoSSH nor Google can read or access your SSH credentials. If you lose your Sync Passphrase, your synced data cannot be recovered.

## 4. Network Communications
The only network communications CoSSH makes are:
1. Directly to the SSH servers you explicitly configure and connect to.
2. To Google's servers (Play Billing API and Google Drive REST API) strictly for processing the Cloud Sync entitlement and transferring your encrypted backup blob, provided you have enabled the feature.

## 5. Changes to This Policy
We may update our Privacy Policy from time to time. We will notify you of any changes by updating the "Last Updated" date at the top of this page.

## 6. Contact
If you have any questions or suggestions about our Privacy Policy, please open an issue on the CoSSH GitHub repository.
