# QA Proof: SSH-76 - Decouple Network Layer and Replace Global Singletons

**User Story:** *As a developer, I need the network layer decoupled from UI components (TerminalSession) and global singletons removed so that memory leaks are prevented, testing is easier, and the Single Responsibility Principle is followed.*

**Verification Proof:**
- [x] `./gradlew :app:testDebugUnitTest --tests "com.adamoutler.ssh.network.*"` passes perfectly.
- [x] Single Responsibility Principle correctly followed by breaking apart connection singletons and passing dependencies directly via constructor or DI.
- [x] Memory leak risk mitigated by decoupling `TerminalSession` from the background services and UI lifecycle hooks.

**Screenshot:**
See the visual proof artifact at `docs/qa/SSH-76.png` demonstrating a successful SSH connection screen rendered after the decoupling.