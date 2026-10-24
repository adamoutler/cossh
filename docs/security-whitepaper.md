# CoSSH Security Architecture Whitepaper: Security Differentiators
**Application**: CoSSH (Cobalt SSH) — Native Android SSH Client  
**Epic**: SSH-188 — "Most Secure SSH Client on Android"  
**Classification**: Public Technical Whitepaper & Architectural Specification  

---

## Executive Summary
CoSSH is an uncompromising, defense-in-depth Android SSH client engineered to eliminate the systemic vulnerabilities of legacy terminal emulators. While standard mobile SSH clients store private keys in software application sandboxes and treat host keys with passive, friction-free prompts, CoSSH introduces hardware-enforced cryptographic boundaries:
1. **Dedicated StrongBox Keymaster/KeyMint Hardware Isolation** with cryptographic ASN.1 X.509 Key Attestation verification.
2. **Wire-Format OpenSSH User & Host Certificates** with Wildcard Host-CA Trust Stores and Zero-TOFU authentication.
3. **Monospace OpenSSH Visual Randomart (Drunken Bishop)** with 2500ms High-Friction MITM Interception Barriers.
4. **Tamper-Evident SHA-256 Hash-Chained Audit Logging** anchored to Android Keystore master keys.

---

## 1. Hardware-Backed StrongBox Keys & Key Attestation (SSH-227)

### 1.1 Threat Model
Software-backed private keys stored in SQLite databases, XML preferences, or even encrypted application sandboxes remain vulnerable to:
- Root-level OS exploits and kernel compromise.
- RAM extraction and cold-boot physical attacks.
- Process memory inspection via debugger attachment.

### 1.2 Cryptographic Architecture
CoSSH addresses this threat by delegating key generation and private key operations directly to dedicated Secure Elements (Google Titan M2, Samsung Knox Vault, NXP SE) via `KeyGenParameterSpec.Builder.setIsStrongBoxBacked(true)`:
- **Private Key Immobility**: Private keys are generated inside the tamper-resistant hardware chip. The key material is physically non-exportable; calls to `privateKey.encoded` return `null`.
- **Hardware Fallback Pipeline**: If a device lacks dedicated StrongBox hardware (`PackageManager.FEATURE_STRONGBOX_KEYSTORE`) or throws `StrongBoxUnavailableException`, CoSSH seamlessly falls back to ARM TrustZone Trusted Execution Environment (TEE) Keystore, categorizing security tiers visually:
  - 🛡️💎 `StrongBox Hardware-Backed` (Level 2)
  - 🔒 `TEE Keystore` (Level 1)
  - ⚠️ `Imported Software Key` (Level 0)
- **Key Attestation Verification**: CoSSH generates a cryptographically random 32-byte nonce challenge injected into `setAttestationChallenge`. Upon generation, CoSSH extracts the X.509 certificate chain and parses the Android Key Attestation extension (OID `1.3.6.1.4.1.11129.2.1.17`) using BouncyCastle ASN.1 parsers, validating:
  - `attestationSecurityLevel` (StrongBox vs TEE vs Software)
  - `attestationChallenge` bit-for-bit equality against the challenge nonce
  - Verified Boot state and OS patch level

### 1.3 Evidence & Verification
- Unit Tests: `StrongBoxKeyManagerTest.kt`, `CertificateAndAttestationDataTest.kt`
- Paparazzi Snapshot Proofs: `docs/qa/SSH-227-attestation-sheet.png`, `docs/qa/SSH-227-identity-badges.png`

---

## 2. OpenSSH Certificates & Host-CA Wildcard Trust (SSH-228)

### 2.1 Threat Model
Traditional SSH setups rely heavily on Trust On First Use (TOFU), exposing administrators to initial connection MITM attacks, key sprawl, and labor-intensive manual key rotations.

### 2.2 Wire Protocol & Verification Engine
CoSSH implements a complete native binary wire parser and validator following OpenSSH `PROTOCOL.certkeys`:
- **Wire Deserialization**: Parses `ssh-ed25519-cert-v01@openssh.com`, `ssh-rsa-cert-v01@openssh.com`, and `ecdsa-sha2-nistp*-cert-v01@openssh.com`.
- **Temporal Validity**: Enforces strict unsigned 64-bit epoch seconds comparison against system time, correctly handling OpenSSH infinity (`0xFFFFFFFFFFFFFFFFL`).
- **Critical Options Trap**: Rejects any certificate containing unrecognized critical options, strictly enforcing constraints such as `force-command` and `source-address`.
- **CA Signature Cryptographic Verification**: Computes SHA-256 / SHA-512 digests over the signed wire bytes and verifies the Certificate Authority signature across Ed25519, RSA-SHA2-256, RSA-SHA2-512, and ECDSA (with IEEE P1363 to DER conversion).
- **Wildcard Host-CA Trust Store**: Extends `known_hosts` parsing to process `@cert-authority` directives with wildcard pattern matching (e.g. `*.corp.internal`, `!untrusted.corp,*.corp`). Any host presenting a valid certificate signed by a trusted CA matching the connection pattern is authenticated automatically with **Zero-TOFU prompts**.

### 2.3 Evidence & Verification
- Unit Tests: `OpenSshCertificateParserTest.kt`, `OpenSshCertificateValidatorTest.kt`, `HostCaTrustStoreTest.kt`, `CertificateKeyProviderTest.kt`
- Paparazzi Snapshot Proofs: `docs/qa/SSH-228-cert-details.png`, `docs/qa/SSH-228-host-ca-expired.png`

---

## 3. OpenSSH Drunken Bishop Randomart & High-Friction MITM Modal (SSH-229)

### 3.1 Threat Model
Humans cannot reliably parse or memorize 43-character Base64 SHA-256 public key fingerprints (e.g., `SHA256:uNp2...`). During TOFU or host key change warnings, users reflexively tap "Accept", succumbing to MITM interception.

### 3.2 Algorithmic Design & Friction Engineering
CoSSH incorporates the authentic OpenSSH Drunken Bishop algorithm:
- **17x9 Monospace Field**: Simulates a bishop starting at $(8, 4)$ walking across a 17-column by 9-row grid bounded by walls.
- **Walk Traversal**: Each byte of the SHA-256 key fingerprint provides 4 pairs of 2-bit direction steps (NW, NE, SW, SE).
- **Character Augmentation**: Hit counters map to the OpenSSH palette `[ .o+=*BOX@%&#/^SE]`, marking the start position with `'S'` and the final position with `'E'`.
- **Obsidian Terminal Container**: Displayed in high-contrast `#0B0F19` with cyan framing (`#38BDF8`), green start `'S'` (`#10B981`), and amber end `'E'` (`#F59E0B`).
- **High-Friction MITM Interception**: When a previously trusted host key changes, CoSSH blocks execution with a high-contrast side-by-side modal (Expected Host Key vs Received Host Key) and requires a **2500ms Hold-to-Confirm gesture** with haptic feedback to prevent accidental acceptance.

### 3.3 Evidence & Verification
- Unit Tests: `OpenSshRandomartGeneratorTest.kt` (bit-for-bit match against OpenSSH reference vectors)
- Paparazzi Snapshot Proofs: `docs/qa/SSH-229-randomart-ed25519.png`, `docs/qa/SSH-229-randomart-rsa.png`

---

## 4. Tamper-Evident SHA-256 Hash-Chained Security Audit Log (SSH-230)

### 4.1 Threat Model
Local log files can be modified, truncated, reordered, or deleted by compromised apps or root processes to cover tracks following unauthorized access.

### 4.2 Cryptographic Chain Architecture
CoSSH implements continuous cryptographic hash chaining for all security-critical events:
- **Hash Chaining Formula**:
  $$H_n = \text{SHA-256}\Big(\text{CanonicalBytes}\big(R_n\big) \parallel H_{n-1}\Big)$$
  where $H_0 = 0^{64}$ (64-character hex zero genesis hash).
- **Canonical Serialization**: Event attributes (timestamp, sequence number, event type, metadata) are sorted lexicographically before hashing to prevent byte serialization drift.
- **Hardware Head Anchoring**: The head tuple `(lastSequenceNumber, lastHeadHash)` is stored in `EncryptedSharedPreferences` backed by the hardware Android Keystore MasterKey. If an attacker truncates the log file, the mismatch with the Keystore head pointer immediately triggers a **Head Anchor Rollback Violation**.
- **POSIX Disk Sync**: Every append invokes `FileChannel.force(true)` (`fsync`) under coroutine `Mutex` synchronization, ensuring power loss or app crashes never corrupt the cryptographic chain.
- **Tamper Detection**: The verification engine detects single-bit payload mutations, sequence gaps, entry reordering, and unauthorized insertions.

### 4.3 Evidence & Verification
- Unit Tests: `SecurityAuditLoggerTest.kt` (1,000-entry continuous chain test, 50-thread concurrent stress test, mutation tests), `SecurityAuditLogManagerTest.kt`
- Paparazzi Snapshot Proofs: `docs/qa/SSH-230-audit-log-verified.png`, `docs/qa/SSH-230-audit-log-tampered.png`

---

## 5. Summary Matrix of Security Controls

| Security Dimension | Legacy Android SSH Clients | CoSSH (Cobalt SSH) |
| :--- | :--- | :--- |
| **Private Key Storage** | Software SQLite / App Files | **Hardware StrongBox (Titan M2) / TEE** |
| **Key Attestation** | None | **ASN.1 X.509 Extension (OID 1.3.6.1.4.1.11129.2.1.17)** |
| **Certificate Auth** | None / Incomplete | **OpenSSH User & Host Certificates (Ed25519/RSA/ECDSA)** |
| **Host-CA Trust** | None | **`@cert-authority` Wildcards with Zero-TOFU** |
| **Visual Host Key Verification** | Hex/Base64 Text Only | **Monospace Drunken Bishop Randomart (17x9 Grid)** |
| **MITM Protection Barrier** | Single tap dismissible dialog | **Side-by-Side Dual Randomart & 2500ms Hold Barrier** |
| **Security Audit Logging** | Unsecured logcat / plaintext | **SHA-256 Hash-Chained Log with Keystore Head Anchor** |
