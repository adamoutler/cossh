# SSH-46 QA Proof: Persistent Terminal Session History on Resume

**User Story:** As a user, when I resume an active background connection, I should be able to see and scroll the full history of the connection (persisted transcript).

## Proof of Implementation
I moved the `TerminalSession` ownership from the UI layer (`TerminalScreen.kt` Composable) to the background state manager (`SshSessionProvider.kt`). 
* `TerminalScreen` now retrieves the session via `SshSessionProvider.getOrCreateSession()` on composition.
* `SshService` now writes directly to this persistent session's emulator.
* The `TerminalSessionClient` is also hosted persistently in the provider, and updates its active `Context` and screen-update callbacks when the `TerminalScreen` UI comes to the foreground. 
* This prevents the terminal history from being wiped when the `TerminalScreen` is disposed (e.g., when backgrounded, rotated, or navigated away from).

## Unit Test Proof
A new Robolectric unit test `TerminalSessionPersistenceTest.kt` was added to verify that the session object is successfully persisted across multiple invocations (simulating UI detachment and re-attachment) without losing the original reference.

```kotlin
@Test
fun testTerminalSessionIsPersistedInProvider() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    SshSessionProvider.getContext = { context }
    
    val session1 = SshSessionProvider.getOrCreateSession()
    assertNotNull(session1)
    
    val session2 = SshSessionProvider.getOrCreateSession()
    assertTrue(session1 === session2)
}
```

The CI build and unit test logs have been attached to `docs/qa/SSH-46.log`.
The tests execute perfectly with zero regressions on other modules.
