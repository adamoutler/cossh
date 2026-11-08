# OpenSSH Configuration Module Context

## Logical Purpose
The `com.adamoutler.ssh.openssh` package provides a hardened, zero-dependency parser for OpenSSH client configuration files (`~/.ssh/config`). It enables users to import existing host definitions into native CoSSH connection profiles without exposing the application to Remote Code Execution (RCE) or directory traversal vectors.

## Key Components & APIs
- **`OpenSshConfigParser`**: The parsing engine for OpenSSH configuration text and input streams.
  - Parses host entries into structured `ConnectionProfile` and `IdentityProfile` associations.
  - Sanitizes and audits unsupported or dangerous directives.
- **`OpenSshParseResult`**: The outcome model containing:
  - `profiles: List<ConnectionProfile>` — Successfully parsed and mapped connection profiles.
  - `sanitizedDirectives: List<String>` — Auditing log of stripped directives.
  - `skippedHosts: List<String>` — List of host aliases skipped due to missing hostnames or wildcard patterns (`*`).

## Behavioral Contracts & Design Patterns
- **Strict Directive Allowlist**: Only safe connection directives (`Host`, `HostName`, `Port`, `User`, `ProxyJump`, `ServerAliveInterval`, `ServerAliveCountMax`, `ConnectTimeout`, `IdentityFile`) are ingested.
- **RCE Vector Neutralization**: All command execution directives (`ProxyCommand`, `LocalCommand`, `PermitLocalCommand`, `PKCS11Provider`, `Match`) are strictly discarded and logged.
- **Path Traversal Neutralization**: `IdentityFile` paths are stripped of directory components (`../../id_rsa` becomes `id_rsa`) and matched only against existing local cryptographic identities.
- **Bounded Input Defense**: Hard limits are enforced on file size (256 KB max), line length (4,096 chars max), and host entries (250 max) to prevent memory exhaustion or denial-of-service.

## Dependencies
- `com.adamoutler.ssh.data` (`ConnectionProfile`, `IdentityProfile`, `Protocol`, `AuthType`)
- `com.adamoutler.ssh.util.AppLog`
- Standard Java I/O (`BufferedReader`, `InputStreamReader`) — Zero third-party parsing dependencies.
