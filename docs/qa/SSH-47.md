# QA Proof for SSH-47: Fix Terminal Text Copying (Strip Trailing Spaces)

## Requirement
*As a user, when I highlight text in the terminal, I expect the copy button to correctly copy the text to my clipboard, without any trailing spaces.*

## Verification Proof
- `TerminalScreen.kt` was modified to inject the `ClipboardManager` into the `TerminalSessionClient` using the context correctly.
- The text is trimmed of trailing spaces using `text.trimEnd()`.
- Created Robolectric unit test `TerminalScreenCopyTest.kt` verifying the functionality correctly accesses the `ClipboardManager` and strips trailing spaces before pushing it to the primary clip.
- CI and local testing execution successfully pass the verification. See `SSH-47.log`.