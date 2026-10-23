# Manage Identities

The **Manage Identities** section allows you to securely manage your SSH cryptographic keys used to authenticate against remote servers.

## Features

- **Generate Keys:** Generate new Ed25519, ECDSA (NIST P-256 / P-384 / P-521), or RSA-4096 key pairs directly on your device.
- **Import Keys:** Paste or load existing private keys via direct text entry or the Storage Access Framework (SAF) file picker (`.pem`, `.key`, `.pkcs8`, `id_*`).
- **Encrypted Key Decryption:** Automatically detects encrypted keys (OpenSSH bcrypt-pbkdf, PKCS#8, and legacy OpenSSL PEM) and presents an interactive passphrase prompt with zero-wipe memory protection (`FLAG_SECURE`, `CharArray`).
- **Secure Storage:** Private keys are securely encrypted using the Android Keystore system.
- **Public Key Extraction:** Easily copy the public key portion to add to your server's `~/.ssh/authorized_keys` file.

## Why use Identities?
Instead of relying on passwords, SSH keys provide a significantly more secure method of authentication that is resistant to brute-force attacks. CoSSH ensures that imported key material never leaves device memory unprotected and clears all plaintext decryption buffers immediately.