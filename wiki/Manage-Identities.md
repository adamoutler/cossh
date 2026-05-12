# Manage Identities

The **Manage Identities** section allows you to securely manage your SSH cryptographic keys used to authenticate against remote servers.

## Features

- **Generate Keys:** Generate new RSA or Ed25519 key pairs directly on your device.
- **Import Keys:** Paste or load existing private keys to use for connections.
- **Secure Storage:** Private keys are securely encrypted using the Android Keystore system.
- **Public Key Extraction:** Easily copy the public key portion to add to your server's `~/.ssh/authorized_keys` file.

## Why use Identities?
Instead of relying on passwords, SSH keys provide a significantly more secure method of authentication that is resistant to brute-force attacks.