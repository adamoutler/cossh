# Utilities Module Context

## Logical Purpose
The `com.adamoutler.ssh.util` package provides shared helper functions, input sanitizers, launcher shortcut management, and secure logging infrastructure across CoSSH.

## Key Components & APIs
- **`AppLog`**: Centralized logging abstraction.
  - Routes all application log calls (`d`, `i`, `w`, `e`) through `SecureLogSanitizer` to scrub passwords, private keys, and sensitive tokens before writing to Android logcat.
- **`QuickConnectParser`**: Hardened parser for ad-hoc connection strings.
  - Supports `user@host:port`, standard URIs (`ssh://`, `telnet://`), and bracketed IPv6 notation (`user@[::1]:2222`).
  - Rejects forbidden shell metacharacters and control characters to prevent injection attacks.
- **`TerminalUrlSanitizer`**: URL detection and safety validator for clickable terminal hyperlinks.
  - Restricts launches strictly to safe browser schemes (`https://`, `http://`), blocking dangerous schemes (`file://`, `javascript:`, `content://`).
- **`ShortcutManagerHelper`**: Dynamic Android launcher shortcut publisher.
  - Manages pinned and dynamic home screen shortcuts for one-touch SSH connections via `ShortcutManagerCompat`.

## Behavioral Contracts & Design Patterns
- **Strict Redaction Invariant**: No sensitive key material, raw passwords, or decrypted byte arrays may reach system logs under any circumstance.
- **Defensive Parser Validation**: All external user inputs are treated as untrusted and validated against strict character allowlists.

## Dependencies
- `com.adamoutler.ssh.security.SecureLogSanitizer`
- `com.adamoutler.ssh.data.Protocol`
- AndroidX Core (`androidx.core.content.pm.ShortcutManagerCompat`)
- Android Log (`android.util.Log`)
