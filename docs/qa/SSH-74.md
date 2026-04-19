# Verification Proof for SSH-74

## Dependabot Alerts Resolved
- BouncyCastle dependencies (`bcprov-jdk15on`, `bcpkix-jdk15on`, `bcutil-jdk15on`) were entirely excluded from the project globally and substituted with their secure `jdk18on` equivalents.
- `org.bouncycastle:bcprov-jdk18on`, `bcpkix-jdk18on`, and `bcutil-jdk18on` forced to version `1.84`.
- `sshj` updated to `0.40.0` which dropped the vulnerable `eddsa:0.3.0` dependency.
- Netty components (`netty-codec-http2`, etc.) forced to `4.1.132.Final`.
- `jose4j` forced to `0.9.6`.
- `jdom2` forced to `2.0.6.1`.
- `commons-io` forced to `2.14.0`.
- `protobuf-java` forced to `3.25.5`.

All version declarations were also added to `gradle/libs.versions.toml` and `buildscript` configurations to ensure Dependabot parses the secure versions correctly.

## Build and Compilation Proof
Standard output of `./gradlew dependencies` verifies that `eddsa` is gone, and the updated versions are correctly pulled in.
The project successfully compiles and passes all tests (including Robolectric and UI tests). 

The full dependency tree is saved in `docs/qa/SSH-74-app-dependencies.log`.
The CI pipeline executed successfully and returned `PASS ✅`.