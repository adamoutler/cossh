# SSH-68 QA Proof: Fix terminal data duplication when keyboard hides

## Resolution
The duplication issue was caused by two factors:
1. The `TerminalSession` subprocess running `/system/bin/cat`, which echoed all stdin directly back to stdout (rendering it again on screen).
2. A previous hack that explicitly cleared the transcript on resize to hide the duplication.

## Fix
The `TerminalSession` was modified in `SshSessionProvider.kt` to use `sh -c 'exec sleep 2147483647'` instead of `cat`.
This completely prevents local stdin echoing back to the screen via the PTY because `sleep` does not read stdin.
The hack clearing the terminal transcript on resize (`SIGWINCH`) was removed (SSH-70), allowing the terminal to maintain its scrollback without duplicating text. 

## Verification
- Local testing and `DeterministicMultiTurnTest.kt` confirm that standard input flows through SSH without local echoing.
- Dismissing the keyboard triggers a `SIGWINCH` resize, and the terminal reflows without duplicating the text block.
- All tests pass in CI.
