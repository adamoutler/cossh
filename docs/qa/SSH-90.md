# QA Proof: SSH-90 - Implement SSH Key Generation and Remote Injection (ssh-copy-id)

**User Story:** *As a user, I need a way to generate a new SSH key pair and securely inject it into a remote server when setting up a connection, so that I can easily establish passwordless access.*

**Verification Proof:**
- [x] Unit test `SSHKeyGeneratorEncodingTest` passing, verifying that `SSHKeyGenerator` produces valid OpenSSH formatted public keys (ssh-ed25519 and ssh-rsa).
- [x] Unit test `SshConnectionManagerInjectionTest` passing, verifying regex validation for public keys and injection command generation.
- [x] UI implementation: "Gen Ed25519" and "Gen RSA-4096" buttons added to `AddEditIdentityScreen`.
- [x] UI implementation: "Inject to Server (ssh-copy-id)" button and `InjectKeyDialog` implemented.

## Test Execution Log
```
⏱️ TEST-METRIC: com.adamoutler.ssh.crypto.SSHKeyGeneratorEncodingTest.testGenerateAndEncodeRSA took 9905ms
SSHKeyGeneratorEncodingTest > testGenerateAndEncodeRSA PASSED
⏱️ TEST-METRIC: com.adamoutler.ssh.crypto.SSHKeyGeneratorEncodingTest.testGenerateAndEncodeEd25519 took 504ms
SSHKeyGeneratorEncodingTest > testGenerateAndEncodeEd25519 PASSED
⏱️ TEST-METRIC: com.adamoutler.ssh.network.SshConnectionManagerInjectionTest.testInjectPublicKey_ValidKey_AttemptConnect took 7174ms
SshConnectionManagerInjectionTest > testInjectPublicKey_ValidKey_AttemptConnect PASSED
⏱️ TEST-METRIC: com.adamoutler.ssh.network.SshConnectionManagerInjectionTest.testInjectPublicKey_InvalidKey_ReturnsFalse took 177ms
SshConnectionManagerInjectionTest > testInjectPublicKey_InvalidKey_ReturnsFalse PASSED
```

## Implementation Details
- Enhanced `SSHKeyGenerator` with OpenSSH public key encoding logic for Ed25519 and RSA.
- Implemented `SshConnectionManager.injectPublicKey` with strict regex validation (`^[a-zA-Z0-9+/= \-_@]+$`) to prevent shell injection.
- Added key generation and remote injection UI to `AddEditIdentityScreen`.
- Injection command follows the standard security protocol: `mkdir -p ~/.ssh && chmod 700 ~/.ssh && echo "$publicKey" >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys`.
