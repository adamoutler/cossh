## 2026-10-19T03:07:36Z
Sender: fb12e85b-e431-4066-92e6-9e5992d37ab2
Priority: MESSAGE_PRIORITY_HIGH

You are the Worker subagent for Ticket SSH-197: Implement Terminal Color Themes, Monospace Font Selection, and Paparazzi Snapshots.
Your working directory is: .agents/teamwork/worker_ssh197/
Dedicated Git branch: feat/SSH-197-terminal-themes-fonts

You MUST read:
- AGENTS.md
- GEMINI.md
- .agents/skills/cossh-secure-architecture/SKILL.md

WORKER INVARIANTS:
- You MUST NOT execute `git push`, `complete_work`, or `transition_ticket`.
- You own ONLY the files assigned to SSH-197. Do not modify files owned by other tickets.

TASK STEPS:
1. Ensure you create and checkout branch `feat/SSH-197-terminal-themes-fonts` from current main.
2. Implement `TerminalTheme.kt` with curated palettes for `COBALT_BLUE`, `SOLARIZED_DARK`, `MONOKAI`, `DRACULA` (ANSI 16 colors, foreground, background, cursor).
3. Implement `TerminalFontChoice.kt` with `DEFAULT_MONOSPACE`, `JETBRAINS_MONO`, `FIRA_CODE`, `INCONSOLATA`, `SOURCE_CODE_PRO`, with fallback to `Typeface.MONOSPACE`.
4. Update `SettingsManager.kt` (`globalColorTheme`, `globalFontChoice`) and `ConnectionProfile.kt` (`colorTheme: String? = null`, `fontChoice: String? = null`).
5. Update `TerminalScreen.kt` to apply theme colors (`tv.setBackgroundColor` + `mColors.mCurrentColors[259]`) and font `tv.setTypeface()`, including `isHeadlessTest` preview.
6. Add `TerminalPreviewCard` and theme/font selectors in `SettingsScreen.kt`.
7. Write unit tests in `app/src/test/kotlin/com/adamoutler/ssh/ui/screens/TerminalViewModelThemeTest.kt`.
8. Structure Paparazzi tests in `app/src/test/snapshots/TerminalThemeScreenshotTest.kt` (using `isHeadlessTest = true`).
9. Run local validation:
   `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64 QEMU_LD_PREFIX=/home/adamoutler/Android/x86_64-rootfs ./gradlew testDebugUnitTest lintDebug`
10. Create `user_stories/SSH-197.md` and QA report `docs/qa/SSH-197.md`.
11. Commit all changes locally to branch `feat/SSH-197-terminal-themes-fonts` with message `feat(terminal): implement terminal color themes, font selection, and preview (SSH-197)`.
12. Write `handoff.md` to `.agents/teamwork/worker_ssh197/handoff.md` and send completion message to orchestrator.

## 2026-10-19T03:50:25Z
Sender: fb12e85b-e431-4066-92e6-9e5992d37ab2
Priority: MESSAGE_PRIORITY_HIGH

**Context**: Ticket SSH-197 Status Check
**Content**: Heartbeat check on Paparazzi snapshot recording and branch commit status.
**Action**: Please provide a brief status update on whether Docker snapshot recording has finished and your expected handoff timing.
