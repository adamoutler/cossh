# SSH-73: Fix GitHub Dependabot Security Alerts (BouncyCastle, Netty, Jose4j)

## User Story
*As a security-conscious user, I need all underlying cryptographic and network dependencies to be free of known vulnerabilities so that my data remains secure against LDAP injections, DNS poisoning, and DoS attacks.*

## Solution
Modified `build.gradle.kts` and `gradle/libs.versions.toml` to enforce secure versions of transitive dependencies:
- `org.bouncycastle:bcprov-jdk18on` bumped to `1.84`
- `io.netty:netty-codec-http` bumped to `4.1.108.Final`
- `org.bitbucket.b_c:jose4j` bumped to `0.9.4` (resolved as 0.9.5)

## Verification Proof
- `buildEnvironment` dependencies properly reflect secure versions.
- Successfully built `clean test lint assembleDebug`.
- 89 actionable tasks executed successfully in 1m 49s with all tests passing.
