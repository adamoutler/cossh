# SSH-32 QA Verification

**User Story:** As a user, I need to connect to a server using a password so that I can access my remote machine without the app crashing.

## Fix Applied
The fatal JVM crash during password authentication was caused by `net.schmizz.sshj.SSHClient` utilizing a BouncyCastle cryptographic implementation that collided with the system's restrictive BouncyCastle algorithms during `authPassword`. This was mitigated by explicitly configuring `SSHClient(net.schmizz.sshj.AndroidConfig())` inside `SshConnectionManager.kt` which registers the correct Security providers and Android-specific implementations for `sshj`. 

## Verification Proof

1. **Visual/UI Changes:** A screenshot (`docs/qa/SSH-32-password-auth.png`) shows the connection to the mock SSHD server being successfully initiated without crashing the UI thread.
2. **Logic/Backend Changes:** The test execution log `docs/qa/SSH-32.log` records the `ConnectionCrashTest` passing.
3. **File/System Changes:** The full execution logcat (`docs/qa/SSH-32_logcat.txt`) demonstrates a successful connection to a password-based connection profile with zero `FATAL EXCEPTION` signals.
