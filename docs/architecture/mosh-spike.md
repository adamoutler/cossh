# Architectural Spike: Mosh (Mobile Shell) Protocol Integration for CoSSH

**Author:** CoSSH Systems & Security Architecture  
**Document ID:** ARCH-SPIKE-237-MOSH  
**Target Epic:** SSH-237  
**Status:** Approved for Phased Architecture Integration  

---

## 1. Executive Summary & Problem Formulation

Standard SSH connections (RFC 4251–4254) are architecturally bound to reliable, stream-oriented TCP sockets (`SOCK_STREAM`). On mobile cellular networks, this fundamental design assumption collapses:

1. **IP Roaming & Handover Failure:** Switching between Wi-Fi and 5G cellular interfaces, cell-tower handovers, or transitions across CGNAT (Carrier-Grade NAT) gateways immediately invalidate TCP sockets, triggering long timeouts and forcing full session re-authentication.
2. **Bufferbloat & Packet Loss Freezes:** Mobile cell edges experience frequent jitter and transient packet loss. TCP head-of-line blocking stalls screen rendering, freezing the terminal cursor until missing segments are acknowledged.
3. **High Latency & Round-Trip Lag:** Satellite (Starlink) or rural LTE links exhibit round-trip latencies of 150ms–800ms. Standard SSH requires a full round-trip for every keystroke echo, making interactive editing in Vim or bash painful and error-prone.
4. **Android Doze Mode & Radio Quiescence:** When the device enters battery-saving Doze mode, Android suspends non-whitelisted sockets, resulting in remote servers resetting connections via keepalive timeouts.

**Mosh (Mobile Shell)** solves these mobile challenges through two core architectural breakthroughs:
- **State Synchronization Protocol (SSP):** Replaces byte-stream transmission with differential screen state synchronization over UDP.
- **Speculative Local Echo:** Locally predicts character entry, cursor motion, and line wrapping before receiving remote server confirmation.

This spike evaluates the feasibility, cryptographic security, Android NDK constraints (including the mandatory 16KB ELF page size), and phased integration roadmap for introducing Mosh into CoSSH alongside native SSH.

---

## 2. Mosh Protocol Architecture Deep-Dive

### 2.1 State Synchronization Protocol (SSP) vs. Character Streams

Traditional SSH sends raw PTY byte sequences over an encrypted TCP pipe. If a network blip drops 5 packets during a compile command generating 100,000 lines of output, TCP will redundantly retransmit every discarded byte.

Mosh fundamentally shifts the paradigm from **Stream Synchronization** to **Object State Synchronization**:

```
+-------------------------------------------------------------+
|                          Mosh Host                          |
|  +--------------------+        +-------------------------+  |
|  | Target Shell / PTY | <----> | mosh-server Framebuffer |  |
|  +--------------------+        +-------------------------+  |
+---------------------------------------------|---------------+
                                              |
                          UDP Datagrams (AES-128-OCB)
                          State Diffs (Protobuf Frames)
                                              |
+---------------------------------------------v---------------+
|                         Mobile Client                       |
|  +-----------------------+     +-------------------------+  |
|  | Predictive Terminal   | <-- | mosh-client Framebuffer |  |
|  | Screen / Compose UI   |     +-------------------------+  |
+-------------------------------------------------------------+
```

- Both client and server maintain an in-memory representation of the terminal display (a `Terminal::Framebuffer`).
- When network packets are dropped, Mosh skips intermediate display frames. The server computes a diff between the client's last acknowledged frame sequence number and the current server screen state.
- Even if a thousand lines scroll by while the user passes through a cellular tunnel, the client receives only the differential delta needed to render the *final* visible screen frame upon reconnection.

### 2.2 Wire Framing: Protocol Buffers & Differential Snaps

Mosh uses Google Protocol Buffers for structured serialization of state deltas:
- `Instruction`: Encapsulates terminal resizes, keystrokes, and control events.
- `Framebuffer`: Encapsulates cell characters, 24-bit TrueColor attributes, cursor coordinates, and terminal title.
- Frame compression ensures high-density screen payloads fit cleanly inside standard Ethernet and cellular MTUs (1280–1400 bytes), avoiding IP-level fragmentation.

### 2.3 Speculative Local Echo & Intelligent Prediction

Mosh implements an intelligent client-side prediction engine:
- User keystrokes are rendered immediately with an underline/contrast highlight indicating speculative state.
- Once the server acknowledges the input and updates the authoritative terminal framebuffer, the speculative prediction is confirmed and merged.
- If the remote program is in an un-echoed mode (e.g., password entry or ncurses UI like `htop`), the predictor detects the absence of echo and automatically disables local speculation.

---

## 3. Cryptographic Envelope & Threat Modeling

### 3.1 AES-128-OCB (RFC 7253) Authenticated Encryption

Mosh encapsulates all UDP datagrams within an authenticated cryptographic wrapper:
- **Cipher:** AES-128 in Offset Codebook Mode (OCB) v3.
- **Properties:** Provides Authenticated Encryption with Associated Data (AEAD) in a single cryptographic pass.
- **Nonce Formulation:** 64-bit monotonically increasing sequence number combined with an 8-bit direction flag (Client-to-Server vs. Server-to-Client).
- **Integrity Tag:** 128-bit MAC tag validating every datagram before any payload decoding occurs.

```
+-----------------------------------------------------------------------+
| Direction (1 byte) | Sequence (7 bytes) | Ciphertext | Tag (16 bytes) |
+-----------------------------------------------------------------------+
```

### 3.2 Security Strengths & Invariant Enforcement

1. **Denial-of-Service / UDP Amplification Resistance:** 
   - An attacker cannot spoof UDP datagrams to disrupt an active session. Datagrams failing AES-128-OCB tag verification are silently dropped in constant time, preventing timing or amplification side-channels.
2. **Replay Attack Rejection:**
   - The receiver maintains a sliding replay window tracking processed sequence numbers. Any packet with a duplicate or outdated sequence number is immediately discarded.
3. **Session Roaming Without Re-Authentication:**
   - Because authentication is tied to cryptographic verification of the AES-OCB tag rather than the source IP address or UDP port, the client can migrate freely across IP subnets (e.g. from Wi-Fi `192.168.1.50` to cellular `100.84.12.9`). When the client sends an authenticated packet from the new IP, the server verifies the tag and seamlessly updates its destination socket.

### 3.3 Cryptographic Limitations & Mitigations

| Threat / Characteristic | Assessment | CoSSH Architectural Mitigation |
| :--- | :--- | :--- |
| **No Perfect Forward Secrecy (PFS) in Session Key** | The Mosh session key is a 128-bit symmetric key exchanged during bootstrap. | The bootstrap phase MUST execute over hardened SSH using ephemeral Curve25519/ECDH key exchange. The Mosh key is never transmitted in cleartext. |
| **Session Key Persistence in Memory** | If a memory dump occurs, the session key could decrypt captured UDP traffic. | The key MUST reside only in volatile C++ memory wiped via `OPENSSL_cleanse()` or `explicit_bzero()` upon session termination. Never serialize to disk or `SharedPreferences`. |
| **OCB Mode Patent Licensing** | OCB had historical patent restrictions. | RFC 7253 and Phillip Rogaway's explicit patent grant grant open-source software (GPL/Apache 2.0/BSD) complete, irrevocable rights for non-military/open software. Modern OpenSSL 3.x and BoringSSL support OCB natively. |

---

## 4. SSH Bootstrap Integration Flow

Mosh does not authenticate directly over UDP. Instead, it leverages SSH as an authenticated bootstrap vehicle:

```
[CoSSH Client]                                             [Remote Server]
      |                                                           |
      | 1. Authenticate over SSH (Ed25519 Keystore key)          |
      |---------------------------------------------------------->|
      |                                                           |
      | 2. Execute remote command:                                |
      |    mosh-server new -s -c 256 -p 60000:61000               |
      |---------------------------------------------------------->|
      |                                                           |
      | 3. Remote server prints allocation info to stdout:        |
      |    MOSH CONNECT 60004 88e998a4d46f5c8b3e21...             |
      |<----------------------------------------------------------|
      |                                                           |
      | 4. Close SSH transport connection cleanly                 |
      |<--------------------------------------------------------->|
      |                                                           |
      | 5. Bind local UDP socket to target port 60004             |
      |    Initialize AES-128-OCB cipher with 128-bit key         |
      |                                                           |
      | 6. Transmit SSP Handshake datagrams                       |
      |==========================================================>|
```

### Parsing Contract:
The output of `mosh-server` matches the strict pattern:
```regex
^MOSH CONNECT ([0-9]{4,5}) ([A-Za-z0-9+/=]{22,})$
```
- **Port:** Selected dynamically from the server pool (default range: 60000–61000).
- **Key:** Base64-encoded 128-bit (16-byte) symmetric key used for all subsequent UDP datagram encryption.

---

## 5. Android Platform Hardening & System Constraints

Integrating Mosh into modern Android (API 34/35/36) presents specific systems-level engineering challenges that must be addressed:

### 5.1 Android 15+ 16KB ELF Page Alignment Mandate

Starting with Android 15 (Vanilla Ice Cream) and mandatory on Android 16, devices support 16KB memory page sizes (e.g. Google Tensor G4 on Pixel 9 series). 
- All shared ELF libraries (`.so`) packaged within the APK MUST align both load segments and PT_LOAD program headers to 16,384 bytes.
- Failure to comply causes package installation to abort with:
  `INSTALL_FAILED_INVALID_APK: Native library libmosh.so is not page-aligned`
- **Compiler Flags:**
  ```cmake
  target_link_options(mosh-native PRIVATE "-Wl,-z,max-page-size=16384")
  ```

### 5.2 Carrier-Grade NAT (CGNAT) & UDP Keep-Alive Timing

Mobile telecom carriers (T-Mobile, AT&T, Verizon) route cellular traffic through aggressive CGNAT firewalls.
- While TCP state tables are typically preserved for 15–60 minutes, **UDP NAT bindings are purged after 30 to 60 seconds of inactivity**.
- If a user pauses typing for 45 seconds, the incoming server datagrams will be dropped by the carrier firewall.
- **Architectural Solution:** CoSSH's Mosh client engine must implement a proactive **Synthetic UDP Heartbeat Timer**. When the connection is idle, it transmits a zero-delta empty SSP probe packet every **20 seconds** to keep the carrier firewall NAT translation entry alive.

### 5.3 Android Doze Mode & Background Foreground Service

When an Android device enters Deep Doze mode:
- All network access is restricted to maintenance windows.
- Unbound UDP sockets cannot receive incoming packets.
- **CoSSH Hardening:**
  - Active terminal sessions running Mosh must be anchored by a persistent Android Foreground Service with type `FOREGROUND_SERVICE_TYPE_DATA_SYNC` or `REMOTE_MESSAGING`.
  - Listen to `ConnectivityManager.NetworkCallback` to detect interface shifts (Wi-Fi drop, cellular enable) and proactively re-bind the client UDP socket to the new active network interface without tearing down the high-level session.

---

## 6. Native NDK Supply-Chain Analysis

To execute Mosh efficiently on mobile without battery degradation, the protocol stack must run natively in C++ via JNI.

### Dependency Evaluation:

| Dependency | Purpose | Size Impact | Security / Vulnerability Assessment |
| :--- | :--- | :--- | :--- |
| **`libprotobuf-lite`** | Serialization of SSP Framebuffer deltas | ~350 KB | Highly hardened; use official Google Protobuf lite release. |
| **`libcrypto` (OpenSSL / BoringSSL)** | AES-128-OCB authenticated encryption | ~1.2 MB | Use modern BoringSSL or OpenSSL 3.3+ with constant-time OCB implementations. |
| **`libmosh-client`** | Protocol state machine & prediction engine | ~400 KB | Forked from upstream `mobile-shell/mosh`, audited for safe memory handling. |
| **`ncurses` / terminal emulator** | Framebuffer cell management | Stripped down | Replace with lightweight internal ANSI grid or reuse CoSSH's existing terminal buffer. |

**Supply-Chain Standard:**
- All native libraries must be built deterministically from source via CMake / Ninja.
- Enable full hardening flags: `-fstack-protector-strong`, `-D_FORTIFY_SOURCE=2`, `-Wl,-z,relro,-z,now` (Full RELRO), and `-fPIE`.

---

## 7. Phased `ConnectionProtocol` Integration Roadmap

To maintain CoSSH's uncompromising zero-regression engineering standard, Mosh will be integrated in four distinct, gated phases:

```
+---------------------------------------------------------------------------------+
| Phase 1: Architecture & Model Contracts (SSH-237)                               |
| - Introduce ConnectionProtocol.MOSH to ConnectionProfile                       |
| - Architect ExtraKeysPreset for Tmux/Mosh workflows                             |
| - Complete Technical Spike & Threat Model (docs/architecture/mosh-spike.md)     |
+---------------------------------------------------------------------------------+
                                       |
                                       v
+---------------------------------------------------------------------------------+
| Phase 2: Native Toolchain & 16KB Page-Aligned NDK Engine                        |
| - Cross-compile mosh-client and protobuf-lite for arm64-v8a and x86_64          |
| - Enforce 16KB ELF alignment (-Wl,-z,max-page-size=16384)                       |
| - Implement JNI Bridge: NativeMoshEngine JNI interface                          |
+---------------------------------------------------------------------------------+
                                       |
                                       v
+---------------------------------------------------------------------------------+
| Phase 3: SSH Bootstrap Coordinator & UDP Transport Layer                        |
| - Implement MoshBootstrapCoordinator using existing JSch/SSH transport          |
| - Parse MOSH CONNECT tokens and initiate native UDP session                     |
| - Wire Predictive Local Echo into TerminalScreen Compose UI                     |
+---------------------------------------------------------------------------------+
                                       |
                                       v
+---------------------------------------------------------------------------------+
| Phase 4: Production Hardening, Battery Optimization & Field Testing            |
| - 20-second CGNAT keepalive probes & NetworkCallback roaming                    |
| - Memory zeroization verification and end-to-end integration tests              |
| - Full QA verification & Reality-Checker Certification                          |
+---------------------------------------------------------------------------------+
```

---

## 8. Conclusion & Recommendation

Mosh represents the single greatest user-experience upgrade for mobile terminal users operating in real-world cellular environments. 

Its differential framebuffer architecture perfectly complements CoSSH's high-performance Jetpack Compose terminal surface. By pairing CoSSH's Keystore-backed authentication with Mosh's AES-128-OCB UDP transport, CoSSH will deliver an unshakeable mobile terminal experience that is impervious to cellular dropouts, IP roaming, and network latency.
