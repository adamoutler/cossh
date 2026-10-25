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

## 5. Post-Quantum Hybrid Key Exchange: sntrup761x25519-sha512@openssh.com (SSH-234)

### 5.1 Threat Model: Harvest-Now-Decrypt-Later (HNDL)
Adversaries with large-scale passive interception capabilities routinely record encrypted SSH transit flows across public networks and Tier-1 transit backbones. While modern elliptic-curve Diffie-Hellman exchanges (X25519) resist classical supercomputers, Shor's algorithm running on a cryptographically relevant quantum computer (CRQC) will solve the discrete logarithm problem in polynomial time, retroactively decrypting decades of captured ciphertext.

### 5.2 Cryptographic Hybrid Architecture
To protect long-lived confidential infrastructure sessions, CoSSH implements the standard OpenSSH hybrid post-quantum key exchange `sntrup761x25519-sha512@openssh.com`:
- **Dual Cryptographic Hardness**: Combines Streamlined NTRU Prime `sntrup761` (a lattice-based Key Encapsulation Mechanism resisting quantum cryptanalysis) with classical Curve25519 (`X25519`). An adversary must break *both* mathematical primitives simultaneously to decrypt session keys.
- **Wire Exchange Sizing**:
  - Ephemeral Client Public Key: 1190 bytes (1158 bytes `sntrup761` public key $\parallel$ 32 bytes `X25519` public key).
  - Ephemeral Server Public Key: 1071 bytes (1039 bytes `sntrup761` ciphertext encapsulation $\parallel$ 32 bytes server `X25519` public key).
- **Shared Secret Combiner**:
  $$K = \text{SHA-512}\Big(ss_{\text{sntrup761}} \parallel ss_{\text{x25519}}\Big)$$
  where both shared secrets are 32 bytes, producing a 64-byte combined secret encoded on the wire as an SSH `mpint`.
- **Volatile State Sanitization**: Intermediate raw shared secrets ($ss_{\text{sntrup761}}$ and $ss_{\text{x25519}}$) and combined digest arrays are strictly zeroized (`.fill(0)`) inside `finally` blocks immediately after `BigInteger(1, combined)` derivation, eliminating volatile memory retention.
- **Suite Hardening**: Registered at index 0 of `keyExchangeFactories` in `SecureSshConfig`, prioritizing post-quantum negotiation by default.

### 5.3 Evidence & Verification
- Unit Tests: `Sntrup761X25519KeyExchangeTest.kt` (keypair generation, server encapsulation / client decapsulation, bit-for-bit shared secret equivalence, zero-wiping verification, wire exchange state machine), `SecureSshConfigTest.kt`
- QA Audit: `docs/qa/SSH-234.md`

---

## 6. DNSSEC-Authenticated Host Key Validation: RFC 4255 / RFC 8484 SSHFP (SSH-234)

### 6.1 Threat Model: TOFU Blind Trust & DNS Poisoning
Standard SSH client implementations suffer from Trust-On-First-Use (TOFU) vulnerabilities during initial server connections. If a DNS resolver is poisoned or an upstream router executes an ARP/BGP MITM interception, the client prompts the user to trust an attacker's rogue host key. In mobile environments where DNS queries traverse untrusted public Wi-Fi or cellular gateways, cleartext DNS offers zero host identity guarantees.

### 6.2 Cryptographic Architecture & Wire Verification
CoSSH eliminates initial TOFU prompts by authenticating host key fingerprints directly via cryptographically signed DNSSEC records:
- **RFC 8484 DNS-over-HTTPS (DoH) Wire Queries**: Constructs RFC 8484 binary DNS queries requesting Resource Record Type 44 (`SSHFP`) with an EDNS0 OPT pseudo-RR (Type 41) declaring the `DO` (DNSSEC OK) bit (`0x8000`).
- **Authenticated Data (AD) Bit Validation**: Parses the wire DNS response header and evaluates the `AD` bit (bit 10 of flags, `0x0020`). If the AD bit is unset, the response is untrusted and rejected (`DnssecNotAuthenticated`).
- **RFC 4255 Resource Record Extraction**: Deserializes Type 44 records extracting:
  - `algorithm`: 1 (RSA), 2 (DSA), 3 (ECDSA), 4 (Ed25519)
  - `fpType`: 1 (SHA-1), 2 (SHA-256)
  - `fingerprint`: Cryptographic digest of the canonical host public key wire blob
- **Host Key Wire Comparison**: Computes the SHA-256 digest over the raw host key wire bytes presented by the SSH server. If the digest matches the DNSSEC-authenticated record, the host is authenticated with **Zero-TOFU friction**. If a mismatch is detected, a high-priority `FingerprintMismatchAlert` halts execution.

### 6.3 Evidence & Verification
- Unit Tests: `SshfpVerifierTest.kt` (RFC 8484 EDNS0 wire builder, DNS response parser, AD bit verification, digest matching, mismatch alerts)
- QA Audit: `docs/qa/SSH-234.md`

---

## 7. FIDO2 / WebAuthn Hardware Security Keys: PROTOCOL.u2f (SSH-235)

### 7.1 Threat Model
Mobile users frequently require physical hardware tokens (YubiKey, SoloKey, Nitrokey) for mission-critical production infrastructure. Standard software clients cannot parse or generate OpenSSH `sk-*` wire formats, preventing hardware token integration.

### 7.2 Binary Wire Protocol & Cryptographic Structures
CoSSH implements complete native binary wire serialization and parsing conforming to OpenSSH `PROTOCOL.u2f`:
- **Supported Key Primitives**:
  - `sk-ssh-ed25519@openssh.com` (Ed25519 on hardware token)
  - `sk-ecdsa-sha2-nistp256@openssh.com` (NIST P-256 on hardware token)
- **Public Key Wire Blobs**:
  - Ed25519: `string "sk-ssh-ed25519@openssh.com" || string public_bytes (32) || string application`
  - ECDSA: `string "sk-ecdsa-sha2-nistp256@openssh.com" || string "nistp256" || string public_point (65) || string application`
- **Wire Signatures**: Encodes/decodes wire signatures with authenticator user presence flags (`0x01`), user verification flags (`0x04`), and 32-bit big-endian counter values.
- **Signed Data Blob Generation**: Conforms to WebAuthn / CTAP2 AuthenticatorData and clientDataHash specifications:
  $$\text{SignedData} = \text{SHA-256}(\text{application}) \parallel \text{flags (1 byte)} \parallel \text{counter (4 bytes)} \parallel \text{SHA-256}(\text{message})$$
  producing exactly 69 bytes verified bit-for-bit against hardware authenticator requirements.
- **ASN.1 DER to mpint Conversion**: Handles bidirectional conversion between JCA/BouncyCastle ASN.1 DER ECDSA signatures (`SEQUENCE { INTEGER r, INTEGER s }`) and OpenSSH wire format `(mpint r, mpint s)`.

### 7.3 Evidence & Verification
- Unit Tests: `OpenSshFido2WireParserTest.kt` (public key blob roundtrip, signature roundtrip, 69-byte signed data builder, ASN.1 DER conversion, flag validation)
- QA Audit: `docs/qa/SSH-235.md`

---

## 8. Biometric Hardware SSH-Agent Forwarding Guard (SSH-235)

### 8.1 Threat Model: Agent Socket Hijacking & Unbounded Hopping
SSH agent forwarding (`ssh -A`) exposes a UNIX domain socket on remote jumpboxes. If an intermediate jumpbox is compromised, any attacker or root process with access to the forwarded socket can silently request signatures from the client's agent without user knowledge, draining access to all other infrastructure. Furthermore, chained forwarding allows credentials to be forwarded across arbitrary hops.

### 8.2 Security Architecture & Forwarding Isolation
CoSSH implements a hardened, defensive OpenSSH Agent protocol handler (`BiometricAuthAgentHandler`):
- **Per-Profile Identity Whitelisting**:
  - The client maintains explicit per-connection key whitelists.
  - When the agent receives `SSH2_AGENTC_REQUEST_IDENTITIES` (11), it filters available keys against the active profile's whitelist. If the whitelist is empty or none match, exactly **0 keys are exposed** (`SSH2_AGENT_IDENTITIES_ANSWER` with count 0).
  - Unwhitelisted sign requests are rejected with `SSH_AGENT_FAILURE` (5).
- **Per-Use Biometric Authorization Bridge**:
  - Before authorizing any `SSH2_AGENTC_SIGN_REQUEST` (13), the handler invokes `BiometricAgentConfirmationBridge.confirmSignRequest(keyAlias, destinationHost, challenge)`.
  - The user must explicitly authenticate via biometric sensor (Fingerprint / Face Unlock) for **every single sign request**. If the prompt is cancelled or verification fails, the signature is denied immediately with `SSH_AGENT_FAILURE`.
- **Loop Guard: MAX_ALLOWED_HOPS = 1**:
  - Enforces a strict hop limit guard. Any request received with hop count $\ge 1$ is immediately rejected with `SSH_AGENT_FAILURE`.
  - Eliminates unbounded multi-hop agent forwarding vulnerabilities.

### 8.3 Evidence & Verification
- Unit Tests: `BiometricAuthAgentHandlerTest.kt` (whitelisting enforcement, empty whitelist isolation, biometric confirmation success/failure, MAX_ALLOWED_HOPS loop guard)
- QA Audit: `docs/qa/SSH-235.md`

---

## 9. Comprehensive Summary Matrix of Security Controls

| Security Dimension | Legacy Android SSH Clients | CoSSH (Cobalt SSH) |
| :--- | :--- | :--- |
| **Private Key Storage** | Software SQLite / App Files | **Hardware StrongBox (Titan M2) / TEE (SSH-227)** |
| **Key Attestation** | None | **ASN.1 X.509 Extension OID 1.3.6.1.4.1.11129.2.1.17 (SSH-227)** |
| **Certificate Auth** | None / Incomplete | **OpenSSH User & Host Certificates (Ed25519/RSA/ECDSA) (SSH-228)** |
| **Host-CA Trust** | None | **`@cert-authority` Wildcards with Zero-TOFU (SSH-228)** |
| **Visual Host Key Verification** | Hex/Base64 Text Only | **Monospace Drunken Bishop Randomart (17x9 Grid) (SSH-229)** |
| **MITM Protection Barrier** | Single tap dismissible dialog | **Side-by-Side Dual Randomart & 2500ms Hold Barrier (SSH-229)** |
| **Security Audit Logging** | Unsecured logcat / plaintext | **SHA-256 Hash-Chained Log with Keystore Head Anchor (SSH-230)** |
| **Post-Quantum KEX** | Classical DH/ECDH only | **Hybrid `sntrup761x25519-sha512@openssh.com` (SSH-234)** |
| **DNSSEC Host Validation** | None (Blind TOFU) | **RFC 4255 / RFC 8484 SSHFP with EDNS0 DO Bit (SSH-234)** |
| **FIDO2 Hardware Keys** | None | **OpenSSH `PROTOCOL.u2f` (`sk-*`) Wire Parser (SSH-235)** |
| **Agent Forwarding Guard** | Unrestricted socket exposure | **Biometric Prompt Per-Use + Whitelist + Hop Guard (SSH-235)** |
